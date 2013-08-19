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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server;

import com.android.server.BatteryService;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import static android.hardware.usb.UsbManager.ACTION_USB_HOST_VBUS;
import static android.hardware.usb.UsbManager.EXTRA_HOST_VBUS;
import static android.hardware.usb.UsbManager.USB_HOST_VBUS_NORMAL;
import static android.hardware.usb.UsbManager.USB_HOST_VBUS_WARNING;
import static android.hardware.usb.UsbManager.USB_HOST_VBUS_ALERT;
import static android.hardware.usb.UsbManager.USB_HOST_VBUS_CRITICAL;
import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED;
import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;

import android.os.UEventObserver;
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.StringTokenizer;
import android.util.Log;
import java.util.Timer;
import java.util.TimerTask;


/**
 * <p> CurrentMgmtService manages the current drawn by the platform.
 * On receiving an ACTION_BATTERY_CHANGED intent, for a change in the
 * battery level, this service configures the BCU(Burst Control Unit)
 * registers, through the sysfs exposed by the BCU driver, to reduce
 * the current consumption.</p>
 */
class CurrentMgmtService extends Binder {

    private static final String TAG = "CurrentMgmtService";

    private static final String devPath = "SUBSYSTEM=hwmon";
    private final Context mContext;

    native static void nativeSubsystemThrottle(int subsystem, int level);
    native static void nativeInit();
    private static int battWarnLevel1 = 15;
    private static int battWarnLevel2 = 10;
    private static int battWarnLevel3 = 5;
    private static boolean isUsbDeviceAttached = false;
    private static boolean shutdownInitiated = false;

    public enum Level {
        NORMAL, WARNING, ALERT, CRITICAL;
    }
    Level currentLevel, prevLevel = Level.NORMAL;

    private enum Flash {Enable(1), Disable(0);
        private int numVal;
        Flash(int numVal) {
             this.numVal = numVal;
        }
        public int getVal() {
             return numVal;
        }
    };

    private enum Speaker {Full(0), Low(1), Stop(2);
        private int numVal;
        Speaker(int numVal) {
             this.numVal = numVal;
        }
        public int getVal() {
             return numVal;
        }
    };

    private enum Otg {Normal(0), Warn(1), Alert(2);
        private int numVal;
        Otg(int numVal) {
            this.numVal = numVal;
        }
        public int getVal() {
            return numVal;
        }
     };

    private enum SubSystem {
         BCU_SUBSYS_AUDIO(1),
         BCU_SUBSYS_CAMERA(2),
         BCU_SUBSYS_DISPLAY(3),
         BCU_SUBSYS_OTG(4),
         BCU_SUBSYS_VIBRA(5);
         private int numVal;
         SubSystem(int numVal) {
              this.numVal = numVal;
         }
         public int getVal() {
              return numVal;
         }
    };

    private static int speakerStatus;
    private static int flashStatus = 1;
    private static int otgStatus;

