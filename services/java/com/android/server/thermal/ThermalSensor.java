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

import com.android.server.thermal.ThermalManager;
import com.intel.internal.telephony.OemTelephony.OemTelephonyConstants;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;

/**
 * The ThermalSensor class describes the attributes of a Thermal Sensor. This
 * class implements methods that retrieve temperature sensor information from
 * the kernel through the native interface.
 *
 * @hide
 */
public class ThermalSensor {

    private static final String TAG = "ThermalSensor";
    private String mSensorPath;     /* sys path to read temp from */
    private String mSensorName;     /* name of the sensor */
    private String mInputTempPath;  /* sys path to read the current temp */
    private String mHighTempPath;   /* sys path to set the intermediate upper threshold */
    private String mLowTempPath;    /* sys path to set the intermediate lower threshold */
    private String mUEventDevPath;  /* sys path for uevent listener */
    private int mErrorCorrectionTemp; /* Temperature difference in mC */
    private int mSensorID;
    private int mSensorState;       /* Thermal state of the sensor */
    private int mCurrTemp;          /* Holds the latest temperature of the sensor */
    private int mSensorSysfsIndx; /* Index of this sensor in the sysfs */
    private int upperLimit = 90;    /* intermediate lower thershold */
    private int lowerLimit = 0;     /* intermediate upper threshold */
    /** MovingAverage: Index pointing to the head of past values of sensor */
    private int mRecordedValuesListHead = -1;
    private int mTempThresholds[];  /* array contain the temperature thresholds */
    private int mMovingAvgWindowList[]; /* Time window for predicting temperature */
    private int mRecordedValuesList[]; /* MovingAverage: Past values of sensor */
    /** MovingAverage: Number of past instances of sensor values */
    private int mNumberOfInstancesList[];
    private boolean mIsSensorActive = false;
    private boolean mIsMovingAverage = false;

    public boolean getSensorActiveStatus() {
        return mIsSensorActive;
    }

    public void printAttrs() {
        Log.i(TAG, "mSensorID: " + Integer.toString(mSensorID));
        Log.i(TAG, "mSensorPath: " + mSensorPath);
        Log.i(TAG, "mSensorName: " + mSensorName);
        Log.i(TAG, "mInputTempPath: " + mInputTempPath);
        Log.i(TAG, "mHighTempPath: " + mHighTempPath);
        Log.i(TAG, "mLowTempPath: " + mLowTempPath);
        Log.i(TAG, "mUEventDevPath: " + mUEventDevPath);
        Log.i(TAG, "mErrorCorrection: " + mErrorCorrectionTemp);
        Log.i(TAG, "mTempThresholds");
        for (int val : mTempThresholds)
            Log.i(TAG, Integer.toString(val));
        if (mMovingAvgWindowList != null)
            for (int val : mMovingAvgWindowList)
                Log.i(TAG, Integer.toString(val));
        if (mNumberOfInstancesList != null)
            for (int val : mNumberOfInstancesList)
                Log.i(TAG, Integer.toString(val));
    }

    private void setSensorSysfsPath() {
        int indx = ThermalManager.getThermalZoneIndex(mSensorName);
        if (indx == -1) {
            indx = ThermalManager. getThermalZoneIndexContains("battery");
            if (indx != -1) {
                mSensorPath = ThermalManager.sSysfsSensorBasePath + indx + "/";
            }
        } else if (indx != -1) {
           mSensorPath = ThermalManager.sSysfsSensorBasePath + indx + "/";
        }

        mSensorSysfsIndx = indx;
    }

    public String getSensorSysfsPath() {
        return mSensorPath;
    }

    public ThermalSensor() {
        mSensorState = ThermalManager.THERMAL_STATE_OFF;
        mCurrTemp = ThermalManager.INVALID_TEMP;
        /**
         * by default set uevent path to invalid. if uevent flag is set for a
         * sensor, but no uevent path tag is added in sensor, then
         * mUEventDevPath will be invalid. ueventobserver in ThermalManager will
         * ignore the sensor
         */
        mUEventDevPath = "invalid";

        // Set default value of 'correction temperature' to 0
        mErrorCorrectionTemp = 0;
    }

    public int getSensorID() {
        return mSensorID;
    }

