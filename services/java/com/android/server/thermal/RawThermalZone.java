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

import android.os.UEventObserver;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * The RawThermalZone class extends the ThermalZone class, with a default
 * implementation of the isZoneStateChanged() method. This computes the
 * zone state by first computing max of all sensor temperature (in polling mode)
 * and comparing this temperature against zone thresholds. For uevent based
 * monitoring only the temperature of first sensor is used to compute zone state.
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
        int curTemp = ThermalManager.INVALID_TEMP, maxCurTemp = ThermalManager.INVALID_TEMP;
        if (isUEventSupported()) {
            ArrayList<ThermalSensor> list = getThermalSensorList();
            if (list != null) {
                // for uevent based monitoring only first sensor used
                ThermalSensor s = list.get(0);
                if (s != null) {
                    maxCurTemp = s.getCurrTemp();
                }
            }
        } else {
            //zone temp is max of all sensor temp
            for (ThermalSensor ts : getThermalSensorList()) {
                if (ts != null && ts.getSensorActiveStatus()) {
                    curTemp = ts.getCurrTemp();
                    if (curTemp > maxCurTemp) {
                        maxCurTemp = curTemp;
                    }
                }
            }
        }

        if (maxCurTemp != ThermalManager.INVALID_TEMP) {
            setZoneTemp(maxCurTemp);
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

    /**
     * Function that calculates the state of the Thermal Zone after reading
     * temperatures of all sensors in the zone. This is an overloaded function
     * used when a zone supports UEvent notifications from kernel. When a
     * sensor sends an UEvent, it also sends its current temperature as a
     * parameter of the UEvent.
     */
    public boolean isZoneStateChanged(ThermalSensor s, int temp) {
        if (s == null) return false;
        s.setCurrTemp(temp);
        return updateZoneParams();
    }

    public void registerUevent() {
        ArrayList<ThermalSensor> sensorList = getThermalSensorList();
        String devPath;
        int indx;
        if (sensorList == null) return;
        if (sensorList.size() > 1) {
            Log.i(TAG, "for zone:" + getZoneName() + " in uevent mode only first sensor used!");
        }
        ThermalSensor sensor = sensorList.get(0);
        if (sensor == null) return;
        String path = sensor.getUEventDevPath();
        if (path.equalsIgnoreCase("invalid")) return;
        if (path.equals("auto")) {
            // build the sensor UEvent listener path
            indx = sensor.getSensorSysfsIndx();
            if (indx == -1) {
                Log.i(TAG, "Cannot build UEvent path for sensor:" + sensor.getSensorName());
                return;
            } else {
                devPath = ThermalManager.sUEventDevPath + indx;
            }
        } else {
            devPath = path;
        }
        // first time update of sensor temp and zone temp
        sensor.updateSensorTemp();
        if (updateZoneParams()) {
            // first intent after initialization
            sendThermalEvent();
        }
        mUEventObserver.startObserving(devPath);
        programThresholds(sensor);
    }

    private UEventObserver mUEventObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            String sensorName;
            int sensorTemp, errorVal, eventType = -1;
            ThermalZone zone;
            ArrayList<ThermalSensor> sensorList = getThermalSensorList();
            if (sensorList ==  null) return;
            // Name of the sensor and current temperature are mandatory parameters of an UEvent
            sensorName = event.get("NAME");
            sensorTemp = Integer.parseInt(event.get("TEMP"));

            // eventType is an optional parameter. so, check for null case
            if (event.get("EVENT") != null)
               eventType = Integer.parseInt(event.get("EVENT"));

            if (sensorName != null) {
                Log.i(TAG, "UEvent received for sensor:" + sensorName + " temp:" + sensorTemp);
                // check the name against the first sensor
                ThermalSensor sensor = sensorList.get(0);
                if (sensor != null && sensor.getSensorName() != null
                        && sensor.getSensorName().equalsIgnoreCase(sensorName)) {
                    // Adjust the sensor temperature based on the 'error correction' temperature.
                    // For 'LOW' event, debounce interval will take care of this.
                    errorVal = sensor.getErrorCorrectionTemp();
                    if (eventType == ThermalManager.THERMAL_HIGH_EVENT)
                       sensorTemp += errorVal;

                    // call isZoneStateChanged for zones mapped to this sensor
                    if (isZoneStateChanged(sensor, sensorTemp)) {
                        sendThermalEvent();
                        // reprogram threshold
                        programThresholds(sensor);
                    }
                }
            }
        }
    };

    private void sendThermalEvent() {
        ThermalEvent thermalEvent = new ThermalEvent(getZoneId(),
                getEventType(), getZoneState(), getZoneTemp(), getZoneName());
        try {
            ThermalManager.sEventQueue.put(thermalEvent);
        } catch (InterruptedException ex) {
                Log.i(TAG, "caught InterruptedException in posting to event queue");
        }
     }

   private void programThresholds(ThermalSensor s) {
        if (s == null) return;
        int zoneState = getZoneState();
        if (zoneState == ThermalManager.THERMAL_STATE_OFF) return;
        int lowerTripPoint = ThermalUtils.getLowerThresholdTemp(zoneState,getZoneTempThreshold());
        int upperTripPoint = ThermalUtils.getUpperThresholdTemp(zoneState, getZoneTempThreshold());
        // write to sysfs
        if (lowerTripPoint != ThermalManager.INVALID_TEMP &&
                upperTripPoint != ThermalManager.INVALID_TEMP) {
            ThermalUtils.writeSysfs(s.getSensorLowTempPath(), lowerTripPoint);
            ThermalUtils.writeSysfs(s.getSensorHighTempPath(), upperTripPoint);
        }
    }

}
