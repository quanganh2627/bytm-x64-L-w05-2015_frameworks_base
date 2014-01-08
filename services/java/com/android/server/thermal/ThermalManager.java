/*
 * Copyright 2012 Intel Corporation All Rights Reserved.
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

package com.android.server.thermal;

import android.os.UEventObserver;
import android.os.SystemProperties;
import android.util.Log;

import java.io.IOException;
import java.io.File;
import java.lang.NumberFormatException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.NoSuchElementException;
/**
 * The ThermalManager class contains data structures that are common to both
 * Thermal Sensor/Zone and Cooling device parts.
 *
 * @hide
 */
public class ThermalManager {
    private static final String TAG = "ThermalManager";
    private static String sVersion;
    private static final String ITUX_VERSION_PROPERTY = "ro.thermal.ituxversion";
    public static final String SENSOR_FILE_PATH = "/system/etc/thermal_sensor_config.xml";

    public static final String THROTTLE_FILE_PATH = "/system/etc/thermal_throttle_config.xml";

    public static String sUEventDevPath = "DEVPATH=/devices/virtual/thermal/thermal_zone";

    /**
     * Thermal Zone State Changed Action: This is broadcast when the state of a
     * thermal zone changes.
     */
    public static final String ACTION_THERMAL_ZONE_STATE_CHANGED =
            "com.android.server.thermal.action.THERMAL_ZONE_STATE_CHANGED";
    public static PlatformInfo sPlatformInfo;
    /* List of all Thermal zones in the platform */
    public static ArrayList<ThermalZone> sThermalZonesList;

    /* Hashtable of (ZoneID and ZoneCoolerBindingInfo object) */
    public static Hashtable<Integer, ZoneCoolerBindingInfo> sZoneCoolerBindMap =
            new Hashtable<Integer, ZoneCoolerBindingInfo>();

    /* Hashtable of (Cooling Device ID and ThermalCoolingDevice object) */
    public static Hashtable<Integer, ThermalCoolingDevice> sCDevMap =
            new Hashtable<Integer, ThermalCoolingDevice>();

    /* Hashtable of (Sensor/Zone name and Sensor/Zone objects) */
    public static Hashtable<String, ThermalSensor> sSensorMap =
            new Hashtable<String, ThermalSensor>();

    public static Hashtable<String, ThermalZone> sSensorZoneMap =
            new Hashtable<String, ThermalZone>();

    public static final int CRITICAL_TRUE = 1;
    public static final int CRITICAL_FALSE = 0;
    /* sZoneCriticalPendingMap stores info whether a zone is in critical state and platform
     * shutdown has not yet occured due to some scenario like ongoing emergency call
     **/
    public static Hashtable<Integer, Integer> sZoneCriticalPendingMap = null;
    /* this lock is to access sZoneCriticalPendingMap synchronously */
    private static final Object sCriticalPendingLock = new Object();
    /* this count keeps track of number of zones in pending critical state.When
     * sZoneCriticalPendingMap is updated, the count is either incremented or
     * decremented depending on whether criical pending flag for a zone is true/
     * false. By keeping a count we can avoid scanning through the entire map to
     * see if there is a pending critical shutdown
     **/
    private static int sCriticalZonesCount = 0;

    /* this array list tracks the sensors for which event observer has been added */
    public static ArrayList<Integer> sSensorsRegisteredToObserver =
            new ArrayList<Integer>();
    /* Blocking queue to hold thermal events from thermal zones */
    private static final int EVENT_QUEUE_SIZE = 10;

    public static BlockingQueue<ThermalEvent> sEventQueue = new ArrayBlockingQueue<ThermalEvent>(EVENT_QUEUE_SIZE);
    /* this lock is to handle uevent callbacks synchronously */
    private static final Object sLock = new Object();

    /**
     * Extra for {@link ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the thermal zone.
     */
    public static final String EXTRA_ZONE = "zone";

    /**
     * Extra for {@link ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the thermal state of the zone.
     */
    public static final String EXTRA_STATE = "state";

    /**
     * Extra for {@link ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the thermal event type for the zone.
     */
    public static final String EXTRA_EVENT = "event";

    /**
     * Extra for {@link ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the temperature of the zone.
     */
    public static final String EXTRA_TEMP = "temp";

    /**
     * Extra for {@link ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * String containing the name of the zone.
     */
    public static final String EXTRA_NAME = "name";

