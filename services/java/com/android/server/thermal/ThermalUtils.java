/*
 * Copyright 2014 Intel Corporation All Rights Reserved.
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

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.File;
/**
 * The ThermalUtils class contains all common utility functionality
 * implementations
 *
 * @hide
 */
public class ThermalUtils {
    private static final String TAG = "ThermalUtils";

    /* Native methods to access Sysfs Interfaces */
    private native static String native_readSysfs(String path);
    private native static int native_writeSysfs(String path, int val);
    private native static int native_getThermalZoneIndex(String name);
    private native static int native_getThermalZoneIndexContains(String name);
    private native static int native_getCoolingDeviceIndex(String name);
    private native static int native_getCoolingDeviceIndexContains(String name);
    private native static boolean native_isFileExists(String name);

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


    public static int calculateThermalState(int temp, Integer thresholds[]) {
        if (thresholds == null) return ThermalManager.THERMAL_STATE_OFF;
        // Return OFF state if temperature less than starting of thresholds
        if (temp < thresholds[0])
            return ThermalManager.THERMAL_STATE_OFF;

        if (temp >= thresholds[thresholds.length - 2])
            return ThermalManager.THERMAL_STATE_CRITICAL;

        for (int i = 0; i < thresholds.length - 1; i++) {
            if (temp >= thresholds[i] && temp < thresholds[i + 1]) {
                return i;
            }
        }

        // should never come here
        return ThermalManager.THERMAL_STATE_OFF;
    }

    public static int getLowerThresholdTemp(int index, Integer thresholds[]) {
        if (thresholds == null) return ThermalManager.INVALID_TEMP;
        if (index < 0 || index >= thresholds.length)
            return ThermalManager.INVALID_TEMP;
        return thresholds[index];
    }

    public static int getUpperThresholdTemp(int index, Integer thresholds[]) {
        if (thresholds == null) return ThermalManager.INVALID_TEMP;
        if (index < 0 || index >= thresholds.length)
            return ThermalManager.INVALID_TEMP;
        return thresholds[index + 1];
    }

    public static void addThermalEvent(ThermalZone zone) {
        ThermalEvent event = new ThermalEvent(zone.getZoneId(),
                zone.getEventType(),
                zone.getZoneState(),
                zone.getZoneTemp(),
                zone.getZoneName());
        try {
            ThermalManager.sEventQueue.put(event);
        } catch (InterruptedException ex) {
            Log.i(TAG, "caught InterruptedException in posting to event queue");
        }
    }

    public static boolean configFilesExist(Context context) {
        if (isFileExists(ThermalManager.SENSOR_FILE_PATH) &&
                isFileExists(ThermalManager.THROTTLE_FILE_PATH)) {
            return true;
        } else {
            ThermalManager.THERMAL_SENSOR_CONFIG_XML_ID = context.getResources().getSystem().
                    getIdentifier("thermal_sensor_config", "xml", "android");
            ThermalManager.THERMAL_THROTTLE_CONFIG_XML_ID = context.getResources().getSystem().
                    getIdentifier("thermal_throttle_config", "xml", "android");
            if (ThermalManager.THERMAL_SENSOR_CONFIG_XML_ID != 0 &&
                    ThermalManager.THERMAL_THROTTLE_CONFIG_XML_ID != 0) {
                Log.i(TAG, "reading thermal config files from overlays");
                ThermalManager.sIsOverlays = true;
                return true;
            }
        }
        return false;
    }
}
