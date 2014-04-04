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

import android.os.UEventObserver;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;

/**
 * The ThermalZone class contains attributes of a Thermal zone. A Thermal zone
 * can have one or more sensors associated with it. Whenever the temperature of a
 * thermal zone crosses the thresholds configured, actions are taken.
 */
public class ThermalZone {

    private static final String TAG = "ThermalZone";

    protected int mZoneID;               /* ID of the Thermal zone */
    protected int mCurrThermalState;     /* Current thermal state of the zone */
    protected int mCurrEventType;        /* specifies thermal event type, HIGH or LOW */
    protected String mZoneName;          /* Name of the Thermal zone */
    /* List of sensors under this thermal zone */
    protected ArrayList<ThermalSensor> mThermalSensors = null;
    // sensor name - sensorAttrib object hash to improve lookup performace
    // during runtime thermal monitoring like re-programming sensor thresholds
    // calculating weighted zone temp.
    protected Hashtable<String, ThermalSensorAttrib> mThermalSensorsAttribMap = null;
    protected int mZoneTemp;             /* Temperature of the Thermal Zone */
    protected boolean mSupportsEmulTemp = true;
    private int mDebounceInterval;    /* Debounce value to avoid thrashing of throttling actions */
    private Integer mPollDelay[];     /* Delay between sucessive polls in milli seconds */
    protected boolean mSupportsUEvent;  /* Determines if Sensor supports Uevents */
    private String mZoneLogic;      /* Logic to be used to determine thermal state of zone */
    private boolean mIsZoneActive = false;
    private int mOffset = 0;
    protected Integer mZoneTempThresholds[];  /* Array containing temperature thresholds */

    /* MovingAverage related declarations */
    private int mRecordedValuesHead = -1; /* Index pointing to the head of past values of sensor */
    private int mRecordedValues[];        /* Recorded values of sensor */
    private int mNumberOfInstances[];     /* Number of recorded instances to be considered */
    private ArrayList<Integer> mWindowList = null;
    private boolean mIsMovingAverage = false; /* By default false */


    public boolean getMovingAverageFlag() {
        return mIsMovingAverage;
    }

    public void setMovingAvgWindow(ArrayList<Integer> windowList) {
        int maxValue = Integer.MIN_VALUE; // -2^31

        if (windowList == null || mPollDelay == null) {
            Log.i(TAG, "setMovingAvgWindow input is null");
            mIsMovingAverage = false;
            return;
        }
        mNumberOfInstances = new int[windowList.size()];
        if (mNumberOfInstances == null) {
            Log.i(TAG, "failed to create poll windowlist");
            mIsMovingAverage = false;
            return;
        }
        mIsMovingAverage = true;
        for (int i = 0; i < windowList.size(); i++) {
            if (mPollDelay[i] == 0) {
                mIsMovingAverage = false;
                Log.i(TAG, "Polling delay is zero, WMA disabled\n");
                return;
            }
            mNumberOfInstances[i] = windowList.get(i) / mPollDelay[i];
            if (mNumberOfInstances[i] <= 0) {
                mIsMovingAverage = false;
                Log.i(TAG, "Polling delay greater than moving average window, WMA disabled\n");
                return;
            }
            maxValue = Math.max(mNumberOfInstances[i], maxValue);
        }
        mRecordedValues = new int[maxValue];
    }

    public int movingAverageTemp() {
        int index, calIndex;
        int predictedTemp = 0;

        mRecordedValuesHead = (mRecordedValuesHead + 1) % mRecordedValues.length;
        mRecordedValues[mRecordedValuesHead] = mZoneTemp;

        // Sensor State starts with -1, InstancesList starts with 0
        for (index = 0; index < mNumberOfInstances[mCurrThermalState + 1]; index++) {
            calIndex = mRecordedValuesHead - index;
            if (calIndex < 0) {
                calIndex = mRecordedValues.length + calIndex;
            }
            predictedTemp += mRecordedValues[calIndex];
        }
        return predictedTemp / index;
    }

