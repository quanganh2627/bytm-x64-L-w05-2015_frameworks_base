/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions an
 * limitations under the License.
 */

package com.android.server.usb;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.Process;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * UsbHostManager manages USB state in host mode.
 */
public class UsbHostManager {
    private static final String TAG = UsbHostManager.class.getSimpleName();
    private static final boolean LOG = false;

    // contains all connected USB devices
    private final HashMap<String, UsbDevice> mDevices = new HashMap<String, UsbDevice>();

    // USB busses to exclude from USB host support
    private final String[] mHostBlacklist;

    private final Context mContext;
    private final Object mLock = new Object();
    private NotificationManager mNotificationManager;
    private UsbHandler mHandler;
    private static final String ONEMORE_UMS_DEVICE = "One more MSC devices attached";
    private static final int MSG_USB_HOST_WARNING = 0;

    @GuardedBy("mLock")
    private UsbSettingsManager mCurrentSettings;

    public UsbHostManager(Context context) {
        mContext = context;
        mHostBlacklist = context.getResources().getStringArray(
                com.android.internal.R.array.config_usbHostBlacklist);

        //create a thread for our Handler
        HandlerThread thread = new HandlerThread("UsbHostManager",
                    Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mHandler = new UsbHandler(thread.getLooper());
    }

    private final class UsbHandler extends Handler {
        public UsbHandler (Looper looper) {
            super(looper);
        }

        private void showUsbHostWarning(String warning) {
            int id;

            if (ONEMORE_UMS_DEVICE.equals(warning)) {
                id = com.android.internal.R.string.usb_warn_host_onemore_ums_device_title;
            } else {
                Slog.w(TAG, "unknown warning" + warning);
                return;
            }

            removeMessages(MSG_USB_HOST_WARNING);
            Message msg = Message.obtain(this,MSG_USB_HOST_WARNING);
            msg.arg1 = id;
            msg.arg2 = 1;
            sendMessageDelayed(msg,500);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_USB_HOST_WARNING:
                    updateWarnNotification(msg.arg1,msg.arg2);
                    break;
                default:
                    Slog.w(TAG, "unknown message " + msg.what);
                    break;
            }
        }

        private void updateWarnNotification(int id, int enable) {

            mNotificationManager = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);

            if (mNotificationManager == null) return;

            if (enable > 0) {

                Resources r = mContext.getResources();
                CharSequence title = r.getText(id);
                int idmsg;

                if (id == com.android.internal.R.string.usb_warn_host_onemore_ums_device_title)
                    idmsg = com.android.internal.R.string.usb_warn_host_onemore_ums_device_message;
                else
                    idmsg = id;

                CharSequence message = r.getText(idmsg);

                Notification notification = new Notification();
                notification.icon = com.android.internal.R.drawable.stat_sys_adb;
                notification.when = 0;
                notification.flags = Notification.FLAG_AUTO_CANCEL;
                notification.tickerText = title;
                notification.defaults = 0; // please be quiet
                notification.sound = null;
                notification.vibrate = null;

                Intent intent = new Intent();
                PendingIntent pi = PendingIntent.getActivity(mContext, 0,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);
                notification.setLatestEventInfo(mContext, title, message, pi);
                mNotificationManager.notify(id, notification);

                // Cancel Notification automatically after few seconds
                removeMessages(MSG_USB_HOST_WARNING);
                Message msg = Message.obtain(this, MSG_USB_HOST_WARNING);
                msg.arg1 = id;
                msg.arg2 = 0;
                sendMessageDelayed(msg, 30000);
            } else {
                Slog.d(TAG, "cancel USB Warning notification");

                mNotificationManager.cancel(id);
            }
        }
    }

    public void setCurrentSettings(UsbSettingsManager settings) {
        synchronized (mLock) {
            mCurrentSettings = settings;
        }
    }

    private UsbSettingsManager getCurrentSettings() {
        synchronized (mLock) {
            return mCurrentSettings;
        }
    }

    private boolean isBlackListed(String deviceName) {
        int count = mHostBlacklist.length;
        for (int i = 0; i < count; i++) {
            if (deviceName.startsWith(mHostBlacklist[i])) {
                return true;
            }
        }
        return false;
    }

    /* returns true if the USB device should not be accessible by applications */
    private boolean isBlackListed(int clazz, int subClass, int protocol) {
        // blacklist hubs
        if (clazz == UsbConstants.USB_CLASS_HUB) return true;

        // blacklist HID boot devices (mouse and keyboard)
        if (clazz == UsbConstants.USB_CLASS_HID &&
                subClass == UsbConstants.USB_INTERFACE_SUBCLASS_BOOT) {
            return true;
        }

        return false;
    }

    /* check if more than one Mass Storage Class device is connected.  */
    private boolean isOneMoreMscDeviceConnected() {
        int umsDevices = 0;

        synchronized (mLock) {
            for (String name : mDevices.keySet()) {
                UsbDevice device = mDevices.get(name);
                if ( device != null) {
                    int interfaces = device.getInterfaceCount();
                    for (int i = 0; i < interfaces; i++) {
                        if (device.getInterface(i).getInterfaceClass() == 0x08)
                            umsDevices++;
                    }
                }
            }
        }
        if (umsDevices > 1)
            return true;
        else
            return false;
    }

    /* Called from JNI in monitorUsbHostBus() to report new USB devices */
    private void usbDeviceAdded(String deviceName, int vendorID, int productID,
            int deviceClass, int deviceSubclass, int deviceProtocol,
            /* array of quintuples containing id, class, subclass, protocol
               and number of endpoints for each interface */
            int[] interfaceValues,
           /* array of quadruples containing address, attributes, max packet size
              and interval for each endpoint */
            int[] endpointValues) {

        if (isBlackListed(deviceName) ||
                isBlackListed(deviceClass, deviceSubclass, deviceProtocol)) {
            return;
        }

        synchronized (mLock) {
            if (mDevices.get(deviceName) != null) {
                Slog.w(TAG, "device already on mDevices list: " + deviceName);
                return;
            }

            int numInterfaces = interfaceValues.length / 5;
            Parcelable[] interfaces = new UsbInterface[numInterfaces];
            try {
                // repackage interfaceValues as an array of UsbInterface
                int intf, endp, ival = 0, eval = 0;
                for (intf = 0; intf < numInterfaces; intf++) {
                    int interfaceId = interfaceValues[ival++];
                    int interfaceClass = interfaceValues[ival++];
                    int interfaceSubclass = interfaceValues[ival++];
                    int interfaceProtocol = interfaceValues[ival++];
                    int numEndpoints = interfaceValues[ival++];

                    Parcelable[] endpoints = new UsbEndpoint[numEndpoints];
                    for (endp = 0; endp < numEndpoints; endp++) {
                        int address = endpointValues[eval++];
                        int attributes = endpointValues[eval++];
                        int maxPacketSize = endpointValues[eval++];
                        int interval = endpointValues[eval++];
                        endpoints[endp] = new UsbEndpoint(address, attributes,
                                maxPacketSize, interval);
                    }

                    // don't allow if any interfaces are blacklisted
                    if (isBlackListed(interfaceClass, interfaceSubclass, interfaceProtocol)) {
                        return;
                    }
                    interfaces[intf] = new UsbInterface(interfaceId, interfaceClass,
                            interfaceSubclass, interfaceProtocol, endpoints);
                }
            } catch (Exception e) {
                // beware of index out of bound exceptions, which might happen if
                // a device does not set bNumEndpoints correctly
                Slog.e(TAG, "error parsing USB descriptors", e);
                return;
            }

            UsbDevice device = new UsbDevice(deviceName, vendorID, productID,
                    deviceClass, deviceSubclass, deviceProtocol, interfaces);
            mDevices.put(deviceName, device);
            getCurrentSettings().deviceAttached(device);

            /* android currently only supports one MSC device for storage.
             * If connected one more, send notification to show a warnning msg.
             *
             * Here add a workaround for BYT, delay 500ms then check. Because
             * the UMS may be emurated again as High speed device, reconnect
             * event may go first than disconnect event.
             */
            TimerTask task = new TimerTask() {
                public void run() {
                    if (isOneMoreMscDeviceConnected()) {
                        Slog.d(TAG, "One more MSC Device Connected and send notification ");
                        mHandler.showUsbHostWarning(ONEMORE_UMS_DEVICE);
                    }
                }
            };
            Timer timer = new Timer();
            timer.schedule(task, 500);
        }
    }

    /* Called from JNI in monitorUsbHostBus to report USB device removal */
    private void usbDeviceRemoved(String deviceName) {
        synchronized (mLock) {
            UsbDevice device = mDevices.remove(deviceName);
            if (device != null) {
                getCurrentSettings().deviceDetached(device);
            }
        }
    }

    public void systemReady() {
        synchronized (mLock) {
            // Create a thread to call into native code to wait for USB host events.
            // This thread will call us back on usbDeviceAdded and usbDeviceRemoved.
            Runnable runnable = new Runnable() {
                public void run() {
                    monitorUsbHostBus();
                }
            };
            new Thread(null, runnable, "UsbService host thread").start();
        }
    }

    /* Returns a list of all currently attached USB devices */
    public void getDeviceList(Bundle devices) {
        synchronized (mLock) {
            for (String name : mDevices.keySet()) {
                devices.putParcelable(name, mDevices.get(name));
            }
        }
    }

    /* Opens the specified USB device */
    public ParcelFileDescriptor openDevice(String deviceName) {
        synchronized (mLock) {
            if (isBlackListed(deviceName)) {
                throw new SecurityException("USB device is on a restricted bus");
            }
            UsbDevice device = mDevices.get(deviceName);
            if (device == null) {
                // if it is not in mDevices, it either does not exist or is blacklisted
                throw new IllegalArgumentException(
                        "device " + deviceName + " does not exist or is restricted");
            }
            getCurrentSettings().checkPermission(device);
            return nativeOpenDevice(deviceName);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw) {
        synchronized (mLock) {
            pw.println("  USB Host State:");
            for (String name : mDevices.keySet()) {
                pw.println("    " + name + ": " + mDevices.get(name));
            }
        }
    }


    private native void monitorUsbHostBus();
    private native ParcelFileDescriptor nativeOpenDevice(String deviceName);
}