    /* values for "STATE" field in the THERMAL_STATE_CHANGED Intent */
    public static final int THERMAL_STATE_OFF = -1;

    public static final int THERMAL_STATE_NORMAL = 0;

    public static final int THERMAL_STATE_WARNING = 1;

    public static final int THERMAL_STATE_ALERT = 2;

    public static final int THERMAL_STATE_CRITICAL = 3;

    public static final int NUM_THERMAL_STATES = 5;

    public static final String STATE_NAMES[] = {
            "OFF", "NORMAL", "WARNING", "ALERT", "CRITICAL"
    };

    /* values of the "EVENT" field in the THERMAL_STATE_CHANGED intent */
    /* Indicates type of event */
    public static final int THERMAL_LOW_EVENT = 0;

    public static final int THERMAL_HIGH_EVENT = 1;

    public static final int INVALID_TEMP = 0xDEADBEEF;

    /* base sysfs path for sensors */
    public static final String sSysfsSensorBasePath = "/sys/class/thermal/thermal_zone";

    public static final String sCoolingDeviceBasePath = "/sys/class/thermal/cooling_device";

    public static final String sCoolingDeviceState = "/cur_state";

    public static final int THROTTLE_MASK_ENABLE = 1;

    public static final int DETHROTTLE_MASK_ENABLE = 1;

    /**
     * Magic number (agreed upon between the Thermal driver and the Thermal Service)
     * symbolising Dynamic Turbo OFF
     */
    public static final int DISABLE_DYNAMIC_TURBO = 0xB0FF;

    public static boolean sIsDynamicTurboEnabled = false;

    /* thermal notifier system properties for shutdown action */
    public static boolean sShutdownTone = false;

    public static boolean sShutdownToast = false;

    public static boolean sShutdownVibra = false;

    /* Native methods to access Sysfs Interfaces */
    private native static String native_readSysfs(String path);
    private native static int native_writeSysfs(String path, int val);
    private native static int native_getThermalZoneIndex(String name);
    private native static int native_getThermalZoneIndexContains(String name);
    private native static int native_getCoolingDeviceIndex(String name);
    private native static int native_getCoolingDeviceIndexContains(String name);
    private native static boolean native_isFileExists(String name);

    /**
     * This class stores the zone throttle info. It contains the zoneID,
     * CriticalShutdown flag and CoolingDeviceInfo arraylist.
     */
    public static class ZoneCoolerBindingInfo {
        private int mZoneID;

        private int mIsCriticalActionShutdown;

        /* cooler ID mask, 1 - throttle device, 0- no action, -1- dont care */
        private ArrayList<CoolingDeviceInfo> mCoolingDeviceInfoList = null;

        private CoolingDeviceInfo lastCoolingDevInfoInstance = null;

        public class CoolingDeviceInfo {
            private int CDeviceID;

            private ArrayList<Integer> DeviceThrottleMask = null;

            private ArrayList<Integer> DeviceDethrottleMask = null;

            public CoolingDeviceInfo() {}

            public void createThrottleStateMask(String mask) {
                try {
                    for (String str : mask.split(",")) {
                        this.DeviceThrottleMask.add(Integer.parseInt(str));
                    }
                } catch (NumberFormatException e) {
                    Log.i(TAG,"exception caught in createThrottleStateMask: " + e.getMessage());
                }
            }

            public void createDeThrottleStateMask(String mask) {
                try {
                    for (String str : mask.split(",")) {
                        this.DeviceDethrottleMask.add(Integer.parseInt(str));
                    }
                } catch (NumberFormatException e) {
                    Log.i(TAG,"exception caught in createDeThrottleStateMask: " + e.getMessage());
                }
            }

            public int getCoolingDeviceId() {
                return CDeviceID;
            }

            public void setCoolingDeviceId(int deviceID) {
                CDeviceID = deviceID;
            }

            public ArrayList<Integer> getThrottleMaskList() {
                return DeviceThrottleMask;
            }

            public ArrayList<Integer> getDeThrottleMaskList() {
                return DeviceDethrottleMask;
            }

            public void setThrottleMaskList(ArrayList<Integer> list) {
                this.DeviceThrottleMask = list;
            }

            public void setDeThrottleMaskList(ArrayList<Integer> list) {
                this.DeviceDethrottleMask = list;
            }

        }