    public void printAttrs() {
        Log.i(TAG, "mZoneID:" + Integer.toString(mZoneID));
        Log.i(TAG, "mDBInterval: " + Integer.toString(mDebounceInterval));
        Log.i(TAG, "mZoneName:" + mZoneName);
        Log.i(TAG, "mSupportsUEvent:" + Boolean.toString(mSupportsUEvent));
        Log.i(TAG, "mZoneLogic:" + mZoneLogic);
        Log.i(TAG, "mOffset:" + mOffset);
        Log.i(TAG, "mPollDelay[]:" + Arrays.toString(mPollDelay));
        Log.i(TAG, "mZoneTempThresholds[]: " + Arrays.toString(mZoneTempThresholds));
        Log.i(TAG, "mNumberOfInstances[]: " + Arrays.toString(mNumberOfInstances));
        Log.i(TAG, "mEmulTempFlag:" + mSupportsEmulTemp);
        printSensors();
        printSensorAttribList();
    }

    private void printSensors() {
        if (mThermalSensors == null) return;
        StringBuilder s =  new StringBuilder();
        for (ThermalSensor ts : mThermalSensors) {
            if (ts != null) {
                s.append(ts.getSensorName());
                s.append(",");
            }
        }
        Log.i(TAG, "zoneID: " + mZoneID + " sensors mapped:" + s.toString());
    }

    private void printSensorAttribList() {
        if (mThermalSensorsAttribMap == null) return;
        Iterator it = (Iterator) mThermalSensorsAttribMap.keySet().iterator();
        if (it == null) return;
        ThermalSensorAttrib sensorAttrib = null;
        while (it.hasNext()) {
            sensorAttrib = mThermalSensorsAttribMap.get((String) it.next());
            if (sensorAttrib != null) sensorAttrib.printAttrs();
        }
    }

    public ThermalZone() {
        mCurrThermalState = ThermalManager.THERMAL_STATE_OFF;
        mZoneTemp = ThermalManager.INVALID_TEMP;
    }

    public static String getStateAsString(int index) {
        if (index < -1 || index > 3)
            return "Invalid";
        return ThermalManager.STATE_NAMES[index + 1];
    }

    public static String getEventTypeAsString(int type) {
        return type == 0 ? "LOW" : "HIGH";
    }

    public void setSensorList(ArrayList<ThermalSensorAttrib> sensorAtribList) {
        if (sensorAtribList == null || ThermalManager.sSensorMap == null) return;
        for (ThermalSensorAttrib sa : sensorAtribList) {
            // since each object of sensor attrib list is already validated during
            // parsing it is gauranteed that 'sa != null' and a valid sensor object 's'
            // will be returned. Hence skipping null check..
            if (mThermalSensors == null) {
                // first time allocation
                mThermalSensors = new ArrayList<ThermalSensor>();
                if (mThermalSensors == null) {
                    // allocation failure. return
                    return;
                }
            }
            if (mThermalSensorsAttribMap == null) {
                // first time allocation
                mThermalSensorsAttribMap = new Hashtable<String, ThermalSensorAttrib>();
                if (mThermalSensorsAttribMap == null) return;
            }
            mThermalSensors.add(ThermalManager.getSensor(sa.getSensorName()));
            mThermalSensorsAttribMap.put(sa.getSensorName(), sa);
        }
    }

    public ArrayList<ThermalSensor> getThermalSensorList() {
        return mThermalSensors;
    }

    public int getZoneState() {
        return mCurrThermalState;
    }

    public void setZoneState(int state) {
        mCurrThermalState = state;
    }

    public int getEventType() {
        return mCurrEventType;
    }

    public void setEventType(int type) {
        mCurrEventType = type;
    }

    public void setZoneTemp(int temp) {
        mZoneTemp = temp;
    }

    public int getZoneTemp() {
        return mZoneTemp;
    }

    public void setZoneId(int id) {
        mZoneID = id;
    }

    public int getZoneId() {
        return mZoneID;
    }

    public void setZoneName(String name) {
        mZoneName = name;
    }

    public String getZoneName() {
        return mZoneName;
    }

    public void setSupportsUEvent(int flag) {
        mSupportsUEvent = (flag == 1);
    }

    public boolean isUEventSupported() {
        return mSupportsUEvent;
    }

    public void setZoneLogic(String type) {
        mZoneLogic = type;
    }

