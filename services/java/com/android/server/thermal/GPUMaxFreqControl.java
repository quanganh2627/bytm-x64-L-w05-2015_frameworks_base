/*
 * Copyright 2013 Intel Corporation All Rights Reserved.
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
import java.io.File;
import java.util.ArrayList;


/**
 * The GPUMaxFreqControl class caps GPU frequency for various thermal States
 *
 * @hide
 */
public class GPUMaxFreqControl {
    private static final String TAG = "Thermal:GPUMaxFreqControl";
    private static String sGPUThrottlePath = null;

    private static void setThrottlePath() {
        int indx = ThermalUtils.getCoolingDeviceIndexContains("gpu_burst");

        if (indx != -1) {
            sGPUThrottlePath = ThermalManager.sCoolingDeviceBasePath + indx
                    + ThermalManager.sCoolingDeviceState;
        } else {
            // look up failed.
            sGPUThrottlePath = null;
        }
    }

    public static void throttleDevice(int thermalState) {
        if (sGPUThrottlePath != null) {
            ThermalUtils.writeSysfs(sGPUThrottlePath, thermalState);
        }
    }

    public static void init(Context context, String path, ArrayList<Integer> values) {
        // If 'path' is 'auto' enumerate from Sysfs
        if (path.equalsIgnoreCase("auto")) {
            setThrottlePath();
        // If 'path' is neither 'auto' nor a null, the given path _is_ the Sysfs path
        } else if (path != null) {
            sGPUThrottlePath = path;
        // None of the above cases. Set the throttle path to null
        } else {
            sGPUThrottlePath = null;
        }
    }
}
