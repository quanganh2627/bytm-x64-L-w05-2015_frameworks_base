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

package android.thermal;

import android.thermal.ThermalZone;
import android.thermal.ThermalSensor;
import android.thermal.ThermalCoolingDevice;
import android.thermal.SysfsManager;
import android.util.Log;
import android.os.UEventObserver;
import java.util.Hashtable;
import java.util.ArrayList;
import java.io.IOException;
import java.lang.NumberFormatException;
import java.io.File;

public class ThermalManager {
    public static final String SENSOR_FILE_PATH = "/system/etc/thermal_sensor_config.xml";
    public static final String THROTTLE_FILE_PATH = "/system/etc/thermal_throttle_config.xml";
    public static String uEventDevPath = "DEVPATH=devices/virtual/thermal/thermal_zone";
    public static PlatformInfo mPlatformInfo;
    private static final String TAG = "ThermalManager";
    public static ArrayList<ThermalZone> mThermalZonesList;
    public static Hashtable<Integer, ZoneCoolerBindingInfo> listOfZones = new Hashtable<Integer, ZoneCoolerBindingInfo>();
    public static Hashtable<Integer, ThermalCoolingDevice> listOfCoolers = new Hashtable<Integer, ThermalCoolingDevice>();
    public static Hashtable<String, ThermalSensor> sensorMap = new Hashtable<String, ThermalSensor>();
    public static Hashtable<String, ThermalZone> sensorZoneMap = new Hashtable<String, ThermalZone>();
    /* this array list tracks the sensors for which event observer has been added */
    public static ArrayList<Integer> sensorsRegisteredToObserver = new ArrayList<Integer>();
    /* this lock is to handle uevent callbacks synchronously */
    private static final Object mLock = new Object();

    /**
     * Extra for {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the thermal zone.
     */
    public static final String EXTRA_ZONE = "zone";
    /**
     * Extra for {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the thermal state of the zone.
     */
    public static final String EXTRA_STATE = "state";
    /**
     * Extra for {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the thermal event type for the zone.
     */
    public static final String EXTRA_EVENT = "event";
    /**
     * Extra for {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the temperature of the zone.
     */
    public static final String EXTRA_TEMP = "temp";
    /**
     * Extra for {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * String containing the name of the zone.
     */
    public static final String EXTRA_NAME = "name";

    /* values for "STATE" field in the THERMAL_STATE_CHANGED Intent */
    public static final int THERMAL_STATE_OFF = -1;
    public static final int THERMAL_STATE_NORMAL = 0;
    public static final int THERMAL_STATE_WARNING = 1;
    public static final int THERMAL_STATE_ALERT = 2;
    public static final int THERMAL_STATE_CRITICAL = 3;

    public static final String STATE_NAMES[] = {"OFF", "NORMAL", "WARNING", "ALERT", "CRITICAL"};
    /* values of the "EVENT" field in the THERMAL_STATE_CHANGED intent */
    /* Indicates type of event */
    public static final int THERMAL_LOW_EVENT = 0;
    public static final int THERMAL_HIGH_EVENT = 1;

    public static final int INVALID_TEMP = 0xDEADBEEF;
    /* base sysfs path for sensors */
    public static final String mSysfsSensorBasePath = "/sys/class/thermal/thermal_zone";
    public static final String mSysfsSensorType = "/type";
    public static final int THROTTLE_MASK_ENABLE = 1;
    public static final int DETHROTTLE_MASK_ENABLE = 1;

    private static final Object sLock = new Object();

    /**
     * this class stores the zone throttle info.It contains the zoneID,
     * CriticalShutdown flag and CoolingDeviceInfo arraylist.
     */
    public static class ZoneCoolerBindingInfo {
        private int mZoneID;
        private int mIsCriticalActionShutdown;
        /* cooler ID mask, 1 - throttle device, 0- no action, -1- dont care */
        private ArrayList <CoolingDeviceInfo> mCoolingDeviceInfoList = null;
        private CoolingDeviceInfo lastCoolingDevInfoInstance = null;
        public class CoolingDeviceInfo {
            private int CDeviceID;
            private ArrayList<Integer> DeviceThrottleMask = new ArrayList<Integer>();
            private ArrayList<Integer> DeviceDethrottleMask = new ArrayList<Integer>();
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
            public void setThrottleMaskList (ArrayList<Integer> list) {
                    this.DeviceThrottleMask = list;
            }

            public void setDeThrottleMaskList (ArrayList<Integer> list) {
                    this.DeviceDethrottleMask = list;
            }

        }

        public ZoneCoolerBindingInfo() {}

        public ArrayList<CoolingDeviceInfo> getCoolingDeviceInfoList() {
            return mCoolingDeviceInfoList;
        }