    public String getZoneLogic() {
        return mZoneLogic;
    }

    public void setDBInterval(int interval) {
        mDebounceInterval = interval;
    }

    public int getDBInterval() {
        return mDebounceInterval;
    }

    public int getOffset() {
        return mOffset;
    }

    public void setOffset(int offset) {
        mOffset  = offset;
    }

    public void setPollDelay(ArrayList<Integer> delayList) {
        if (delayList != null) {
            mPollDelay = new Integer[delayList.size()];
            if (mPollDelay != null) {
                mPollDelay = delayList.toArray(mPollDelay);
            }
        }
    }

    public Integer[] getPollDelay() {
        return mPollDelay;
    }

    /**
     * In polldelay array, index of TOFF = 0, Normal = 1, Warning = 2, Alert =
     * 3, Critical = 4. Whereas a ThermalZone states are enumerated as TOFF =
     * -1, Normal = 0, Warning = 1, Alert = 2, Critical = 3. Hence we add 1
     * while querying poll delay
     */
    public int getPollDelay(int index) {
        index++;

        // If poll delay is requested for an invalid state, return the delay
        // corresponding to normal state
        if (index < 0 || index >= mPollDelay.length)
            index = ThermalManager.THERMAL_STATE_NORMAL + 1;

        return mPollDelay[index];
    }

    public void setZoneTempThreshold(ArrayList<Integer> thresholdList) {
        if (thresholdList != null ) {
            mZoneTempThresholds = new Integer[thresholdList.size()];
            if (mZoneTempThresholds != null) {
                mZoneTempThresholds = thresholdList.toArray(mZoneTempThresholds);
            }
        }
    }

    public int getZoneTempThreshold(int index) {
        if (index < 0 || index >= mZoneTempThresholds.length)
            return -1;
        return mZoneTempThresholds[index];
    }

    public Integer[] getZoneTempThreshold() {
        return mZoneTempThresholds;
    }

    public boolean getZoneActiveStatus() {
        return mIsZoneActive;
    }

    public void computeZoneActiveStatus() {
        if (mThermalSensors == null) {
            mIsZoneActive = false;
            return;
        }

        if (mSupportsUEvent) {
            // if uevent just check the first sensor
            ThermalSensor s = mThermalSensors.get(0);
            if (s != null && s.getSensorActiveStatus()) {
                mIsZoneActive = true;
                return;
            }
        } else {
            for (ThermalSensor ts : mThermalSensors) {
                if (ts != null && ts.getSensorActiveStatus()) {
                    mIsZoneActive = true;
                    return;
                }
            }
        }
    }

    public void setEmulTempFlag(int flag) {
        mSupportsEmulTemp = (flag == 1);
    }

    public boolean getEmulTempFlag() {
        return mSupportsEmulTemp;
    }

    // override in Specific zone class which inherit ThermalZone
    public void startMonitoring() {
    }

    // override in ModemZone to unregister Modem specific intents
    // override in VirtualThermalZone to stop UEvent observers
    public void unregisterReceiver() {

    }

    // override in VirtualThermalZone class
    public void startEmulTempObserver() {
    }

    // override in VirtualThermalZone class
    public void calibrateThresholds() {
    }