        public ZoneCoolerBindingInfo() {
        }

        public ArrayList<CoolingDeviceInfo> getCoolingDeviceInfoList() {
            return mCoolingDeviceInfoList;
        }

        public void createNewCoolingDeviceInstance() {
            lastCoolingDevInfoInstance = new CoolingDeviceInfo();
        }

        public CoolingDeviceInfo getLastCoolingDeviceInstance() {
            return lastCoolingDevInfoInstance;
        }

        public void setCDeviceInfoMaskList(ArrayList<CoolingDeviceInfo> mList) {
            mCoolingDeviceInfoList = mList;
        }

        public void setZoneID(int zoneID) {
            mZoneID = zoneID;
        }

        public int getZoneID() {
            return mZoneID;
        }

        public void setCriticalActionShutdown(int val) {
            mIsCriticalActionShutdown = val;
        }

        public int getCriticalActionShutdown() {
            return mIsCriticalActionShutdown;
        }

        public void setCoolingDeviceInfoList(ArrayList<CoolingDeviceInfo> devinfoList) {
            mCoolingDeviceInfoList = devinfoList;
        }

        public void initializeCoolingDeviceInfoList() {
            mCoolingDeviceInfoList = new ArrayList<CoolingDeviceInfo>();
        }

        public void addCoolingDeviceToList(CoolingDeviceInfo CdeviceInfo) {
            mCoolingDeviceInfoList.add(CdeviceInfo);
        }
    }

    /* platform information */
    public static class PlatformInfo {
       public int mMaxThermalStates;

       public int getMaxThermalStates() {
            return mMaxThermalStates;
       }

       public void printAttrs() {
           Log.i(TAG, Integer.toString(mMaxThermalStates));
       }
       public PlatformInfo() {}
    }

    /* methods */
    public ThermalManager() {
        // empty constructor
    }

    public static String getVersion() {
        return sVersion;
    }

    public static void loadiTUXVersion() {
        sVersion = SystemProperties.get(ITUX_VERSION_PROPERTY, "none");
        if (sVersion.equalsIgnoreCase("none")) {
            Log.i(TAG, "iTUX Version not found!");
        } else {
            Log.i(TAG, "iTUX Version:" + sVersion);
        }
    }

    // native methods to access kernel sysfs layer
    public static String readSysfs(String path) {
        try {
            return native_readSysfs(path);
        } catch (UnsatisfiedLinkError e) {
            Log.i(TAG, "caught UnsatisfiedLinkError in readSysfs");
            return null;
        }
    }

    public static int writeSysfs(String path, int val) {
        try {
            return native_writeSysfs(path, val);
        } catch (UnsatisfiedLinkError e) {
            Log.i(TAG, "caught UnsatisfiedLinkError in writeSysfs");
            return -1;
        }
    }

    public static int getThermalZoneIndex(String name) {
        try {
            return native_getThermalZoneIndex(name);
        } catch (UnsatisfiedLinkError e) {
            Log.i(TAG, "caught UnsatisfiedLinkError in getThermalZoneIndex");
            return -1;
        }
    }

    public static int getThermalZoneIndexContains(String name) {
        try {
            return native_getThermalZoneIndexContains(name);
        } catch (UnsatisfiedLinkError e) {
            Log.i(TAG, "caught UnsatisfiedLinkError in getThermalZoneIndexContains");
            return -1;
        }
    }

    public static int getCoolingDeviceIndex(String name) {
        try {
            return native_getCoolingDeviceIndex(name);
        } catch (UnsatisfiedLinkError e) {
            Log.i(TAG, "caught UnsatisfiedLinkError in getCoolingDeviceIndex");
            return -1;
        }
    }

    public static int getCoolingDeviceIndexContains(String name) {
        try {
            return native_getCoolingDeviceIndexContains(name);
        } catch (UnsatisfiedLinkError e) {
            Log.i(TAG, "caught UnsatisfiedLinkError in getCoolingDeviceIndexContains");
            return -1;
        }
    }

    public static boolean isFileExists(String path) {
        try {
            return native_isFileExists(path);
        } catch (UnsatisfiedLinkError e) {
            Log.i(TAG, "caught UnsatisfiedLinkError in isFileExists");
            return false;
        }
    }


    public ArrayList<ThermalZone> getThermalZoneList() {
        return sThermalZonesList;
    }