    private final class UsbReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_DEVICE_ATTACHED.equals(action))
                isUsbDeviceAttached = true;
            else if (ACTION_USB_DEVICE_DETACHED.equals(action))
                isUsbDeviceAttached = false;
        }
    }


    private final class BCUReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int battLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            prevLevel = currentLevel;
            // Determine the current BCU state: normal/warning/alert/critical
            if (battLevel < battWarnLevel3) {
                currentLevel = Level.CRITICAL;
            } else if (battLevel < battWarnLevel2) {
                currentLevel = Level.ALERT;
            } else if (battLevel < battWarnLevel1) {
                currentLevel = Level.WARNING;
            } else {
                currentLevel = Level.NORMAL;
            }

            // Take actions if the BCU state has changed
            if (currentLevel != prevLevel) {
                 // Intent to notify USB on certain battery thresholds
                Intent BCUIntent = new Intent(ACTION_USB_HOST_VBUS);
                switch (currentLevel) {
                    // Currently, we take same actions for both critical and alert state
                    case CRITICAL:
                    case ALERT:
                        // Same action could have been taken in an earlier state also.
                        // So check the throttling status before proceeding.
                        if (speakerStatus != Speaker.Stop.getVal()) {
                            nativeSubsystemThrottle(SubSystem.BCU_SUBSYS_AUDIO.getVal(), Speaker.Stop.getVal());
                            speakerStatus = Speaker.Stop.getVal();
                        }
                        if (flashStatus != Flash.Disable.getVal()) {
                            nativeSubsystemThrottle(SubSystem.BCU_SUBSYS_CAMERA.getVal(), Flash.Disable.getVal());
                            flashStatus = Flash.Disable.getVal();
                        }
                        if (otgStatus != Otg.Alert.getVal()) {
                            BCUIntent.putExtra(EXTRA_HOST_VBUS, USB_HOST_VBUS_CRITICAL);
                            otgStatus = Otg.Alert.getVal();
                        }
                        break;
                    case WARNING:
                        if (speakerStatus != Speaker.Low.getVal()) {
                            nativeSubsystemThrottle(SubSystem.BCU_SUBSYS_AUDIO.getVal(), Speaker.Low.getVal());
                            speakerStatus = Speaker.Low.getVal();
                        }
                        if (flashStatus != Flash.Disable.getVal()) {
                            nativeSubsystemThrottle(SubSystem.BCU_SUBSYS_CAMERA.getVal(), Flash.Disable.getVal());
                            flashStatus = Flash.Disable.getVal();
                        }
                        if (otgStatus != Otg.Warn.getVal()) {
                            BCUIntent.putExtra(EXTRA_HOST_VBUS, USB_HOST_VBUS_WARNING);
                            otgStatus = Otg.Warn.getVal();
                        }
                        break;
                    case NORMAL:
                        if (speakerStatus != Speaker.Full.getVal()) {
                            nativeSubsystemThrottle(SubSystem.BCU_SUBSYS_AUDIO.getVal(), Speaker.Full.getVal());
                            speakerStatus = Speaker.Full.getVal();
                        }
                        if (flashStatus != Flash.Enable.getVal()) {
                            nativeSubsystemThrottle(SubSystem.BCU_SUBSYS_CAMERA.getVal(), Flash.Enable.getVal());
                            flashStatus = Flash.Enable.getVal();
                        }
                        if (otgStatus != Otg.Normal.getVal()) {
                            BCUIntent.putExtra(EXTRA_HOST_VBUS, USB_HOST_VBUS_NORMAL);
                            otgStatus = Otg.Normal.getVal();
                        }
                        break;
                }
                if (isUsbDeviceAttached) {
                    context.sendBroadcastAsUser(BCUIntent, UserHandle.ALL);
                }
            }
        }
    }

    private UEventObserver mUEventObserver = new UEventObserver() {
        public void onUEvent(UEventObserver.UEvent event) {
            Log.v(TAG, "Uevent Called");
            if ("VWARN2".equals(event.get("BCUEVT"))) {
                if (!shutdownInitiated) {
                    shutdownInitiated = true;
                    Log.i(TAG, "Initiating shutdown due to peak current");
                    Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
                    intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivityAsUser(intent, UserHandle.CURRENT);
                }
            }
        }
    };

    public CurrentMgmtService(Context context) {
        Log.v(TAG, "CurrentMgmtService start");
        mContext = context;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(new BCUReceiver(), filter);
        IntentFilter usbFilter1 = new IntentFilter();
        usbFilter1.addAction(ACTION_USB_DEVICE_ATTACHED);
        IntentFilter usbFilter2 = new IntentFilter();
        usbFilter2.addAction(ACTION_USB_DEVICE_DETACHED);
        mContext.registerReceiver(new UsbReceiver(), usbFilter1);
        mContext.registerReceiver(new UsbReceiver(), usbFilter2);
        nativeInit();
        mUEventObserver.startObserving(devPath);
    }
}
