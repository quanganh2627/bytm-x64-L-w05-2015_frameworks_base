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

/**
 * The BatteryChargeCurrentControl class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED} Intent.
 *@hide
 */
public class BatteryChargeCurrentControl {
    private static final String TAG = "BatteryChargeCurrentControl";
    /* Maximum four levels of Throttling for Charger available */
    private static final int mMaxChargeRate = 4;
    private static String mThrottlePath;

    public static void throttleDevice(int tstate) {
        /*
         * Charging rate can be controlled in four levels 1 to 4, with
         * 4 being highest rate of charging and 1 being the lowest. The
         * thermal states are numbered as 0 to 3, with 0 being Normal,
         * and 3 being critical. So, set the rate of charging to
         * 'mMaxChargeRate - tstate'.
         */
        int newChargeRate = mMaxChargeRate - tstate;
        SysfsManager.writeSysfs(mThrottlePath, newChargeRate);
        android.util.Log.d(TAG, "New throttled charge rate: " + newChargeRate);
    }

    public static void init(String path) {
       mThrottlePath = path;
    }
}

