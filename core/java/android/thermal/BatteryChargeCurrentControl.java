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

import android.thermal.ThermalManager;
import android.thermal.SysfsManager;
import java.io.File;
import android.util.Log;

/**
 * The BatteryChargeCurrentControl class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED} Intent.
 *@hide
 */
public class BatteryChargeCurrentControl {
    private static final String TAG = "Thermal:BatteryChargeCurrentControl";
    private static String mThrottlePath;

    private static void setThrottlePath() {
        int count = 0;
        while (new File (ThermalManager.mCoolingDeviceBasePath + count + ThermalManager.mCoolingDeviceType).exists()) {
           String name = SysfsManager.readSysfs(ThermalManager.mCoolingDeviceBasePath + count + ThermalManager.mCoolingDeviceType);
           if (name != null && name.contains("_charger")) {
              mThrottlePath = (ThermalManager.mCoolingDeviceBasePath + count + ThermalManager.mCoolingDeviceState);
              break;
           }
           count++;
        }
    }

    public static void throttleDevice(int tstate) {
        /*
         * Charging rate can be controlled in four levels 0 to 3, with
         * 0 being highest rate of charging and 3 being the lowest.
         */
        if (mThrottlePath != null) {
          SysfsManager.writeSysfs(mThrottlePath, tstate);
          Log.d(TAG, "New throttled charge rate: " + tstate);
        }
    }

    public static void init(String path) {
         if (path != null) {
            if (path.equalsIgnoreCase("auto")) {
               setThrottlePath();
            } else {
               mThrottlePath = path;
            }
         } else {
         Log.i(TAG, "Throttle path is null");
         }
    }
}
