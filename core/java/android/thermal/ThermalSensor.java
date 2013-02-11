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

package android.thermal;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlPullParserException;
import android.util.Log;
import java.util.ArrayList;
import android.os.UEventObserver;
import android.thermal.SysfsManager;
import java.io.File;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import android.thermal.ThermalZone;
// Modem specific imports
import com.intel.internal.telephony.OemTelephony.OemTelephonyConstants;

/**
 * The ThermalSensor class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED} Intent.
 *
 */
public class ThermalSensor {

    private static final String TAG = "ThermalSensor";
    private String mSensorPath;     /* sys path to read temp from */
    private String mSensorName;     /* name of the sensor */
    private String mInputTempPath;  /* sys path to read the current temp */
    private String mHighTempPath;   /* sys path to set the intermediate upper threshold */
    private String mLowTempPath;    /* sys path to set the intermediate lower threshold */
    private String mUEventDevPath;  /* sys path for uevent listener */

    // Sysfs path and node names for attributes of a sensor
    private static final String mSysfsSensorBasePath = "/sys/class/thermal/thermal_zone";
    private static final String mSysfsSensorType = "/type";

    private boolean isAlreadyObserving = false;
    private int mSensorID;
    private int mCurrTemp;          /* Holds the latest temperature of the sensor */
    private int upperLimit = 90;    /* intermediate lower thershold */
    private int lowerLimit = 0;     /* intermediate upper threshold */
    private int mTempThresholds[];  /* array contain the temperature thresholds */
    private int mSensorState;
    private static final int INVALID_TEMP = 0xDEADBEEF;

    public void printAttrs() {
        Log.i(TAG, "mSensorID: " + Integer.toString(mSensorID));
        Log.i(TAG, "mSensorPath: " + mSensorPath);
        Log.i(TAG, "mSensorName: " + mSensorName);
        Log.i(TAG, "mInputTempPath: " + mInputTempPath);
        Log.i(TAG, "mHighTempPath: " + mHighTempPath);
        Log.i(TAG, "mLowTempPath: " + mLowTempPath);
        Log.i(TAG, "mUEventDevPath: " + mUEventDevPath);
        Log.i(TAG, "mTempThresholds");
        for (int val : mTempThresholds)
            Log.i(TAG, Integer.toString(val));
    }

    private void setSensorSysfsPath() {
        int count = 0;
        while (new File (mSysfsSensorBasePath + count + mSysfsSensorType).exists()) {
           String name = SysfsManager.readSysfs(mSysfsSensorBasePath + count + mSysfsSensorType);
           if (name != null && (name.equalsIgnoreCase(mSensorName) ||
              (mSensorName.equalsIgnoreCase("battery") && name.contains("_battery")))) {
              mSensorPath = mSysfsSensorBasePath + count + "/";
              break;
           }
           count++;
        }
    }

    public ThermalSensor() {
        mSensorState = ThermalZone.THERMAL_STATE_OFF;
        mCurrTemp = INVALID_TEMP;
    }


    public int getSensorID() {
        return mSensorID;
    }

    public void setSensorID(int id) {
        mSensorID = id;
    }

    public String getSensorPath() {
        return mSensorPath;
    }