    public static int calculateThermalState(int temp, Integer mThresholds[]) {
        // Return OFF state if temperature less than starting of thresholds
        if (temp < mThresholds[0])
            return ThermalManager.THERMAL_STATE_OFF;

        if (temp >= mThresholds[mThresholds.length - 2])
            return ThermalManager.THERMAL_STATE_CRITICAL;

        for (int i = 0; i < mThresholds.length - 1; i++) {
            if (temp >= mThresholds[i] && temp < mThresholds[i + 1]) {
                return i;
            }
        }

        // should never come here
        return ThermalManager.THERMAL_STATE_OFF;
    }

    // this method builds a map of active sensors
    public static void buildSensorMap() {
        ArrayList<ThermalSensor> tempSensorList;
        if (sThermalZonesList == null) return;
        for (ThermalZone t : sThermalZonesList) {
            tempSensorList = t.getThermalSensorList();
            if (tempSensorList ==  null) continue;
            for (ThermalSensor s : tempSensorList) {
                /* put only active sensors in hashtable */
                if (s != null && s.getSensorActiveStatus() &&
                        !sSensorMap.containsKey(s.getSensorName())) {
                    sSensorMap.put(s.getSensorName(), s);
                    sSensorZoneMap.put(s.getSensorName(), t);
                }
            }
        }
    }

    public static void programSensorThresholds(ThermalSensor s) {
        int sensorState = s.getSensorThermalState();
        int lowerTripPoint = s.getLowerThresholdTemp(sensorState);
        int upperTripPoint = s.getUpperThresholdTemp(sensorState);
        // write to sysfs
        if (lowerTripPoint != -1 && upperTripPoint != -1) {
            ThermalManager.writeSysfs(s.getSensorLowTempPath(), lowerTripPoint);
            ThermalManager.writeSysfs(s.getSensorHighTempPath(), upperTripPoint);
        }
    }

