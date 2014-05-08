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
import android.os.IPowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;

import java.util.ArrayList;

/**
 * The BrightnessControl class can limit the brightness to a configured level
 * when the platform temperature exceeds certain limits.
 *
 * @hide
 */
public class BrightnessControl {

    private static final String TAG = "Thermal:BrightnessControl";

    // Interface to powermanager
    private static IPowerManager sPower;

    private static final int sFullBrightness = 255;

    private static Context sContext;

    private static int sBrightnessValuesPercentage[];

    private static boolean sToast = false;

    private static boolean sVibra = false;

    private static int sNotificationMask = 0;

    private static int sMaxThrottleValues;

    private static boolean sIsThrottlingPossible = false;

    private static void setDefaultBrightnessValues() {
        sBrightnessValuesPercentage = new int[sMaxThrottleValues];

        // Use 100% brightness for Normal/Warning and 50% brightness for
        // Alert/Critical
        sBrightnessValuesPercentage[0] = 100;
        sBrightnessValuesPercentage[1] = 100;
        sBrightnessValuesPercentage[2] = 50;
        sBrightnessValuesPercentage[3] = 50;
    }

    public static void init(Context context, String path, ArrayList<Integer> values) {
        sContext = context;

        if (values == null) {
            sMaxThrottleValues = ThermalManager.DEFAULT_NUM_THROTTLE_VALUES;
            setDefaultBrightnessValues();
        } else {
            if (values.size() < ThermalManager.DEFAULT_NUM_THROTTLE_VALUES) {
                Log.i(TAG, "Brightness Plugin throttle values < :"
                        + ThermalManager.DEFAULT_NUM_THROTTLE_VALUES);
                return;
            }
            sMaxThrottleValues = ThermalManager.DEFAULT_NUM_THROTTLE_VALUES;
            sBrightnessValuesPercentage = new int[values.size()];
            for (int i = 0; i < values.size(); i++)
                sBrightnessValuesPercentage[i] = values.get(i);
        }

        /* Get interface to power manager service */
        sPower = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
        if (sPower == null) {
            Log.d(TAG, "could not get interface to PowerManager");
            return;
        }
        readDisplayThrottleNotifierProperties();
        initializeNotifierProperties();
        sIsThrottlingPossible = true;
    }

    public static void throttleDevice(int tstate) {
        if (sIsThrottlingPossible == false || sPower == null || tstate < 0
                || tstate >= sMaxThrottleValues) {
            Log.i(TAG, "Brightness throttling rejected due to error");
            return;
        }

        int maxBrightnessAllowed = (sBrightnessValuesPercentage[tstate] * sFullBrightness) / 100;

        try {
            sPower.setThermalBrightnessLimit(maxBrightnessAllowed, true);
        } catch (RemoteException e) {
            Log.i(TAG, "remote exception for setThermalBrightnessLimit()");
        }

        // Notify user if we are limiting brightness. Value '2' works only for default brightness
        // throttle values. If user changes throttle values then this number has to be revisited.
        // Needs to be fixed.
        if (tstate >= 2)
            new ThermalNotifier(sContext, sNotificationMask).triggerNotification();
    }

    private static void readDisplayThrottleNotifierProperties() {
        try {
            if ("1".equals(SystemProperties.get("persist.thermal.display.msg", "0"))) {
                sToast = true;
            }
            if ("1".equals(SystemProperties.get("persist.thermal.display.vibra", "0"))) {
                sVibra = true;
            }
        } catch (java.lang.IllegalArgumentException e) {
            Log.e(TAG, "exception caught in reading thermal system properties");
        }
    }

   private static void initializeNotifierProperties() {
        if (sVibra) sNotificationMask |= ThermalNotifier.VIBRATE;
        if (sToast) sNotificationMask |= ThermalNotifier.TOAST;
        sNotificationMask |= ThermalNotifier.BRIGHTNESS;
   }
}