    /**
     * This function sets the sensor path to the given String value.
     * If the String is "auto" it loops through the standard sysfs path,
     * to obtain the 'mSensorPath'. The standard sysfs path is /sys/class/
     * thermal/thermal_zoneX and the look up is based on 'mSensorName'.
     */
    public void setSensorPath(String path) {
        if (path.equalsIgnoreCase("auto")) {
           setSensorSysfsPath();
        } else {
           mSensorPath = path;
        }
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

    public void setInputTempPath(String Path) {
        mInputTempPath = mSensorPath + Path;
    }

    public String getSensorInputTempPath() {
        return mInputTempPath;
    }

    public void setHighTempPath(String Path) {
        mHighTempPath = mSensorPath + Path;
    }

    public String getSensorHighTempPath() {
        return mHighTempPath;
    }

    public void setLowTempPath(String Path) {
        mLowTempPath = mSensorPath + Path;
    }

    public String getSensorLowTempPath() {
        return mLowTempPath;
    }

    public void setCurrTemp(int temp) {
        this.mCurrTemp = temp;
    }

    public int getCurrTemp() {
        return mCurrTemp;
    }

    /**
     * Method to read the current temperature from sensor.
     * This method should be used only when we want to obtain
     * the latest temperature from sensors. Otherwise, the
     * getCurrTemp method should be used, which returns the
     * previously read value.
     */
    public void updateSensorTemp() {
        int currTemp = SysfsManager.readSysfsAsInt(mInputTempPath);
        setCurrTemp(currTemp);
    }

    /* Method to determine the current thermal state of sensor */
    // TBD: needs to be removed. calculateSensorState() needs to be used instead.
    public int getCurrState() {
        int currTemp = getCurrTemp();
        /* Return OFF state if temperature less than starting of thresholds */
        if (currTemp < mTempThresholds[0]) return ThermalZone.THERMAL_STATE_OFF;
        if (currTemp >= mTempThresholds[mTempThresholds.length - 2])
            return ThermalZone.THERMAL_STATE_CRITICAL;

        for (int i = 0; i < (mTempThresholds.length - 1); i++) {
            if (currTemp >= mTempThresholds[i] && currTemp < mTempThresholds[i+1])
                return i;
        }

        /* should never come here */
        return ThermalZone.THERMAL_STATE_OFF;
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
        if (currTemp < mTempThresholds[0]) return ThermalZone.THERMAL_STATE_OFF;

        if (currTemp >= mTempThresholds[mTempThresholds.length - 2])
            return ThermalZone.THERMAL_STATE_CRITICAL;

        for (int i = 0; i < (mTempThresholds.length - 1); i++) {
            if (currTemp >= mTempThresholds[i] && currTemp < mTempThresholds[i+1])
                return i;
        }

        // should never come here
        return ThermalZone.THERMAL_STATE_OFF;
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
        if (currTemp < mTempThresholds[0]) return ThermalZone.THERMAL_STATE_OFF;

        if (currTemp >= mTempThresholds[mTempThresholds.length - 2])
            return ThermalZone.THERMAL_STATE_CRITICAL;

        for (int i = 0; i < (mTempThresholds.length - 1); i++) {
            if (currTemp >= mTempThresholds[i] && currTemp < mTempThresholds[i+1])
                return i;
        }

        // should never come here
        return ThermalZone.THERMAL_STATE_OFF;
    }

    /**
     * this fucntion updates the sensor attributes. However for a low event,
     * if the decrease in sensor temp is less than the debounce interval, donot
     * change sensor state. Hence the sensor state changes only for a high event
     * or a low event which staisfies debounce condition
     */
    public void updateSensorAttributes(int debounceInterval) {
        /* read sysfs and update the sensor temp variable mCurrTemp */
        updateSensorTemp();
        int oldSensorState = getSensorThermalState();
        int newSensorState = calculateSensorState(mCurrTemp);

        /* if state for the sensor has not changed, just update the temparature and return */
        if (newSensorState == oldSensorState) return;

        /* if new sensor state in THERMAL_STATE_OFF, donot consider debounce.
           Update the sensor stae, temp and return */
        if (newSensorState ==  ThermalZone.THERMAL_STATE_OFF) {
            setSensorThermalState(newSensorState);
            return;
        }

        /* get the lower temp threshold for the old state. this is used for debounce test */
        int threshold = getLowerThresholdTemp(oldSensorState);
        /* second condition of if is tested only if (newState < oldState) is true */
        if ((newSensorState < oldSensorState) && (mCurrTemp > (threshold - debounceInterval))) {
            Log.i(TAG, " THERMAL_LOW_EVENT for sensor:" +getSensorName() + " rejected due to debounce interval");
            return;
        }

        /* debounce check passed, now update the sensor state */
        setSensorThermalState(newSensorState);
    }
}
