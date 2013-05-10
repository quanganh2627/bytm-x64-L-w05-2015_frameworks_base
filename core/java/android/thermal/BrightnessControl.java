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
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.util.Log;
import android.os.RemoteException;
/**
 * The BrightnessControl class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED} Intent.
 *@hide
 */

public class BrightnessControl {

    private static final String TAG = "Thermal:BrightnessControl";
    /* interface to powermanager */
    private static IPowerManager sPower;
    private static final int sDefaultBrightness = 102;
    private static final int sFullBrightness = 255;


    public static void init(String path) {
        /* Get interface to power manager service */
        sPower = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
        if (sPower == null) {
           Log.d(TAG, "couldnt get interface to powerManager");
           return;
        }
    }

    public static void throttleDevice(int tstate) {

        if (sPower == null) return;

        switch(tstate) {
        case ThermalManager.THERMAL_STATE_ALERT:
        case ThermalManager.THERMAL_STATE_CRITICAL:
              try {
                    sPower.setThermalBrightnessLimit(sDefaultBrightness, true);
              } catch (RemoteException e) {
                    Log.i(TAG, "remote exception for setThermalBrightnessLimit()");
              }
        break;
        case ThermalManager.THERMAL_STATE_WARNING:
            // We do not throttle brightness when we reach warning from normal state
            // We do not de-throttle, when we reach warning from Alert/critical
        break;
        case ThermalManager.THERMAL_STATE_NORMAL:
                /* Unlock the brightness value, so that User/ALS can change it */
                try {
                        sPower.setThermalBrightnessLimit(sFullBrightness, false);
                } catch (RemoteException e) {
                        Log.i(TAG, "remote exception for setThermalBrightnessLimit()");
                }
        break;
        default:
            Log.i(TAG, "handling 'default' case in throttleDevice method, thermalState: "+ tstate);
        }
    }
}
