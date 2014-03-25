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
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UEventObserver;
import android.util.Log;

import com.android.server.BatteryService;
import com.android.server.thermal.ThermalManager;

import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
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
    private static final String CPU_PATH = "/sys/devices/system/cpu/";
    private final Context mContext;

    native static void nativeSubsystemThrottle(int subsystem, int level);
    native static void nativeInit();
    private static boolean sShutdownInitiated = false;
    private static boolean sBootCompleted = false;
    private static boolean sRecheckTempState = false;
    private static int sPrevLevelState, sCurLevelState, sPrevTempState, sCurTempState;
    private ArrayList<Integer> mLevels = new ArrayList();
    private ArrayList<Integer> mTemps = new ArrayList();
    private ArrayList<Integer> mLevelsTemp = new ArrayList();
    ParseCmsConfig mPcc = null;
    ThrottleTrigger mLevelTrigger, mTempTrigger = null;
    private ArrayList<ThrottleTrigger> mThrottleTriggers;
    private ArrayList<ContributingDevice> mCDevs;
    private ArrayList<State> mLevelStates;
    private ArrayList<State> mTempStates;
    private transient final List<CpuCore> mCpuList = new ArrayList();

    /**
     * Native method to read sysfs.
     * Returns String read from sysfs
     * @param path path of the sysfs
     */
    private native static String native_readSysfs(String path);

    /**
     * Native method to write sysfs.
     * Returns integer - 0 on success and -1 on failure
     * @param path path of the sysfs
     * @param val value to be written into the sysfs
     */
    private native static int native_writeSysfs(String path, int val);

    /**
     * Method to read sysfs.
     * Returns a string that contains the value of the sysfs read.
     * @param path path of the sysfs
     */
    public static String readSysfs(String path) {
        try {
            return native_readSysfs(path);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "caught UnsatisfiedLinkError in readSysfs");
            return null;
        }
    }

    /**
     * Native method to write sysfs.
     * Returns integer - 0 on success and -1 on failure
     * @param path path of the sysfs
     * @param val value to be written into the sysfs
     */
    public static int writeSysfs(String path, int val) {
        try {
            return native_writeSysfs(path, val);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "caught UnsatisfiedLinkError in writeSysfs");
            return -1;
        }
    }

    /**
     * Class CpuCore to represent each cpu core and set of frequencies
     */
    class CpuCore {
        private final transient String mName;
        private transient int[] mCpuAvailFreqs;
        private transient boolean mIsCapped;
        private transient int mMaxFreq;

       /**
        * Constructor for the class CpuCore
        * @param name cpu core name, for eg: cpu0
        */
        public CpuCore(String name) {
            this.mName = name;
            getAvailFreqs();
            checkIfCapped();
        }

        /**
         * Getter method for mName.
         * Returns mName as String.
         */
        public String getName() {
            // mName is set in the constructor.
            // So no setter method required for this variable.
            return mName;
        }

        /**
         * Getter method for mIsCapped.
         * Returns boolean value of mIsCapped.
         */
        public boolean isCapped() {
            // mIsCapped is already set by calling checkIfCapped() in constructor.
            // Since this occurs only once during boot, explicit setter method is not needed.
            return mIsCapped;
        }

        /**
         * Getter method for mMaxFreq.
         * Returns integer value of mMaxfreq.
         */
        public int getMaxFreq() {
            // mMaxFreq is already set by calling checkIfCapped() in constructor.
            // So no setter method required for this variable.
            return mMaxFreq;
        }

        /**
         * Method to fetch scaling available frequencies of a cpu core.
         * mCpuAvailFreqs array is initialized in this method.
         */
        public final void getAvailFreqs() {
            String path = CPU_PATH + mName + "/cpufreq/scaling_available_frequencies";
            String frequencyList = readSysfs(path);
            if (frequencyList == null) {
                Log.e(TAG, "Cannot get Available Frequencies");
                return;
            }
            String frequencies[] = frequencyList.split(" ");
            final int numFreqs = frequencies.length;
            mCpuAvailFreqs = new int[numFreqs];
            for (int i = 0; i < frequencies.length; i++) {
                try {
                    mCpuAvailFreqs[i] = Integer.parseInt(frequencies[i]);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Number format exception while getting available frequencies");
                }
            }
        }

        /**
         * Method to check if the scaling maximum frequency is capped.
         * This method initializes mIsCapped boolean for the current cpu core.
         */
        public final void checkIfCapped() {
            String path = CPU_PATH + mName + "/cpufreq/scaling_max_freq";
            int curMax;

            if (mCpuAvailFreqs == null) {
                return;
            }

            Arrays.sort(mCpuAvailFreqs);
            mMaxFreq = mCpuAvailFreqs[mCpuAvailFreqs.length - 1];
            Log.i(TAG, "Maximum frequency:" + mMaxFreq);
            try {
                curMax = Integer.parseInt(readSysfs(path));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Number format exception while parsing current maximum frequency");
                return;
            }
            if (curMax == mCpuAvailFreqs[0]) {
                mIsCapped = true;
            }
        }
    }

    /**
     * Method that will check the cpu core list.
     * This method will create one object per cpu core list.
     * Parameters like scaling available frequencies and scaling max frequency
     * will be initialized in the constructor of cpu core.
     */
    private void checkFreq() {
        File cpuDir = new File(CPU_PATH);
        if (!cpuDir.isDirectory()) {
            Log.e(TAG, "CPU sysfs path not found by CMS");
            return;
        }
        String cpuList[] = cpuDir.list();
        if (cpuList == null) {
            Log.e(TAG, "CPU sysfs path not accessed by CMS");
            return;
        }
        for (int i = 0; i < cpuList.length; i++) {
            if (cpuList[i].startsWith("cpu") && Character.isDigit(cpuList[i].charAt(3))) {
                CpuCore cpu = new CpuCore(cpuList[i]);
                mCpuList.add(cpu);
                Log.i(TAG, "cpu:" + cpuList[i]);
            }
        }
    }

    /**
     * Method that will check if dynamic turbo is enabled.
     * Returns boolean
     */
    private boolean IsDynamicTurboEnabled() {
        if (SystemProperties.get("persist.thermal.turbo.dynamic", "1").equals("0")) {
            return false;
        }
        return true;
    }

    /**
     * Method that will check if SOC is a throttle device.
     * Returns boolean.
     */
    private boolean IsSOCThrottleDevice() {
        for (ContributingDevice device : mCDevs) {
            if (device.getName().equals("SOC")) {
                return true;
            }
        }
        return false;
    }

    private final class BCUReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int i, j, k, battLevel, battTemp, plugType, status;
            Intent vbusIntent, thermalIntent;
            String actionReceived;
            actionReceived = intent.getAction();
            if (actionReceived != null &&
                    actionReceived.equals(intent.ACTION_BOOT_COMPLETED)) {
                thermalIntent = new Intent();
                thermalIntent.setAction(ThermalManager.ACTION_THERMAL_ZONE_STATE_CHANGED);
                thermalIntent.putExtra(ThermalManager.EXTRA_NAME, "CMS");
                thermalIntent.putExtra(ThermalManager.EXTRA_ZONE, 129);
                Log.i(TAG, "Boot completed");
                sBootCompleted = true;
                takeAction(context);
                for (i = 0; i < mCpuList.size(); i++) {
                    if (mCpuList.get(i).isCapped()) {
                        if (IsSOCThrottleDevice()) {
                            return;
                        }
                        if (IsDynamicTurboEnabled()) {
                            if (writeSysfs(CPU_PATH + mCpuList.get(i).getName()
                                    + "/cpufreq/scaling_max_freq",
                                    mCpuList.get(i).getMaxFreq()) == 0) {
                                Log.i(TAG, "Successfully released capped frequency for "
                                        + mCpuList.get(i).getName());
                            } else {
                                Log.e(TAG, "Could not release the capped frequency for "
                                        + mCpuList.get(i).getName());
                            }
                        } else {
                            // FIXME: Send a warning event and then a normal event.
                            // Otherwise thermal will ignore the dethrottle request.
                            thermalIntent.putExtra(ThermalManager.EXTRA_EVENT,
                                    ThermalManager.THERMAL_HIGH_EVENT);
                            thermalIntent.putExtra(ThermalManager.EXTRA_STATE,
                                    ThermalManager.THERMAL_STATE_WARNING);
                            context.sendBroadcastAsUser(thermalIntent, UserHandle.ALL);
                            thermalIntent.putExtra(ThermalManager.EXTRA_EVENT,
                                    ThermalManager.THERMAL_LOW_EVENT);
                            thermalIntent.putExtra(ThermalManager.EXTRA_STATE,
                                    ThermalManager.THERMAL_STATE_NORMAL);
                            context.sendBroadcastAsUser(thermalIntent, UserHandle.ALL);
                            return;
                        }
                    }
                }
                return;
            }

            battLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            battTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10;
            plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);

            if (status == BatteryManager.BATTERY_STATUS_CHARGING &&
                    plugType != BatteryManager.BATTERY_PLUGGED_USB) {
                sCurLevelState = 0;
            } else {
                for (i = mLevels.size() - 1; i >= 0; i--) {
                    if (battLevel < mLevels.get(i)) {
                        sCurLevelState = i + 1;
                        if (sCurLevelState == mLevels.size()) {
                            sCurLevelState = i;
                        }
                        break;
                    }
                    sCurLevelState = 0;
                }
                for (i = mTemps.size() - 1; i >= 0; i--) {
                    if (battTemp < mTemps.get(i)) {
                        if ((i < mTemps.size() - 1)
                                && battLevel < mLevelsTemp.get(i + 1)) {
                            sCurTempState = i + 1;
                        } else if (i == mTemps.size() - 1) {
                            // If temperature is critical, no need to check for
                            // battery level. Assign state as critical.
                            sCurTempState = i;
                        }
                        break;
                    }
                    sCurTempState = 0;
                }
            }
            takeAction(context);
        }
    }

    private void takeAction(Context context) {
        int i, j, k;
        Intent vbusIntent, thermalIntent;

        thermalIntent = new Intent();
        vbusIntent = new Intent(ACTION_USB_HOST_VBUS);

        if (sPrevLevelState != sCurLevelState) {
            Log.i(TAG, "Battery level state changed:" + sCurLevelState);
            sRecheckTempState = true;
            if (sCurLevelState == 0) {
                for (i = 0; i < mCDevs.size(); i++) {
                    if (mCDevs.get(i).getID() != 4) {
                        nativeSubsystemThrottle(mCDevs.get(i).getID(), 0);
                    }
                }
                if (sBootCompleted) {
                    vbusIntent.putExtra(EXTRA_HOST_VBUS, USB_HOST_VBUS_NORMAL);
                    context.sendBroadcastAsUser(vbusIntent, UserHandle.ALL);
                    thermalIntent.putExtra(ThermalManager.EXTRA_EVENT,
                            ThermalManager.THERMAL_LOW_EVENT);
                    thermalIntent.putExtra(ThermalManager.EXTRA_STATE,
                            ThermalManager.THERMAL_STATE_NORMAL);
                    context.sendBroadcastAsUser(thermalIntent, UserHandle.ALL);
                } else {
                    Log.i(TAG, "Boot not complete yet, not broadcasting message");
                }
            } else {
                if (sCurLevelState < sPrevLevelState) {
                    thermalIntent.putExtra(ThermalManager.EXTRA_EVENT,
                            ThermalManager.THERMAL_LOW_EVENT);
                } else {
                    thermalIntent.putExtra(ThermalManager.EXTRA_EVENT,
                            ThermalManager.THERMAL_HIGH_EVENT);
                }
                for (j = 0; j < mLevelStates.get(sCurLevelState).getDevIDList().size(); j++) {
                    for (k = 0; k < mCDevs.size(); k++) {
                        if (mCDevs.get(k).getID() ==
                                    mLevelStates.get(sCurLevelState).getDevIDList().get(j)) {
                            if (mCDevs.get(k).getID() != 4) {
                                nativeSubsystemThrottle(mCDevs.get(k).getID(),
                                            mCDevs.get(k).getThrottleTriggerByID(0).
                                            getThrottleValue(0, sCurLevelState));
                            } else if (sBootCompleted) {
                                switch (sCurLevelState) {
                                    case 1:
                                        vbusIntent.putExtra(
                                                EXTRA_HOST_VBUS, USB_HOST_VBUS_WARNING);
                                        thermalIntent.putExtra(ThermalManager.EXTRA_STATE,
                                                ThermalManager.THERMAL_STATE_WARNING);
                                        break;
                                    case 2:
                                        vbusIntent.putExtra(
                                                EXTRA_HOST_VBUS, USB_HOST_VBUS_ALERT);
                                        thermalIntent.putExtra(ThermalManager.EXTRA_STATE,
                                                ThermalManager.THERMAL_STATE_ALERT);
                                        break;
                                    case 3:
                                        vbusIntent.putExtra(
                                                EXTRA_HOST_VBUS, USB_HOST_VBUS_CRITICAL);
                                        thermalIntent.putExtra(ThermalManager.EXTRA_STATE,
                                                ThermalManager.THERMAL_STATE_CRITICAL);
                                        break;
                                }
                                context.sendBroadcastAsUser(vbusIntent, UserHandle.ALL);
                                context.sendBroadcastAsUser(thermalIntent, UserHandle.ALL);
                            } else {
                                Log.i(TAG,
                                        "Boot not complete yet, not broadcasting message");
                            }
                        }
                    }
                }
            }
            if (sBootCompleted)
                sPrevLevelState = sCurLevelState;
        }
        if (sPrevTempState != sCurTempState || sRecheckTempState) {
            Log.i(TAG, "Battery temperature state changed:" + sCurTempState);
            sRecheckTempState = false;
            for (j = 0; j < mTempStates.get(sCurTempState).getDevIDList().size(); j++) {
                for (k = 0; k < mCDevs.size(); k++) {
                    if (mCDevs.get(k).getID() ==
                            mTempStates.get(sCurTempState).getDevIDList().get(j)) {
                        if (sCurTempState != 0) {
                            nativeSubsystemThrottle(mCDevs.get(k).getID(),
                                    mCDevs.get(k).getThrottleTriggerByID(1).
                                    getThrottleValue(1, sCurTempState));
                        } else if (sCurTempState == 0 && sCurLevelState == 0) {
                            nativeSubsystemThrottle(mCDevs.get(k).getID(), 0);
                        }
                    }
                }
            }
            sPrevTempState = sCurTempState;
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

    public boolean getLevels() {
        mThrottleTriggers = mPcc.getThrottleTriggers();
        mCDevs = mPcc.getContributingDevices();

        int i, j;
        for (i = 0; i < mThrottleTriggers.size(); i++) {
            if (mThrottleTriggers.get(i).getName().equals("battLevel")) {
                mLevelTrigger = mThrottleTriggers.get(i);
                break;
            }
        }

        if (mLevelTrigger == null) {
            Log.e(TAG, "Battery level trigger not found");
            return false;
        }

        mLevelStates = mLevelTrigger.getStates();

        for (j = 0; j < mLevelStates.size(); j++) {
            mLevels.add(mLevelStates.get(j).getLevel());
        }

        for (i = 0; i < mThrottleTriggers.size(); i++) {
            if (mThrottleTriggers.get(i).getName().equals("battTemp")) {
                mTempTrigger = mThrottleTriggers.get(i);
                break;
            }
        }

        if (mTempTrigger == null) {
            Log.e(TAG, "Battery temperature trigger not found");
            return false;
        }
        mTempStates = mTempTrigger.getStates();

        for (j = 0; j < mTempStates.size(); j++) {
            mLevelsTemp.add(mTempStates.get(j).getLevel());
            mTemps.add(mTempStates.get(j).getTemp());
        }
        return true;
    }

    public CurrentMgmtService(Context context) {
        Log.v(TAG, "CurrentMgmtService start");
        mContext = context;
        mPcc = new ParseCmsConfig();
        if (mPcc == null) {
            Log.e(TAG, "Cannot get Parser object");
            return;
        }

        if (!mPcc.parseCmsThrottleConfig() || !mPcc.parseCmsDeviceConfig()) {
            Log.e(TAG, "CMS Configuration XML not found");
            return;
        }

        if (!getLevels()) {
            Log.e(TAG, "Cannot load levels and states from XML");
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        mContext.registerReceiver(new BCUReceiver(), filter);
        nativeInit();
        checkFreq();
        mUEventObserver.startObserving(DEV_PATH);
    }
}