    private static UEventObserver mUEventObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            String sensorName;
            int sensorTemp, errorVal, eventType = -1;
            ThermalZone zone;
            synchronized (sLock) {
                // Name of the sensor and current temperature are mandatory parameters of an UEvent
                sensorName = event.get("NAME");
                sensorTemp = Integer.parseInt(event.get("TEMP"));

                // eventType is an optional parameter. so, check for null case
                if (event.get("EVENT") != null)
                   eventType = Integer.parseInt(event.get("EVENT"));

                Log.i(TAG, "UEvent received for sensor:" + sensorName + " temp:" + sensorTemp);

                if (sensorName != null) {
                    ThermalSensor s = sSensorMap.get(sensorName);
                    if (s == null) return;

                    // Adjust the sensor temperature based on the 'error correction' temperature.
                    // For 'LOW' event, debounce interval will take care of this.
                    errorVal = s.getErrorCorrectionTemp();
                    if (eventType == THERMAL_HIGH_EVENT)
                       sensorTemp += errorVal;

                    // call isZoneStateChanged for zones mapped to this sensor
                    zone = sSensorZoneMap.get(sensorName);
                    if (zone != null && zone.isZoneStateChanged(s, sensorTemp)) {
                        ThermalManager.addThermalEvent(zone);
                        // reprogram threshold
                        programSensorThresholds(s);
                    }
                }
            }
        }
    };

    public static void registerUevent(ThermalZone z) {
        String devPath;
        int indx;
        ArrayList<ThermalSensor> tempSensorList = null;
        tempSensorList = z.getThermalSensorList();
        if (tempSensorList == null) return;
        for (ThermalSensor s : tempSensorList) {
            /**
             * If sensor is not already registered and sensor is active, add a
             * uevent listener
             */
            if (sSensorsRegisteredToObserver.indexOf(s.getSensorID()) == -1
                    && s.getSensorActiveStatus() && !(s.getUEventDevPath().equals("invalid"))) {
                String eventObserverPath = s.getUEventDevPath();
                if (eventObserverPath.equals("auto")) {
                    // build the sensor UEvent listener path
                    indx = s.getSensorSysfsIndx();
                    if (indx == -1) {
                        Log.i(TAG, "Cannot build UEvent path for sensor:" + s.getSensorName());
                        continue;
                    } else {
                        devPath = sUEventDevPath + indx;
                    }
                } else {
                    devPath = eventObserverPath;
                }

                sSensorsRegisteredToObserver.add(s.getSensorID());
                s.updateSensorAttributes(z.getDBInterval());
                mUEventObserver.startObserving(devPath);
                // program high and low trip points for sensor
                programSensorThresholds(s);
            }
        }
    }

    public static void addThermalEvent(ThermalZone zone) {
        ThermalEvent event = new ThermalEvent(zone.getZoneId(),
                zone.getEventType(),
                zone.getZoneState(),
                zone.getZoneTemp(),
                zone.getZoneName());
        try {
            sEventQueue.put(event);
        } catch (InterruptedException ex) {
            Log.i(TAG, "caught InterruptedException in posting to event queue");
        }
    }

    public static void startMonitoringZones() {
        if (sThermalZonesList == null) return;
        for (ThermalZone zone : sThermalZonesList) {
            if (zone.isUEventSupported()) {
                registerUevent(zone);
            } else {
                // start polling thread for each zone
                zone.startMonitoring();
            }
        }
    }

    public static boolean configFilesExist() {
        return ThermalManager.isFileExists(SENSOR_FILE_PATH) &&
                ThermalManager.isFileExists(THROTTLE_FILE_PATH);
    }

    public static void readShutdownNotiferProperties() {
        try {
            if ("1".equals(SystemProperties.get("persist.thermal.shutdown.msg", "0"))) {
                sShutdownToast = true;
            }
            if ("1".equals(SystemProperties.get("persist.thermal.shutdown.tone", "0"))) {
                sShutdownTone = true;
            }
            if ("1".equals(SystemProperties.get("persist.thermal.shutdown.vibra", "0"))) {
                sShutdownVibra = true;
            }
        } catch (java.lang.IllegalArgumentException e) {
            Log.e(TAG, "exception caught in reading thermal system properties");
        }
    }

    /**
     * This function scans through all the thermal zones and its associated
     * sensors to check if at least one sensor is active. If no sensors are
     * active, the Thermal service exits.
     */
    public static boolean isThermalServiceNeeded() {
        for (ThermalZone z : sThermalZonesList) {
            if (z != null && z.getZoneActiveStatus()) {
                return true;
            }
        }
        return false;
    }

    public static void unregisterZoneReceivers() {
        if (sThermalZonesList == null) return;
        for (ThermalZone z : sThermalZonesList) {
            if (z == null) continue;
            z.unregisterReceiver();
        }
    }

    public static void initializeZoneCriticalPendingMap() {
        if (sThermalZonesList == null) return;
        sZoneCriticalPendingMap = new Hashtable<Integer, Integer>();
        if (sZoneCriticalPendingMap == null) return;
        Enumeration en;
        try {
            // look up for zone list is performed from sZoneCoolerBindMap instead of
            // sThermalZonesList since some non thermal zones may not have entry in
            // sThermalZonesList. This is because such zones only have entry in throttle
            // config file and not in sensor config files.
            en = sZoneCoolerBindMap.keys();
            while (en.hasMoreElements()) {
                int zone = (Integer) en.nextElement();
                sZoneCriticalPendingMap.put(zone, CRITICAL_FALSE);
            }
        } catch (NoSuchElementException e) {
            Log.i(TAG, "NoSuchElementException in InitializeZoneCriticalPendingMap()");
        }
    }

    /*
     * updateZoneCriticalPendingMap updates sZoneCriticalPendingMap synchronously.
     * sCriticalZonesCount is incremented iff old value in the map for the zone is
     * FALSE (ensures count is incremented only once for a zone) and decremented
     * iff oldval is TRUE (ensures no negative value for count)
     **/
    public static boolean updateZoneCriticalPendingMap(int zoneid, int flag) {
        synchronized (sCriticalPendingLock) {
            if (sZoneCriticalPendingMap == null) return false;
                Integer oldVal = sZoneCriticalPendingMap.get(zoneid);
                if (oldVal == null) return false;
                sZoneCriticalPendingMap.put(zoneid, flag);
                if (oldVal == CRITICAL_FALSE && flag == CRITICAL_TRUE) {
                   sCriticalZonesCount++;
                } else if (oldVal == CRITICAL_TRUE && flag == CRITICAL_FALSE) {
                   sCriticalZonesCount--;
                }
                return true;
        }
    }

    public static boolean checkShutdownCondition() {
        synchronized (sCriticalPendingLock) {
           return sCriticalZonesCount > 0;
        }
    }
}