        public void createNewCoolingDeviceInstance() {
            lastCoolingDevInfoInstance = new CoolingDeviceInfo();
        }

        public CoolingDeviceInfo getLastCoolingDeviceInstance() {
            return lastCoolingDevInfoInstance;
        }

        public void setCDeviceInfoMaskList(ArrayList <CoolingDeviceInfo> mList) {
            mCoolingDeviceInfoList = mList;
        }

        public void setZoneID(int zoneID) {
            this.mZoneID = zoneID;
        }

        public int getZoneID() {
            return mZoneID;
        }

        public void setCriticalActionShutdown(int val) {
            this.mIsCriticalActionShutdown = val;
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

    public ArrayList<ThermalZone> getThermalZoneList() {
        return mThermalZonesList;
    }

    /* this method builds a map of active sensors */
    public static void buildSensorMap() {
        ArrayList<ThermalSensor> tempSensorList;
        for (ThermalZone t : mThermalZonesList) {
            tempSensorList = t.getThermalSensorList();
            for (ThermalSensor s : tempSensorList) {
                /* put only active sensors in hashtable */
                if (s.getSensorActiveStatus() && !sensorMap.containsKey(s.getSensorName())) {
                    sensorMap.put(s.getSensorName(),s);
                    sensorZoneMap.put(s.getSensorName(), t);
                }
            }
        }
    }

    public static void programSensorThresholds(ThermalSensor s) {
         int sensorState = s.getSensorThermalState();
         int lowerTripPoint = s.getLowerThresholdTemp(sensorState);
         int upperTripPoint = s.getUpperThresholdTemp(sensorState);
         // write to sysfs
         if (lowerTripPoint != -1 &&
             upperTripPoint != -1) {
             SysfsManager.writeSysfs(s.getSensorLowTempPath(), lowerTripPoint);
             SysfsManager.writeSysfs(s.getSensorHighTempPath(), upperTripPoint);
         }
    }

    private static UEventObserver mUEventObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            String sensorName, temp;
            ThermalZone zone;
            synchronized (mLock) {
                int sensorTemp = 0;
                sensorName = event.get("NAME");
                temp = event.get("TEMP");
                Log.i(TAG, "UEvent received for sensor:" + sensorName + " temp:" + temp);
                // call isZoneStateChanged() for zones registed to this sensor
                if (sensorName != null) {
                    ThermalSensor s = sensorMap.get(sensorName);
                    if (s == null) return;
                    sensorTemp = Integer.parseInt(temp);
                    // for zone to which this sensor is mapped,
                    // call iszonestatechanged
                    zone = sensorZoneMap.get(sensorName);
                    if (zone != null) {
                        if (zone.isZoneStateChanged(s,sensorTemp)) {
                            zone.update();
                            // reprogram threshold
                            programSensorThresholds(s);
                        }
                    }
                }
            }
        }
    };

    public static void registerUevent(ThermalZone z) {
        /**
        * browse through all the sensors in the zone and add the path
        * to eventobserver
        */
        ArrayList<ThermalSensor> tempSensorList = z.getThermalSensorList();
        boolean subsystemLevelObserver = false;
        String devPath, sensorSysfs;
        char count;
        for (ThermalSensor s : tempSensorList) {
            /**
            * If sensor is not already resgisterd and sensor is active,
            * add a uevent listener
            */
            if (sensorsRegisteredToObserver.indexOf(s.getSensorID()) == -1 &&
                s.getSensorActiveStatus() &&
                !s.getUEventDevPath().equals("invalid")) {
                String eventObserverPath = s.getUEventDevPath();
                if (eventObserverPath.equals("auto")) {
                    /* build the sensor listener path */
                    sensorSysfs = s.getSensorSysfsPath();
                    count = sensorSysfs.charAt(sensorSysfs.length() - 2);
                    devPath = uEventDevPath + count;
                } else {
                    devPath = eventObserverPath;
                }

                sensorsRegisteredToObserver.add(s.getSensorID());
                s.updateSensorAttributes(z.getDBInterval());
                mUEventObserver.startObserving(devPath);
                // program high low trip points for sensor
                programSensorThresholds(s);
            }
        }
    }

     public static void startMonitoringZones() {
        for (ThermalZone zone: mThermalZonesList) {
            if (zone.isUEventSupported()) {
                registerUevent(zone);
            } else {
                // start polling thread for each zone
                zone.startMonitoring();
            }
        }
    }

    public static boolean configFilesExist() {
         return ((new File(SENSOR_FILE_PATH).exists()) &&
                (new File(THROTTLE_FILE_PATH).exists()));
    }
}
