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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.os.ServiceManager;
import com.intel.internal.telephony.OemTelephony.IOemTelephony;
import com.intel.internal.telephony.OemTelephony.OemTelephonyConstants;
import android.thermal.ThermalSensor;
import android.thermal.ThermalServiceEventQueue;
import android.thermal.ThermalEvent;
import android.thermal.ThermalCoolingManager;
import android.util.Log;
import java.lang.Math;
import java.util.ArrayList;
/**
 * ModemZone class
 *@hide
 */

// mCurrThermalState, mZoneTemp is the max of three modem sensors namely
// PCB,BB,RF. These variable needs to be updated synchronously from the
// intent receiver function
public class ModemZone extends ThermalZone {
    private static final String TAG = "Thermal:ModemZone";
    private IOemTelephony mPhoneService = null;
    private boolean exit;
    private Context mContext;
    // oem string related constants
    private static final int FILTERED_TEMP_INDEX = 0;
    private static final int RAW_TEMP_INDEX = 1;
    private static final int MAX_TEMP_VALUES = 2;
    private static final int INVALID_TEMP = 0xDEADBEEF;
    // lock to update and read zone attributes, zonestate and temp
    private static final Object sZoneAttribLock = new Object();
    private int mSensorIDwithMaxTemp;
    private static final int MAX_NUM_SENSORS = 3;
    // indices in the sensor attribute arrays
    private static final int SENSOR_INDEX_PCB = 0;
    private static final int SENSOR_INDEX_RF = 1;
    private static final int SENSOR_INDEX_BB = 2;

    public ModemZone(Context context) {
        super();
        mPhoneService = IOemTelephony.Stub.asInterface(ServiceManager.getService("oemtelephony"));
        if (mPhoneService == null) {
            Log.i(TAG, "failed to acquire IOemTelephony interface handle\n");
            return;
        }

        // register with the intent
        mContext = context;

        IntentFilter threshold_filter = new IntentFilter();
        threshold_filter.addAction(OemTelephonyConstants.ACTION_MODEM_SENSOR_THRESHOLD_REACHED);
        mContext.registerReceiver(new ModemThresholdIndReceiver(), threshold_filter);

        // initialize zone attributes
        updateZoneAttributes(-1, THERMAL_STATE_OFF, INVALID_TEMP);

        Log.i(TAG, "Modem thermal zone registered successfully");
    }

    // activates the thresholds for all the active sensors.
    public void startMonitoring() {
        int minTemp = 0, maxTemp = 0, temp = 0;
        int currMaxSensorState, sensorState = -1;
        int finalMaxTemp = INVALID_TEMP;
        int debounceInterval = 0;

        if (mPhoneService == null) {
           Log.i(TAG, "IOemTelephony interface handle is null\n");
           return;
        }

        debounceInterval = getDBInterval();
        for (ThermalSensor t : mThermalSensors) {
            t.UpdateSensorID();
            temp = readModemSensorTemp(t);
            finalMaxTemp = Math.max(finalMaxTemp,temp);
            if (temp != INVALID_TEMP) {
                sensorState = t.getCurrState(temp * 10);
                t.setSensorThermalState(sensorState);
                t.setCurrTemp(temp * 10);
                minTemp = t.getLowerThresholdTemp(sensorState);
                maxTemp = t.getUpperThresholdTemp(sensorState);
                minTemp -= debounceInterval;
                setModemSensorThreshold(true, t, minTemp, maxTemp);
            }
        }

        if (finalMaxTemp == INVALID_TEMP) {
            Log.i(TAG, "all modem sensor temp invalid!!!exiting...");
            return;
        }


        currMaxSensorState = getMaxSensorState();
        if (isZoneStateChanged(currMaxSensorState)) {
            sendThermalEvent(mCurrEventType, mCurrThermalState, mZoneTemp);
        }
    }

    private final class ModemThresholdIndReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            int currSensorstate, currZoneState;
            int minTemp, maxTemp;
            ThermalSensor t;
            if (OemTelephonyConstants.ACTION_MODEM_SENSOR_THRESHOLD_REACHED.equals(action) == false ) return;

