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

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;

/**
 * The BatteryChargeControl class implements methods to control the rate of
 * battery charging when the platform thermal conditions need it. This is done
 * by means of writing into a cooling device sysfs interface exposed by kernel
 * Thermal subsystem. The cooling device 'type' should have 'charger' string in
 * it, for it to be detected by this Java file.
 *
 * @hide
 */
public class BatteryChargeCurrentControl {
    private static final String TAG = "Thermal:BatteryChargeCurrentControl";

    public static void throttleDevice(int tstate) {
	Log.d(TAG, "throttle to state: " + tstate);
    }

    public static void init(Context context, String path, ArrayList<Integer> values) {
    }
}
