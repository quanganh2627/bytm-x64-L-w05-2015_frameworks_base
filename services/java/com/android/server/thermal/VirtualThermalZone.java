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

import java.lang.Math;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * The VirtualThermalZone class extends the ThermalZone class, with a default
 * implementation of the isZoneStateChanged() method. This computes the
 * zone state by computing the equation, which can be linear / higher order implementation
 * @hide
 */
public class VirtualThermalZone extends ThermalZone {

    private static final String TAG = "VirtualThermalZone";

    public VirtualThermalZone() {
        super();
    }

    // override thermal zone setSupportsUEvent
    public void setSupportsUEvent(int flag) {
        Log.i(TAG, "Virtual Zone:" + getZoneName() + " supports only poling mode!!");
        mSupportsUEvent = false;
    }

    public void startMonitoring() {
        new ThermalZoneMonitor(this);
    }

    private boolean updateZoneTemp() {
        int curZoneTemp = ThermalManager.INVALID_TEMP;
        int rawSensorTemp, sensorTemp;
        Integer weights[], order[];
        boolean flag = false;
        for (ThermalSensor ts : getThermalSensorList()) {
            if (ts != null && ts.getSensorActiveStatus()) {
                rawSensorTemp = ts.getCurrTemp();
                weights = ts.getWeights();
                order = ts.getOrder();
                if (flag == false) {
                    // one time initialization of zone temp
                    curZoneTemp = 0;
                    flag = true;
                }
                if (weights != null && order == null) {
                    // only first weight will be considered
                    curZoneTemp += weights[0] * rawSensorTemp;
                } else if (weights != null && order != null && weights.length == order.length) {
                    // if order array is provided in xml,
                    // it should be of same size as weights array
                    sensorTemp = 0;
                    for (int i = 0; i < weights.length; i++) {
                        sensorTemp += weights[i] * (int) Math.pow(rawSensorTemp, order[i]);
                    }
                    curZoneTemp += sensorTemp;
                } else {
                    curZoneTemp += rawSensorTemp;
                }
            }
        }

        if (curZoneTemp != ThermalManager.INVALID_TEMP) {
            curZoneTemp += getOffset();
            setZoneTemp(curZoneTemp);
            return true;
        }

        return false;
    }

    private boolean updateZoneParams() {
        int newZoneState;
        int prevZoneState = getZoneState();

        if (!updateZoneTemp()) {
            return false;
        }
        newZoneState = ThermalUtils.calculateThermalState(getZoneTemp(), getZoneTempThreshold());
        if (newZoneState == prevZoneState) {
            return false;
        }

        if (newZoneState == ThermalManager.THERMAL_STATE_OFF) {
            setZoneState(newZoneState);
            return true;
        }

        int threshold = ThermalUtils.getLowerThresholdTemp(prevZoneState, getZoneTempThreshold());
        if (newZoneState < prevZoneState && getZoneTemp() > (threshold - getDBInterval())) {
            Log.i(TAG, " THERMAL_LOW_EVENT for zone:" + getZoneName() +
                    " rejected due to debounce interval");
            return false;
        }

        setZoneState(newZoneState);
        setEventType(newZoneState > prevZoneState ?
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
        for (ThermalSensor ts : getThermalSensorList()) {
            if (ts.getSensorActiveStatus()) {
                ts.updateSensorTemp();
            }
        }
        return updateZoneParams();
    }
}