            synchronized (sZoneAttribLock) {
                int sensorID = intent.getIntExtra(OemTelephonyConstants.MODEM_SENSOR_ID_KEY, 0);
                int temperature = intent.getIntExtra(OemTelephonyConstants.MODEM_SENSOR_TEMPERATURE_KEY, 0);
                temperature *= 10;// convert to millidegree celcius
                t = getThermalSensorObject(sensorID);
                if (t == null) return;

                Log.i(TAG, "Got notification for Sensor:" + t.getSensorName() + " with Current Temperature " + temperature);

                // this method is triggered asynchonously for any sensor that trips the threshold. The zone state,
                // is the max of all the sensor states. If a sensor moves to a state, that the zone is already in,
                // no action is taken. Only the upper and lower thresholds for the given state are reactivated for the
                // respective sensor.
                // if the sensor moves to a different state, this gets updated synchronously in zone state variables.
                currSensorstate = t.getCurrState(temperature);
                t.setSensorThermalState(currSensorstate);
                t.setCurrTemp(temperature);

                if (isZoneStateChanged(currSensorstate)) {
                    sendThermalEvent(mCurrEventType, mCurrThermalState, mZoneTemp);
                }

                // reactivate thresholds
                minTemp = t.getLowerThresholdTemp(currSensorstate);
                maxTemp = t.getUpperThresholdTemp(currSensorstate);
                int debounceInterval = getDBInterval();
                minTemp -= debounceInterval;
                Log.i(TAG, "Threshold receiver: resetting threshold for sensor:" + t.getSensorName() + "mintemp,maxtemp:" + minTemp + "," + maxTemp + "sensorstate:" + currSensorstate);
                setModemSensorThreshold(true, t, minTemp, maxTemp);
            }
        }
    }

    private boolean isZoneStateChanged(int currSensorstate) {
        boolean retVal = false;
        if (currSensorstate != mCurrThermalState) {
            mCurrEventType = (currSensorstate < mCurrThermalState) ? THERMAL_LOW_EVENT : THERMAL_HIGH_EVENT;
            int currMaxSensorState = getMaxSensorState();
            if ((mCurrEventType == THERMAL_HIGH_EVENT) ||
            ((mCurrEventType == THERMAL_LOW_EVENT) && (currMaxSensorState < mCurrThermalState))) {
                updateZoneAttributes(getSensorIDwithMaxTemp(), currMaxSensorState, getMaxSensorTemp());
                retVal = true;
            }
        }
        return retVal;
    }

    public int getZoneCurrThermalState() {
        synchronized (sZoneAttribLock) {
            return mCurrThermalState;
        }
    }

    public void setZoneCurrThermalState(int state) {
        synchronized (sZoneAttribLock) {
            mCurrThermalState = state;
        }
    }

    public int getCurrZoneTemp() {
        synchronized (sZoneAttribLock) {
            return mZoneTemp;
        }
    }

    private void updateZoneAttributes(int sensorID, int state, int temp) {
        mSensorIDwithMaxTemp = sensorID;
        mCurrThermalState = state;
        mZoneTemp = temp;
    }

    private void sendThermalEvent (int eventType, int thermalState, int temp) {
        ThermalEvent event = new ThermalEvent(mZoneID, eventType, thermalState, temp);
        try {
            ThermalServiceEventQueue.eventQueue.put(event);
        } catch (InterruptedException ex) {
            Log.i(TAG, "InterruptedException while sending thermal event");
        }
    }

    private void setModemSensorThreshold(boolean flag, ThermalSensor t, int minTemp, int maxTemp) {
        String str;

        try {
            Log.i(TAG,"Setting Thresholds for Modem Sensor: " +
            t.getSensorName() + "--Min: " + minTemp + "Max: " + maxTemp);
            // convert temp format from millidegrees to format expected by oemTelephony class
            minTemp = minTemp / 10;
            maxTemp = maxTemp / 10;
            mPhoneService.ActivateThermalSensorNotification(flag, t.getSensorID(), minTemp, maxTemp);
        } catch (RemoteException e) {
            Log.i(TAG, "remote exception while Setting Thresholds");
        }
    }

    // this fucntion returns the temparature read for a given sensor ID.
    // the temparature returned can be either a filtered value or raw value,
    // depending on the tempindex. the temp returned is in the format 2300
    // i.e last two digits are after decimal places. it should be interpreted
    // as 23.00 degrees
    public int readModemSensorTemp(ThermalSensor t) {
        String value;
        ArrayList<Integer> tempList = new ArrayList<Integer>();
        int finalval = INVALID_TEMP;

        if (mPhoneService == null) return -1;

        try {
            value = mPhoneService.getThermalSensorValue(t.getSensorID());
        } catch (RemoteException e) {
            Log.i(TAG, "Remote Exception while reading temp for sensor:" + t.getSensorName());
            return INVALID_TEMP;
        }

        if (value != null && value.length() > 0) {
            for (String token : value.split(" ")) {
                try {
                    tempList.add(Integer.parseInt(token));
                } catch (NumberFormatException e) {
                    Log.i(TAG, token + "is not a number");
                }
            }
            finalval = tempList.get(FILTERED_TEMP_INDEX);
        }

        Log.i(TAG, "readSensorTemp():finalval for sensor:"+ t.getSensorName() + " is " + finalval);
        return finalval;
    }

    // ZoneState is the max of three modem sensors states: PCB,BB,RF
    private int getMaxSensorState() {
        int maxState = -1;
        int currState;
        for (ThermalSensor t : mThermalSensors) {
            currState = t.getSensorThermalState();
            if (maxState < currState) maxState = currState;
        }

        return maxState;
    }

    private int getMaxSensorTemp() {
        int maxTemp = INVALID_TEMP;
        int currTemp;
        for (ThermalSensor t : mThermalSensors) {
            currTemp = t.getCurrTemp();
            if (maxTemp < currTemp) maxTemp = currTemp;
        }

        return maxTemp;
    }

    private int getSensorIDwithMaxTemp() {
        int maxIndex = 0;
        int maxTemp = INVALID_TEMP,currTemp;
        int sensorID = -1;

        for (ThermalSensor t : mThermalSensors) {
            currTemp = t.getCurrTemp();
            if (maxTemp < currTemp) {
                maxTemp = currTemp;
                sensorID = t.getSensorID();
            }
        }
        return sensorID;
    }

    private ThermalSensor getThermalSensorObject(int sensorID) {
        for (ThermalSensor t : mThermalSensors) {
            if (t.getSensorID() == sensorID) {
                return t;
            }
        }
        return null;
    }
}
