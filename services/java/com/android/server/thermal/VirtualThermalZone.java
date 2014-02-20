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

    public void startMonitoring() {
        new ThermalZoneMonitor(this);
    }

    // override fucntion
    public boolean updateZoneTemp() {
        int curZoneTemp = ThermalManager.INVALID_TEMP;
        int rawSensorTemp, sensorTemp;
        int weightedTemp;
        boolean flag = false;

        if (isUEventSupported()) {
            ArrayList<ThermalSensor> list = getThermalSensorList();
            if (list != null) {
                // for uevent based monitoring only first sensor used
                ThermalSensor s = list.get(0);
                if (s != null) {
                    curZoneTemp = getWeightedTemp(s);
                }
            }
        } else {
            for (ThermalSensor ts : getThermalSensorList()) {
                if (ts != null && ts.getSensorActiveStatus()) {
                    if (flag == false) {
                        // one time initialization of zone temp
                        curZoneTemp = 0;
                        flag = true;
                    }
                    weightedTemp = getWeightedTemp(ts);
                    if (weightedTemp != ThermalManager.INVALID_TEMP) {
                        curZoneTemp += weightedTemp;
                    }
                }
            }
        }

        if (curZoneTemp != ThermalManager.INVALID_TEMP) {
            curZoneTemp += getOffset();
            setZoneTemp(curZoneTemp);
            if (getMovingAverageFlag() && !isUEventSupported()) {
                // only for polling mode apply moving average on predicted zone temp
                setZoneTemp(movingAverageTemp());
            }
            return true;
        }

        return false;
    }

    private int getWeightedTemp(ThermalSensor ts) {
        int curZoneTemp = 0;
        int rawSensorTemp = ts.getCurrTemp();
        Integer weights[], order[];

        ThermalSensorAttrib sa = mThermalSensorsAttribMap.get(ts.getSensorName());
        if (sa == null) return ThermalManager.INVALID_TEMP;
        weights = sa.getWeights();
        order = sa.getOrder();
        if (weights == null && order == null) return rawSensorTemp;
        if (weights != null) {
            if (order == null) {
                // only first weight will be considered
                return (weights[0] * rawSensorTemp);
            } else if (order != null && weights.length == order.length) {
                // if order array is provided in xml,
                // it should be of same size as weights array
                int sensorTemp = 0;
                for (int i = 0; i < weights.length; i++) {
                    sensorTemp += weights[i] * (int) Math.pow(rawSensorTemp, order[i]);
                }
                return sensorTemp;
            }
        }
        // for every other mismatch return the raw sensor temp
        return rawSensorTemp;
    }
}
