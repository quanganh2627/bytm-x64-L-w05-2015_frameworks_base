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

import java.io.File;
import android.content.Context;
/**
 * The SoC Control class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED} Intent.
 *@hide
 */
public class SoCControl {
    private static final String TAG = "Thermal:SoCControl";
    private static Context mContext;
    private static String mSoCThrottlePath;
    private static boolean mIsSoCDeviceExists = false;

    public static void throttleDevice(int thermalState) {
       if (mIsSoCDeviceExists) {
          SysfsManager.writeSysfs(mSoCThrottlePath, thermalState);
       }
    }

    public static void init(Context context, String path) {
       mContext = context;
       /* Cooling device throttle path information */
       String coolDeviceThrottlePath = "/sys/class/thermal/cooling_device";
       String coolDeviceState = "/cur_state";
       String coolDeviceType = "/type";
       int i = 0;
       /* Search throttle path for SoC cooling device */
       while (new File(coolDeviceThrottlePath + i + coolDeviceType).exists()) {
             String coolDeviceName = SysfsManager.readSysfs(coolDeviceThrottlePath + i + coolDeviceType);
             if ((coolDeviceName != null) && (coolDeviceName.equals("SoC"))) {
                   mSoCThrottlePath = coolDeviceThrottlePath + i + coolDeviceState;
                   mIsSoCDeviceExists = true;
             }
             i++;
       }
    }
}
