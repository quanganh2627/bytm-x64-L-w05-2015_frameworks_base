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
import java.util.Arrays;

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
    private boolean mIsSensorActive = false; /* Whether this sensor is active */
    private Integer mTempThresholds[];  /* Array containing temperature thresholds */

    /* MovingAverage related declarations */
    private int mRecordedValuesHead = -1; /* Index pointing to the head of past values of sensor */
    private int mRecordedValues[];        /* Recorded values of sensor */
    private int mNumberOfInstances[];     /* Number of recorded instances to be considered */
    private boolean mIsMovingAverage = false; /* By default false */

    public void printAttrs() {
        Log.i(TAG, "mSensorID: " + Integer.toString(mSensorID));
        Log.i(TAG, "mSensorPath: " + mSensorPath);
        Log.i(TAG, "mSensorName: " + mSensorName);
        Log.i(TAG, "mInputTempPath: " + mInputTempPath);
        Log.i(TAG, "mHighTempPath: " + mHighTempPath);
        Log.i(TAG, "mLowTempPath: " + mLowTempPath);
        Log.i(TAG, "mUEventDevPath: " + mUEventDevPath);
        Log.i(TAG, "mErrorCorrection: " + mErrorCorrectionTemp);
        Log.i(TAG, "mTempThresholds[]: " + Arrays.toString(mTempThresholds));
        Log.i(TAG, "mNumberOfInstances[]: " + Arrays.toString(mNumberOfInstances));
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

    public boolean getSensorActiveStatus() {
        return mIsSensorActive;
    }

    public String getSensorSysfsPath() {
        return mSensorPath;
    }

    public ThermalSensor() {
        mSensorState = ThermalManager.THERMAL_STATE_OFF;
        mCurrTemp = ThermalManager.INVALID_TEMP;
        /**
         * By default set uevent path to invalid. if uevent flag is set for a
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
     * If sensor path is "none", sensor temp is not read via any sysfs
     */
    public void setSensorPath(String path) {
        if (path.equalsIgnoreCase("auto")) {
            setSensorSysfsPath();
        } else {
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
        if (thresholdList != null ) {
            mTempThresholds = new Integer[thresholdList.size()];
            if (mTempThresholds != null) {
                mTempThresholds = thresholdList.toArray(mTempThresholds);
            }
        }
    }

    public int getThermalThreshold(int index) {
        if (index < 0 || index >= mTempThresholds.length)
            return -1;
        return mTempThresholds[index];
    }

    public Integer[] getTempThresholds() {
        return mTempThresholds;
    }

    public void setMovingAvgWindow(ArrayList<Integer> windowList, Integer[] delay) {
        int maxValue = Integer.MIN_VALUE; // -2^31

        if (windowList == null || delay == null) {
            Log.i(TAG, "setMovingAvgWindow input is null");
            return;
        }
        mNumberOfInstances = new int[windowList.size()];
        if (mNumberOfInstances == null) {
            Log.i(TAG, "failed to create poll windowlist");
            return;
        }
        mIsMovingAverage = true;
        try {
            for (int i = 0; i < windowList.size(); i++) {
                if (delay[i] == 0) {
                    mIsMovingAverage = false;
                    Log.i(TAG, "Polling delay is zero, WMA disabled\n");
                    return;
                }
                mNumberOfInstances[i] = windowList.get(i) / delay[i];
                if (mNumberOfInstances[i] <= 0) {
                    mIsMovingAverage = false;
                    Log.i(TAG, "Polling delay greater than moving average window, WMA disabled\n");
                    return;
                }
                maxValue = Math.max(mNumberOfInstances[i], maxValue);
            }
            mRecordedValues = new int[maxValue];
        } catch (IndexOutOfBoundsException e) {
            Log.i(TAG, "IndexOutOfBoundsException caught in setMovingAvgWindow()\n");
        }
    }

    public void setInputTempPath(String name) {
        // sensor path is none, it means sensor temperature reporting is
        // not sysfs based. So turn sensor active by default.
        // If the sensor path does not exist, deactivate the sensor.
        if (mSensorPath != null && mSensorPath.equalsIgnoreCase("none")) {
            mIsSensorActive = true;
        } else {
            mInputTempPath = mSensorPath + name;
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
        int predictedTemp = 0;

        mRecordedValuesHead = (mRecordedValuesHead + 1) % mRecordedValues.length;
        mRecordedValues[mRecordedValuesHead] = currTemp;

        // Sensor State starts with -1, InstancesList starts with 0
        for (index = 0; index < mNumberOfInstances[mSensorState + 1]; index++) {
            calIndex = mRecordedValuesHead - index;
            if (calIndex < 0) {
                calIndex = mRecordedValues.length + calIndex;
            }
            predictedTemp += mRecordedValues[calIndex];
        }
        return predictedTemp / index;
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

    public int getSensorThermalState() {
        return mSensorState;
    }

    public void setSensorThermalState(int state) {
        mSensorState = state;
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

    public void updateSensorAttributes(int debounceInterval) {
        /* read sysfs and update the sensor temp variable mCurrTemp */
        updateSensorTemp();
        updateSensorAttributes(debounceInterval, mCurrTemp);
    }

    /**
     * This function updates the sensor attributes. However for a low event, if
     * the decrease in sensor temp is less than the debounce interval, donot
     * change sensor state. Hence the sensor state changes only for a high event
     * or a low event which satisfies debounce condition.
     */
    public void updateSensorAttributes(int debounceInterval, int temp) {
        // Do not read sysfs. just update the sensor temp variable mCurrTemp
        setCurrTemp(temp);
        int oldSensorState = getSensorThermalState();
        int newSensorState = ThermalManager.calculateThermalState(mCurrTemp, mTempThresholds);

        if (newSensorState == oldSensorState) return;

        // If new sensor state in THERMAL_STATE_OFF, do not consider debounce.
        // Update the sensor state, temperature etc and return
        if (newSensorState == ThermalManager.THERMAL_STATE_OFF) {
            setSensorThermalState(newSensorState);
            return;
        }

        int threshold = getLowerThresholdTemp(oldSensorState);
        if (newSensorState < oldSensorState && mCurrTemp > (threshold - debounceInterval)) {
            Log.i(TAG, " THERMAL_LOW_EVENT for sensor:" + getSensorName()
                    + " rejected due to debounce interval");
            return;
        }

        // Debounce check passed, now update the sensor state
        setSensorThermalState(newSensorState);
    }
}