    public void setSensorID(int id) {
        mSensorID = id;
    }

    public int getSensorSysfsIndx() {
        return mSensorSysfsIndx;
    }

    public String getSensorPath() {
        return mSensorPath;
    }

    /**
     * This function sets the sensor path to the given String value. If the
     * String is "auto" it loops through the standard sysfs path, to obtain the
     * 'mSensorPath'. The standard sysfs path is /sys/class/
     * thermal/thermal_zoneX and the look up is based on 'mSensorName'.
     */
    public void setSensorPath(String path) {
        if (path.equalsIgnoreCase("auto")) {
            setSensorSysfsPath();
        } else {
            // if sensor path is "none", sensor temp is not read via any sysfs
            mSensorPath = path;
        }
    }

    private boolean isSensorSysfsValid(String path) {
        return ThermalManager.isFileExists(path);
    }

    public String getSensorName() {
        return mSensorName;
    }

    public void setSensorName(String name) {
        mSensorName = name;
    }

    public void setUEventDevPath(String devPath) {
        mUEventDevPath = devPath;
    }

    public String getUEventDevPath() {
        return mUEventDevPath;
    }

    public void setErrorCorrectionTemp(int temp) {
        mErrorCorrectionTemp = temp;
    }

    public int getErrorCorrectionTemp() {
        return mErrorCorrectionTemp;
    }

    public void setThermalThresholds(ArrayList<Integer> thresholdList) {
        if (thresholdList == null ) {
            Log.i(TAG, "setThermalThresholds input is null");
            mTempThresholds = null;
            return;
        }
        mTempThresholds = new int[thresholdList.size()];
        if (mTempThresholds == null) {
            Log.i(TAG, "failed to create threshold list");
            return;
        }
        try {
            for (int i = 0; i < thresholdList.size(); i++ ) {
                mTempThresholds[i] = thresholdList.get(i);
            }
        } catch (IndexOutOfBoundsException e) {
            Log.i(TAG, "IndexOutOfBoundsException caught in setThermalThresholds()\n");
        }
    }

    public int getThermalThreshold(int index) {
        if (index < 0 || index >= mTempThresholds.length)
            return -1;
        return mTempThresholds[index];
    }

    public void setMovingAvgWindow(ArrayList<Integer> windowList, int[] delayList) {
        int maxValue = 0;
        if (windowList == null || delayList == null) {
            Log.i(TAG, "setMovingAvgWindow input is null");
            mMovingAvgWindowList = null;
            return;
        }
        mMovingAvgWindowList = new int[windowList.size()];
        mNumberOfInstancesList = new int[windowList.size()];
        if (mMovingAvgWindowList == null || mNumberOfInstancesList == null) {
            Log.i(TAG, "failed to create poll windowlist");
            return;
        }
        mIsMovingAverage = true;
        try {
            for (int i = 0; i < windowList.size(); i++) {
                mMovingAvgWindowList[i] = windowList.get(i);
                if (delayList[i] == 0) {
                    mIsMovingAverage = false;
                    Log.i(TAG, "Polling delay is zero, WMA disabled\n");
                    return;
                }
                mNumberOfInstancesList[i] = mMovingAvgWindowList[i] / delayList[i];
                if (mNumberOfInstancesList[i] <= 0) {
                    mIsMovingAverage = false;
                    Log.i(TAG, "Polling delay greater than moving average window, WMA disabled\n");
                    return;
                }
                if (mNumberOfInstancesList[i] > maxValue)
                    maxValue = mNumberOfInstancesList[i];
            }
            mRecordedValuesList = new int[maxValue];
        } catch (IndexOutOfBoundsException e) {
            Log.i(TAG, "IndexOutOfBoundsException caught in setMovingAvgWindow()\n");
        }
    }

    public void setInputTempPath(String name) {
        if (mSensorPath != null && mSensorPath.equalsIgnoreCase("none")) {
            // sensor path is none, it means sensor temperature reporting is
            // not sysfs based. So turn sensor active by default
            mIsSensorActive = true;
        } else {
            mInputTempPath = mSensorPath + name;
            /* if sensor path does not exist, deactivate sensor */
            if (!isSensorSysfsValid(mInputTempPath)) {
                mIsSensorActive = false;
                Log.i(TAG, "Sensor:" + mSensorName + " path:" + mInputTempPath
                        + " is invalid...deactivaing Sensor");
            } else {
                mIsSensorActive = true;
            }
        }
    }

