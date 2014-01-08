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

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

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
    protected ArrayList <ThermalSensor> mThermalSensors = null;
    protected int mZoneTemp;             /* Temperature of the Thermal Zone */

    private int mDebounceInterval;    /* Debounce value to avoid thrashing of throttling actions */
    private Integer mPollDelay[];     /* Delay between sucessive polls in milli seconds */
    private boolean mSupportsUEvent;  /* Determines if Sensor supports Uevents */
    private String mSensorLogic;      /* Logic to be used to determine thermal state of zone */
    private boolean mIsZoneActive = false;

    public void printAttrs() {
        Log.i(TAG, "mZoneID:" + Integer.toString(mZoneID));
        Log.i(TAG, "mDBInterval: " + Integer.toString(mDebounceInterval));
        Log.i(TAG, "mZoneName:" + mZoneName);
        Log.i(TAG, "mSupportsUEvent:" + Boolean.toString(mSupportsUEvent));
        Log.i(TAG, "mSensorLogic:" + mSensorLogic);
        Log.i(TAG, "mPollDelay[]:" + Arrays.toString(mPollDelay));

        for (ThermalSensor ts : mThermalSensors) {
            ts.printAttrs();
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

    public void setSensorList(ArrayList<ThermalSensor> ThermalSensors) {
        mThermalSensors = ThermalSensors;
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

    public void setSensorLogic(String type) {
        mSensorLogic = type;
    }

    public String getSensorLogic() {
        return mSensorLogic;
    }

    public void setDBInterval(int interval) {
        mDebounceInterval = interval;
    }

    public int getDBInterval() {
        return mDebounceInterval;
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

    public boolean getZoneActiveStatus() {
        return mIsZoneActive;
    }

    public void computeZoneActiveStatus() {
        if (mThermalSensors == null) {
            mIsZoneActive = false;
            return;
        }

        for (ThermalSensor ts : mThermalSensors) {
            if (ts != null && ts.getSensorActiveStatus()) {
                mIsZoneActive = true;
                break;
            }
        }
    }

    public boolean isZoneStateChanged() {
        return false;
    }

    public boolean isZoneStateChanged(ThermalSensor ts, int temp) {
        return false;
    }

    public void startMonitoring() {
    }

    public void unregisterReceiver() {
    }
}
