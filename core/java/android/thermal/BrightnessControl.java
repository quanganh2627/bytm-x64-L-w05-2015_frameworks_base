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
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.util.Log;
import android.thermal.ThermalNotifier;
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
    private static Context mContext;

    public static void init(Context context, String path) {
        mContext = context;
        /* Get interface to power manager service */
        sPower = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
        if (sPower == null) {
           Log.d(TAG, "couldnt get interface to powerManager");
           return;
        }
    }

    public static void throttleDevice(int tstate) {
        if (sPower == null) return;

        int notificationMask = 0x0;
        switch(tstate) {
        case ThermalManager.THERMAL_STATE_WARNING:
        case ThermalManager.THERMAL_STATE_ALERT:
        case ThermalManager.THERMAL_STATE_CRITICAL:
              try {
                    sPower.setThermalBrightnessLimit(sDefaultBrightness, true);
                    notificationMask |= ThermalNotifier.VIBRATE;
                    notificationMask |= ThermalNotifier.TOAST;
                    notificationMask |= ThermalNotifier.BRIGHTNESS;
                    new ThermalNotifier(mContext, notificationMask).triggerNotification();
              } catch (RemoteException e) {
                    Log.i(TAG, "remote exception for setThermalBrightnessLimit()");
              }
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
