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

import java.util.ArrayList;
import java.util.Arrays;

/**
 * The RawThermalZone class extends the ThermalZone class, with a default
 * implementation of the isZoneStateChanged() method. This computes the
 * zone state by selecting the maximum state of the all sensors inside
 * this zone.
 *
 * @hide
 */
public class RawThermalZone extends ThermalZone {

    private static final String TAG = "RawThermalZone";

    public RawThermalZone() {
        super();
    }

    public void startMonitoring() {
        new ThermalZoneMonitor(this);
    }

    private boolean updateZoneTemp() {
        int maxSensorState = ThermalManager.THERMAL_STATE_OFF - 1;
        int curSensorState = ThermalManager.THERMAL_STATE_OFF - 1;
        int curTemp = ThermalManager.INVALID_TEMP;

        for (ThermalSensor ts : getThermalSensorList()) {
            if (ts.getSensorActiveStatus()) {
                curSensorState = ts.getSensorThermalState();
                if (curSensorState > maxSensorState) {
                    maxSensorState = curSensorState;
                    curTemp = ts.getCurrTemp();
                }
            }
        }

        if (curTemp != ThermalManager.INVALID_TEMP) {
            setZoneTemp(curTemp);
            return true;
        }

        return false;
    }

    private int calcZoneState() {
        int maxSensorState = ThermalManager.THERMAL_STATE_OFF - 1;
        int curSensorState = ThermalManager.THERMAL_STATE_OFF - 1;
        int curTemp = ThermalManager.INVALID_TEMP;

        for (ThermalSensor ts : getThermalSensorList()) {
            if (ts.getSensorActiveStatus()) {
                curSensorState = ts.getSensorThermalState();
                if (curSensorState > maxSensorState) {
                    maxSensorState = curSensorState;
                    curTemp = ts.getCurrTemp();
                }
            }
        }

        return maxSensorState;
    }

    private boolean updateZoneParams() {
        int state;
        int prevZoneState = getZoneState();

        if (!updateZoneTemp()) {
            return false;
        }

        state = calcZoneState();
        if (state == prevZoneState) {
            return false;
        }

        setZoneState(state);
        setEventType(state > prevZoneState ?
                ThermalManager.THERMAL_HIGH_EVENT :
                ThermalManager.THERMAL_LOW_EVENT);
        return true;
    }

    /**
     * Function that calculates the state of the Thermal Zone after reading
     * temperatures of all sensors in the zone. This function is used when a
     * zone operates in polling mode.
     */
    public boolean isZoneStateChanged() {
        // updateSensorAttributes() updates a sensor's state.
        // Debounce Interval is passed as input, so that for
        // LOWEVENT (Hot to Cold transition), if decrease in sensor
        // temperature is less than (threshold - debounce interval), sensor
        // state change is ignored and original state is maintained.
        for (ThermalSensor ts : getThermalSensorList()) {
            if (ts.getSensorActiveStatus()) {
                ts.updateSensorAttributes(getDBInterval());
            }
        }
        return updateZoneParams();
    }

    /**
     * Function that calculates the state of the Thermal Zone after reading
     * temperatures of all sensors in the zone. This is an overloaded function
     * used when a zone supports UEvent notifications from kernel. When a
     * sensor sends an UEvent, it also sends its current temperature as a
     * parameter of the UEvent.
     */
    public boolean isZoneStateChanged(ThermalSensor s, int temp) {
        // Update sensor state, and record the max sensor state and
        // max sensor temp. This overloaded fucntion updateSensorAttributes()
        // doesnot do a sysfs read, but only updates temperature.
        s.updateSensorAttributes(getDBInterval(), temp);
        return updateZoneParams();
    }
}