    public String getSensorInputTempPath() {
        return mInputTempPath;
    }

    public void setHighTempPath(String name) {
        mHighTempPath = mSensorPath + name;
    }

    public String getSensorHighTempPath() {
        return mHighTempPath;
    }

    public void setLowTempPath(String name) {
        mLowTempPath = mSensorPath + name;
    }

    public String getSensorLowTempPath() {
        return mLowTempPath;
    }

    public void setCurrTemp(int temp) {
        mCurrTemp = temp;
    }

    public int getCurrTemp() {
        return mCurrTemp;
    }

    public int movingAverageTemp(int currTemp) {
        int index, calIndex;
        int sensorState = 0;
        int predictedTemp = 0;
        if (mSensorState > 0) sensorState = mSensorState;
        mRecordedValuesListHead = (mRecordedValuesListHead + 1) % mRecordedValuesList.length;
        mRecordedValuesList[mRecordedValuesListHead] = currTemp;
        /* Sensor State starts with -1, InstancesList starts with 0 */
        for (index = 0; index < mNumberOfInstancesList[sensorState + 1]; index++) {
            calIndex = mRecordedValuesListHead - index;
            if (calIndex < 0) {
                calIndex = mRecordedValuesList.length + calIndex;
            }
            predictedTemp += mRecordedValuesList[calIndex];
        }
        predictedTemp = predictedTemp / index;
        return predictedTemp;
    }

    /**
     * Method to read the current temperature from sensor. This method should be
     * used only when we want to obtain the latest temperature from sensors.
     * Otherwise, the getCurrTemp method should be used, which returns the
     * previously read value.
     */
    public void updateSensorTemp() {
        int val = ThermalManager.INVALID_TEMP;
        try {
            String tempStr = ThermalManager.readSysfs(mInputTempPath);
            if (tempStr != null) {
                val = Integer.parseInt(tempStr.trim());
            }
        } catch (NumberFormatException e) {
            Log.i(TAG, "NumberFormatException in updateSensorTemp():" + mInputTempPath);
        }
        if (mIsMovingAverage) {
            setCurrTemp(movingAverageTemp(val));
        } else {
            setCurrTemp(val);
        }
    }

    /* Method to determine the current thermal state of sensor */
    // TBD: needs to be removed. calculateSensorState() needs to be used instead.
    public int getCurrState() {
        int currTemp = getCurrTemp();
        /* Return OFF state if temperature less than starting of thresholds */
        if (currTemp < mTempThresholds[0]) return ThermalManager.THERMAL_STATE_OFF;
        if (currTemp >= mTempThresholds[mTempThresholds.length - 2])
            return ThermalManager.THERMAL_STATE_CRITICAL;

        for (int i = 0; i < (mTempThresholds.length - 1); i++) {
            if (currTemp >= mTempThresholds[i] && currTemp < mTempThresholds[i+1])
                return i;
        }

        /* should never come here */
        return ThermalManager.THERMAL_STATE_OFF;
    }

    public int getSensorThermalState() {
        return mSensorState;
    }

    public void setSensorThermalState(int state) {
        mSensorState = state;
    }

    // method overloaded
    // TBD:this overloaded method needs to be removed,
    // and calculateSensorState needs to be used.
    // Change needs to be made to modemzone class.
    public int getCurrState(int currTemp) {
        // Return OFF state if temperature less than starting of thresholds
        if (currTemp < mTempThresholds[0]) return ThermalManager.THERMAL_STATE_OFF;

        if (currTemp >= mTempThresholds[mTempThresholds.length - 2])
            return ThermalManager.THERMAL_STATE_CRITICAL;

        for (int i = 0; i < (mTempThresholds.length - 1); i++) {
            if (currTemp >= mTempThresholds[i] && currTemp < mTempThresholds[i+1])
                return i;
        }

        // should never come here
        return ThermalManager.THERMAL_STATE_OFF;
    }

    public int getLowerThresholdTemp(int index) {
        if (index < 0 || index >= mTempThresholds.length)
            return -1;
        return mTempThresholds[index];
    }

