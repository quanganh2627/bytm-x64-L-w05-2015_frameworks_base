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

    private static int sMaxAllowedBrightness = 255;

    private static Context sContext;

    private static int sBrightnessValuesPercentage[];

    private static boolean sToast = false;

    private static boolean sVibra = false;

    private static int sNotificationMask = 0;

    private static void setDefaultBrightnessValues() {
        sBrightnessValuesPercentage = new int[ThermalManager.NUM_THERMAL_STATES - 1];

        // Use 100% brightness for Normal/Warning and 50% brightness for
        // Alert/Critical
        sBrightnessValuesPercentage[ThermalManager.THERMAL_STATE_NORMAL] = 100;
        sBrightnessValuesPercentage[ThermalManager.THERMAL_STATE_WARNING] = 100;
        sBrightnessValuesPercentage[ThermalManager.THERMAL_STATE_ALERT] = 50;
        sBrightnessValuesPercentage[ThermalManager.THERMAL_STATE_CRITICAL] = 50;
    }

    public static void init(Context context, String path, ArrayList<Integer> values) {
        sContext = context;

        if (values == null) {
            setDefaultBrightnessValues();
        } else {
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
    }

    public static void throttleDevice(int tstate) {
        if (sPower == null || tstate < 0)
            return;

        int maxBrightness = (sBrightnessValuesPercentage[tstate] * sFullBrightness) / 100;

        try {
            sPower.setThermalBrightnessLimit(maxBrightness, true);
            // Notify user if we are limiting brightness.
            // Shown every time the brightness is throttled more.
            if (maxBrightness < sMaxAllowedBrightness) {
                new ThermalNotifier(sContext, sNotificationMask).triggerNotification();
            }
            sMaxAllowedBrightness = maxBrightness;
        } catch (RemoteException e) {
            Log.i(TAG, "remote exception for setThermalBrightnessLimit()");
        }
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
