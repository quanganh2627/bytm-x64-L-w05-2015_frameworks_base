/*
 * Copyright 2014 Intel Corporation All Rights Reserved.
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

import android.util.Log;

/**
 * The ThermalZoneMonitor class runs a thread for each zone
 * with which it is instantiated.
 *
 * @hide
 */
public class ThermalZoneMonitor implements Runnable {
    private static final String TAG = "ThermalZoneMonitor";
    private Thread t;
    private ThermalZone zone;

    public ThermalZoneMonitor(ThermalZone tz) {
        zone = tz;
        String threadName = "ThermalZone" + zone.getZoneId();
        t = new Thread(this, threadName);
        t.start();
    }

    public void run() {
        try {
            while (true) {
                if (zone.isZoneStateChanged()) {
                    ThermalUtils.addThermalEvent(zone);
                }
                Thread.sleep(zone.getPollDelay(zone.getZoneState()));
            }
        } catch (InterruptedException iex) {
            Log.i(TAG, "caught InterruptedException in run()");
        }
    }
}
