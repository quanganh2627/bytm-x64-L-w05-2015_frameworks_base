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
import android.content.Context;
import android.util.Log;
import android.thermal.ThermalManager;
/**
 *Modem Cooling class
 *@hide
 */
public class ModemCooling {
    private static final String TAG = "Thermal:ModemCooling";
    private static Context mContext;
    public static void init(Context context, String path) {
        mContext = context;
    }

    public static void throttleDevice(int tstate) {
        switch(tstate) {
        case ThermalManager.THERMAL_STATE_NORMAL:
        case ThermalManager.THERMAL_STATE_WARNING:
        case ThermalManager.THERMAL_STATE_ALERT:
        case ThermalManager.THERMAL_STATE_CRITICAL:
        break;
        default:
            Log.i(TAG, "handling 'default' case in throttleDevice method, thermalState: "+ tstate);
        }
    }
}