    /**
     * Function that calculates the state of the Thermal Zone after reading
     * temperatures of all sensors in the zone. This function is used when a
     * zone operates in polling mode.
     */
    public boolean isZoneStateChanged() {
        for (ThermalSensor ts : mThermalSensors) {
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
        setZoneTemp(temp);
        return updateZoneParams();
    }

    /**
     * Method to update Zone Temperature and Zone Thermal State
     */
    public boolean updateZoneParams() {
        int newZoneState;
        int prevZoneState = mCurrThermalState;

        if (!updateZoneTemp()) {
            return false;
        }

        newZoneState = ThermalUtils.calculateThermalState(mZoneTemp, mZoneTempThresholds);
        if (newZoneState == prevZoneState) {
            return false;
        }

        if (newZoneState == ThermalManager.THERMAL_STATE_OFF) {
            setZoneState(newZoneState);
            return true;
        }

        int threshold = ThermalUtils.getLowerThresholdTemp(prevZoneState, mZoneTempThresholds);
        // For Interrupt based zones, HW (should) takes care of the debounce.
        if (!isUEventSupported()) {
            if (newZoneState < prevZoneState && getZoneTemp() > (threshold - getDBInterval())) {
                Log.i(TAG, " THERMAL_LOW_EVENT for zone:" + getZoneName()
                        + " rejected due to debounce interval");
                return false;
            }
        }

        setZoneState(newZoneState);
        setEventType(newZoneState > prevZoneState
                ? ThermalManager.THERMAL_HIGH_EVENT
                : ThermalManager.THERMAL_LOW_EVENT);
        return true;
    }

    public boolean updateZoneTemp() {
        return false;
    }

    public void registerUevent() {
        String devPath;
        int indx;
        if (mThermalSensors == null) return;
        if (mThermalSensors.size() > 1) {
            Log.i(TAG, "for zone:" + getZoneName() + " in uevent mode only first sensor used!");
        }
        ThermalSensor sensor = mThermalSensors.get(0);
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
        setZoneTemp(sensor.getCurrTemp());
        if (updateZoneParams()) {
            // first intent after initialization
            sendThermalEvent();
        }
        mUEventObserver.startObserving(devPath);
        programThresholds(sensor);
    }

    public UEventObserver mUEventObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            String sensorName;
            int sensorTemp, errorVal, eventType = -1;
            ThermalZone zone;
            if (mThermalSensors ==  null) return;
            // Name of the sensor and current temperature are mandatory parameters of an UEvent
            sensorName = event.get("NAME");
            sensorTemp = Integer.parseInt(event.get("TEMP"));

            // eventType is an optional parameter. so, check for null case
            if (event.get("EVENT") != null)
                eventType = Integer.parseInt(event.get("EVENT"));

            if (sensorName != null) {
                Log.i(TAG, "UEvent received for sensor:" + sensorName + " temp:" + sensorTemp);
                // check the name against the first sensor
                ThermalSensor sensor = mThermalSensors.get(0);
                if (sensor != null && sensor.getSensorName() != null
                        && sensor.getSensorName().equalsIgnoreCase(sensorName)) {
                    // Adjust the sensor temperature based on the 'error correction' temperature.
                    // For 'LOW' event, debounce interval will take care of this.
                    errorVal = sensor.getErrorCorrectionTemp();
                    if (eventType == ThermalManager.THERMAL_HIGH_EVENT)
                        sensorTemp += errorVal;

                    if (isZoneStateChanged(sensor, sensorTemp)) {
                        sendThermalEvent();
                        // reprogram threshold
                        programThresholds(sensor);
                    }
                }
            }
        }
    };

    public void programThresholds(ThermalSensor s) {
        if (s == null) return;
        int zoneState = getZoneState();
        if (zoneState == ThermalManager.THERMAL_STATE_OFF) return;
        int lowerTripPoint = ThermalUtils.getLowerThresholdTemp(zoneState, getZoneTempThreshold());
        int upperTripPoint = ThermalUtils.getUpperThresholdTemp(zoneState, getZoneTempThreshold());
        if (lowerTripPoint != ThermalManager.INVALID_TEMP
                && upperTripPoint != ThermalManager.INVALID_TEMP) {
            if (ThermalUtils.writeSysfs(s.getSensorLowTempPath(), lowerTripPoint) == -1) {
                Log.i(TAG, "error while programming lower trip point:" + lowerTripPoint
                        + "for sensor:" + s.getSensorName());
            }
            if (ThermalUtils.writeSysfs(s.getSensorHighTempPath(), upperTripPoint) == -1) {
                Log.i(TAG, "error while programming upper trip point:" + upperTripPoint
                        + "for sensor:" + s.getSensorName());
            }
        }
    }

    public void sendThermalEvent() {
        ThermalEvent thermalEvent = new ThermalEvent(mZoneID,
                mCurrEventType, mCurrThermalState, mZoneTemp, mZoneName);
        try {
            ThermalManager.sEventQueue.put(thermalEvent);
        } catch (InterruptedException ex) {
            Log.i(TAG, "caught InterruptedException in posting to event queue");
        }
    }
}