    public int getUpperThresholdTemp(int index) {
        if (index < 0 || index >= mTempThresholds.length)
            return -1;
        return mTempThresholds[index + 1];
    }


    // Modem specific sensor IDs
    public void UpdateSensorID() {
        mSensorID = -1;
        if (mSensorName == null) return;

        if (mSensorName.contains("PCB")) {
            mSensorID = OemTelephonyConstants.MODEM_SENSOR_ID_PCB;
        } else if (mSensorName.contains("RF")) {
            mSensorID = OemTelephonyConstants.MODEM_SENSOR_ID_RF;
        } else if (mSensorName.contains("BB")) {
            mSensorID = OemTelephonyConstants.MODEM_SENSOR_ID_BASEBAND_CHIP;
        }
    }

    public int calculateSensorState(int currTemp) {
        // Return OFF state if temperature less than starting of thresholds
        if (currTemp < mTempThresholds[0])
            return ThermalManager.THERMAL_STATE_OFF;

        if (currTemp >= mTempThresholds[mTempThresholds.length - 2])
            return ThermalManager.THERMAL_STATE_CRITICAL;

        for (int i = 0; i < (mTempThresholds.length - 1); i++) {
            if (currTemp >= mTempThresholds[i] && currTemp < mTempThresholds[i + 1])
                return i;
        }

        // should never come here
        return ThermalManager.THERMAL_STATE_OFF;
    }

    /**
     * This fucntion updates the sensor attributes. However for a low event, if
     * the decrease in sensor temp is less than the debounce interval, donot
     * change sensor state. Hence the sensor state changes only for a high event
     * or a low event which staisfies debounce condition
     */
    public void updateSensorAttributes(int debounceInterval) {
        /* read sysfs and update the sensor temp variable mCurrTemp */
        updateSensorTemp();
        int oldSensorState = getSensorThermalState();
        int newSensorState = calculateSensorState(mCurrTemp);

        // If the sensor's state has not changed, just update temperature and return
        if (newSensorState == oldSensorState)
            return;

        /*
         * if new sensor state in THERMAL_STATE_OFF, donot consider debounce.
         * Update the sensor stae, temp and return
         */
        if (newSensorState == ThermalManager.THERMAL_STATE_OFF) {
            setSensorThermalState(newSensorState);
            return;
        }

        /*
         * get the lower temp threshold for the old state. this is used for
         * debounce test
         */
        int threshold = getLowerThresholdTemp(oldSensorState);
        /*
         * second condition of if is tested only if (newState < oldState) is
         * true
         */
        if (newSensorState < oldSensorState && (mCurrTemp > (threshold - debounceInterval))) {
            Log.i(TAG, " THERMAL_LOW_EVENT for sensor:" + getSensorName()
                    + " rejected due to debounce interval");
            return;
        }

        /* debounce check passed, now update the sensor state */
        setSensorThermalState(newSensorState);
    }

    /* overloaded function */
    public void updateSensorAttributes(int debounceInterval, int temp) {
        /* donot read sysfs. just update the sensor temp variable mCurrTemp */
        setCurrTemp(temp);
        int oldSensorState = getSensorThermalState();
        int newSensorState = calculateSensorState(mCurrTemp);

        /*
         * if state for the sensor has not changed, just update the temperature
         * and return
         */
        if (newSensorState == oldSensorState)
            return;

        /*
         * if new sensor state in THERMAL_STATE_OFF, do not consider debounce.
         * Update the sensor state, temperature etc and return
         */
        if (newSensorState == ThermalManager.THERMAL_STATE_OFF) {
            setSensorThermalState(newSensorState);
            return;
        }

        /*
         * Get the lower temperature threshold for the old state. This is used
         * for debounce test.
         */
        int threshold = getLowerThresholdTemp(oldSensorState);
        /*
         * second condition of if is tested only if (newState < oldState) is
         * true
         */
        if (newSensorState < oldSensorState && (mCurrTemp > (threshold - debounceInterval))) {
            Log.i(TAG, " THERMAL_LOW_EVENT for sensor:" + getSensorName()
                    + " rejected due to debounce interval");
            return;
        }

        /* debounce check passed, now update the sensor state */
        setSensorThermalState(newSensorState);
    }
}
