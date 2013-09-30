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

package com.android.server.cms;

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
import android.os.BatteryManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UEventObserver;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

/**
 * <p> CurrentMgmtService manages the current drawn by the platform.
 * On receiving an ACTION_BATTERY_CHANGED intent, for a change in the
 * battery level, this service configures the BCU(Burst Control Unit)
 * registers, through the sysfs exposed by the BCU driver, to reduce
 * the current consumption.</p>
 */
public class CurrentMgmtService extends Binder {

    private static final String TAG = "CurrentMgmtService";

    private static final String DEV_PATH = "SUBSYSTEM=hwmon";
    private final Context mContext;

    native static void nativeSubsystemThrottle(int subsystem, int level);
    native static void nativeInit();
    private static boolean sShutdownInitiated = false;
    private static int sPrevState, sCurState;
    ArrayList<Integer> mLevels = new ArrayList();
    ParseCmsConfig mPcc = null;
    ThrottleTrigger mTt = null;
    private ArrayList<ThrottleTrigger> mThrottleTriggers;
    private ArrayList<ContributingDevice> mCDevs;
    private ArrayList<State> mStates;

    private final class BCUReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int i, j, k;
            int battLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            Intent vbusIntent;

            if (status == BatteryManager.BATTERY_STATUS_CHARGING &&
                    plugType != BatteryManager.BATTERY_PLUGGED_USB) {
                    sCurState = 0;
            } else {
                for (i = mLevels.size() - 1; i >= 0; i--) {
                    if (battLevel < mLevels.get(i)) {
                        sCurState = i + 1;
                        break;
                    }
                    sCurState = 0;
                }
            }

            if (sPrevState != sCurState) {
                Log.i(TAG, "state changed:" + sCurState);
                if (sCurState == 0) {
                    for (i = 0; i < mCDevs.size(); i++) {
                        if (mCDevs.get(i).getID() != 4) {
                            nativeSubsystemThrottle(mCDevs.get(i).getID(), 0);
                        }
                    }
                    vbusIntent = new Intent(ACTION_USB_HOST_VBUS);
                    vbusIntent.putExtra(EXTRA_HOST_VBUS, USB_HOST_VBUS_NORMAL);
                    context.sendBroadcastAsUser(vbusIntent, UserHandle.ALL);
                    sPrevState = sCurState;
                    return;
                }
                for (j = 0; j < mStates.get(sCurState).getDevIDList().size(); j++) {
                    for (k = 0; k < mCDevs.size(); k++) {
                        if (mCDevs.get(k).getID() == mStates.get(sCurState).getDevIDList().get(j)) {
                            if (mCDevs.get(k).getID() != 4) {
                                nativeSubsystemThrottle(mCDevs.get(k).getID(),
                                mCDevs.get(k).getThrottleTriggerByID(0).
                                       getThrottleValue(0, sCurState));
                            } else {
                                vbusIntent = new Intent(ACTION_USB_HOST_VBUS);
                                switch (sCurState) {
                                    case 1:
                                    case 2:
                                        vbusIntent.putExtra
                                                (EXTRA_HOST_VBUS, USB_HOST_VBUS_ALERT);
                                        break;
                                    case 3:
                                        vbusIntent.putExtra
                                                (EXTRA_HOST_VBUS, USB_HOST_VBUS_CRITICAL);
                                        break;
                                }
                                context.sendBroadcastAsUser(vbusIntent, UserHandle.ALL);
                            }
                        }
                    }
                }
                sPrevState = sCurState;
            }
        }
    }

    private UEventObserver mUEventObserver = new UEventObserver() {
        public void onUEvent(UEventObserver.UEvent event) {
            Log.v(TAG, "Uevent Called");
            if ("VWARN2".equals(event.get("BCUEVT"))) {
                if (!sShutdownInitiated) {
                    sShutdownInitiated = true;
                    Log.i(TAG, "Initiating shutdown due to peak current");
                    Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
                    intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivityAsUser(intent, UserHandle.CURRENT);
                }
            }
        }
    };

    public void getLevels() {
        mThrottleTriggers = mPcc.getThrottleTriggers();
        mCDevs = mPcc.getContributingDevices();
        for (int i = 0; i < mThrottleTriggers.size(); i++) {
             if (mThrottleTriggers.get(i).getName().equals("battLevel")) {
                 mTt = mThrottleTriggers.get(i);
                 break;
             }
        }

        mStates = mTt.getStates();

        for (int j = 0; j < mStates.size(); j++) {
             mLevels.add(mStates.get(j).getLevel());
        }
    }

    public CurrentMgmtService(Context context) {
        Log.v(TAG, "CurrentMgmtService start");
        mContext = context;
        mPcc = new ParseCmsConfig();
        if (!mPcc.parseCmsThrottleConfig() || !mPcc.parseCmsDeviceConfig()) {
            Log.e(TAG, "CMS Configuration XML not found");
            return;
        }
        getLevels();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(new BCUReceiver(), filter);
        nativeInit();
        mUEventObserver.startObserving(DEV_PATH);
    }
}
