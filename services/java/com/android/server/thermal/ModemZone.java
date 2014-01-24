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

import com.android.internal.telephony.TelephonyIntents;
import com.intel.internal.telephony.OemTelephony.IOemTelephony;
import com.intel.internal.telephony.OemTelephony.OemTelephonyConstants;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.lang.Math;

/**
 * ModemZone class
 *
 * @hide
 */

// mCurrThermalState is the sensor with max state,
// ZoneTemp is temperature of the sensor with max state
// These variable needs to be updated synchronously from the
// intent receiver function
// sensors - PCB,BB,RF.
public class ModemZone extends ThermalZone {
    private static final String TAG = "Thermal:ModemZone";
    private IOemTelephony mPhoneService = null;
    private boolean exit;
    private Context mContext;
    private NotificationManager mNotificationManager;
    // oem string related constants
    private static final int FILTERED_TEMP_INDEX = 0;
    private static final int RAW_TEMP_INDEX = 1;
    private static final int MAX_TEMP_VALUES = 2;
    // lock to update and read zone attributes, zonestate and temp
    private static final Object sZoneAttribLock = new Object();
    // lock to update service state and zone monitoring status variables
    private static final Object sMonitorStateLock = new Object();
    // this variable indicates telephony service state - in service,
    // out of service, emrgency call, poweroff
    private static int sServiceState = ServiceState.STATE_POWER_OFF;
    // this flag indicates if sensor thresholds are set for monitoring
    private static boolean sIsMonitoring = false;
    private int mSensorIDwithMaxTemp;
    private static final int MAX_NUM_SENSORS = 3;
    // indices in the sensor attribute arrays
    private static final int SENSOR_INDEX_PCB = 0;
    private static final int SENSOR_INDEX_RF = 1;
    private static final int SENSOR_INDEX_BB = 2;
    private static final int DEFAULT_WAIT_POLL_TIME = 30 * 1000;  // 30 seconds
    // Emergency call related locks and flags
    private static final Object sEmergencyCallLock = new Object();
    private static boolean sOnGoingEmergencyCall = false;
    private static boolean sCriticalMonitorPending = false;
    // a class variable to ensure only one monitor is active at a time
    private static final int STATUS_MONITOR_RUNNING = -1;
    private static final int STATUS_SUCCESS = 0;
    // this lock is to synchronize critical monitor async task
    private static final Object sCriticalMonitorLock = new Object();
    // this flag suggests if a critical monitor is already started
    private static boolean sIsCriticalMonitorStarted = false;
    // this variable stores the last known zone state.
    // it is used to determine if user needs to be notified via a toast
    // that critical monitor is being started again i.e if zone is still
    // in CRITICAL state, when one poliing interval for critical monitor
    // ends, user should not be notified that monitor is starting again.
    private int mLastKnownZoneState = ThermalManager.THERMAL_STATE_OFF;
    // read from thermal throttle config, critical shutdown flag
    // if shutdown flag is true, donot switch to AIRPLANE mode
    private boolean mIsCriticalShutdownEnable = false;
    private boolean mIsCriticalShutdownflagUpdated = false;
    private ModemStateBroadcastReceiver intentReceiver = new ModemStateBroadcastReceiver();
    private int mModemOffState = ThermalManager.THERMAL_STATE_OFF;
    private ThermalManager.ZoneCoolerBindingInfo mZoneCoolerBindInfo = null;

    public ModemZone(Context context) {
        super();
        mPhoneService = IOemTelephony.Stub.asInterface(ServiceManager.getService("oemtelephony"));
        if (mPhoneService == null) {
            Log.i(TAG, "failed to acquire IOemTelephony interface handle\n");
            return;
        }

        // register with the intent
        mContext = context;
        // handle to notificaiton manager
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        filter.addAction(OemTelephonyConstants.ACTION_MODEM_SENSOR_THRESHOLD_REACHED);
        filter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALL_STATUS_CHANGED);

        mContext.registerReceiver(intentReceiver, filter);

        // initialize zone attributes
        updateZoneAttributes(-1, ThermalManager.THERMAL_STATE_OFF, ThermalManager.INVALID_TEMP);

        Log.i(TAG, "Modem thermal zone registered successfully");
    }

    private void updateModemOffState() {
        int finalState = ThermalManager.THERMAL_STATE_OFF;
        ThermalCoolingDevice device = null;

        if (mZoneCoolerBindInfo != null &&
                mZoneCoolerBindInfo.getCoolingDeviceInfoList() != null) {
            for (ThermalManager.ZoneCoolerBindingInfo.CoolingDeviceInfo cDeviceInfo :
                    mZoneCoolerBindInfo.getCoolingDeviceInfoList()) {
                device = ThermalManager.sCDevMap.get(cDeviceInfo.getCoolingDeviceId());
                if (device != null && device.getDeviceName() != null &&
                        device.getDeviceName().equalsIgnoreCase("ModemAirplane")) {
                    ArrayList<Integer> list = cDeviceInfo.getThrottleMaskList();
                    if (list == null) break;
                    // iterate the list and take highest enabled state
                    for (int i = 0; i < ThermalManager.NUM_THERMAL_STATES - 1; i++) {
                       if (list.get(i) == 1) {
                           finalState = i;
                       }
                    }
                }
            }
        }

        if (finalState == ThermalManager.THERMAL_STATE_OFF ||
                (finalState ==  ThermalManager.THERMAL_STATE_CRITICAL &&
                mIsCriticalShutdownEnable == true)) {
            mModemOffState = ThermalManager.THERMAL_STATE_OFF;
        } else {
            mModemOffState = finalState;
        }
        Log.i(TAG, "ModemOff State=" + mModemOffState);
    }

    public void unregisterReceiver() {
        Log.i(TAG, "Modem zone unregister called");
        if (mContext != null) mContext.unregisterReceiver(intentReceiver);
    }

    private boolean isDebounceConditionSatisfied(ThermalSensor t,
            int temp, int debounceInterval, int oldState) {
        if (t == null) return false;
        int lowTemp = t.getLowerThresholdTemp(oldState);
        return (((lowTemp - temp) >= debounceInterval) ? true : false);
    }

    void updateCriticalShutdownFlag() {
        // one time update
        if (mIsCriticalShutdownflagUpdated ==  false) {
            mZoneCoolerBindInfo = ThermalManager.sZoneCoolerBindMap.get(getZoneId());
            if (mZoneCoolerBindInfo != null) {
                mIsCriticalShutdownEnable = mZoneCoolerBindInfo.getCriticalActionShutdown() == 1;
            }
            updateModemOffState();
            mIsCriticalShutdownflagUpdated = true;
        }
    }

    // this method is triggered in two conditions:
    // a) service state chnages from OFF to active state
    // b) there is a change is service state within 0,1,2
    // and no sensor thresholds are set.
    // startmonitoring updates all active sensor states and
    // resets thresholds.
    @Override
    public void startMonitoring() {
        int minTemp = 0, maxTemp = 0, temp = 0;
        int currMaxSensorState, sensorState = -1;
        int finalMaxTemp = ThermalManager.INVALID_TEMP;
        int debounceInterval = 0;
        int oldState = ThermalManager.THERMAL_STATE_OFF;
        if (mPhoneService == null) {
            Log.i(TAG, "IOemTelephony interface handle is null");
            return;
        }

        if (getServiceState() == ServiceState.STATE_POWER_OFF) {
            Log.i(TAG, "radio not yet available");
            return;
        }

        updateCriticalShutdownFlag();
        debounceInterval = getDBInterval();
        for (ThermalSensor t : mThermalSensors) {
            t.UpdateSensorID();
            temp = readModemSensorTemp(t);
            finalMaxTemp = Math.max(finalMaxTemp,temp);
            if (temp != ThermalManager.INVALID_TEMP) {
                temp = temp * 10; // convert to millidegree celcius
                t.setCurrTemp(temp);
                oldState = t.getSensorThermalState();
                sensorState = ThermalManager.calculateThermalState(temp, t.getTempThresholds());
                if ((sensorState < oldState) &&
                        (!isDebounceConditionSatisfied(t, temp, debounceInterval, oldState)))
                    // update sensor state only if debounce condition statisfied
                    // else retain old state
                    continue;
                Log.i(TAG, "updating sensor state:<old,new>=" + oldState + "," + sensorState);
                t.setSensorThermalState(sensorState);
            }
        }

        if (finalMaxTemp == ThermalManager.INVALID_TEMP) {
            Log.i(TAG, "all modem sensor temp invalid!!!exiting...");
            setZoneMonitorStatus(false);
            return;
        }

        updateLastKnownZoneState();
        currMaxSensorState = getMaxSensorState();
        if (isZoneStateChanged(currMaxSensorState)) {
            sendThermalEvent(mCurrEventType, mCurrThermalState, mZoneTemp);
        }

        if (currMaxSensorState == ThermalManager.THERMAL_STATE_CRITICAL &&
                mIsCriticalShutdownEnable == true) {
            // if shutdown flag is enabled for critical state, The intent sent to
            // Thermal Cooling takes care of platform shutfdown. so just return
            return;
        } else if (currMaxSensorState == mModemOffState) {
            triggerCriticalMonitor();
            setZoneMonitorStatus(false);
        } else {
            // set the thresholds after zone attributes are updated once,
            // to prevent race condition
            for (ThermalSensor t : mThermalSensors) {
                sensorState = t.getSensorThermalState();
                if (sensorState != ThermalManager.THERMAL_STATE_OFF) {
                    minTemp = t.getLowerThresholdTemp(sensorState);
                    maxTemp = t.getUpperThresholdTemp(sensorState);
                    minTemp -= debounceInterval;
                    setModemSensorThreshold(true,t, minTemp, maxTemp);
                }
            }
            setZoneMonitorStatus(true);
        }
    }

    private final class ModemStateBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            Log.i(TAG, "got intent with action: " + action);

            if (action.equals(OemTelephonyConstants.ACTION_MODEM_SENSOR_THRESHOLD_REACHED)) {
                handleSensorThesholdReached(intent);
            } else if (action.equals(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED)) {
                handleServiceStateChange(intent);
            } else if (action.equals(TelephonyIntents.ACTION_EMERGENCY_CALL_STATUS_CHANGED)) {
                handleEmergencyCallIntent(intent);
            }
        }
    }

    private static void setServiceState(int val) {
        synchronized(sMonitorStateLock) {
            sServiceState = val;
        }
    }

    private static int getServiceState() {
        synchronized(sMonitorStateLock) {
            return sServiceState;
        }
    }

    private static boolean getZoneMonitorStatus() {
        synchronized(sMonitorStateLock) {
            return sIsMonitoring;
        }
    }

    private static void setZoneMonitorStatus(boolean flag) {
        synchronized(sMonitorStateLock) {
            sIsMonitoring = flag;
        }
    }

    private boolean isZoneStateChanged(int currSensorstate) {
        boolean retVal = false;
        if (currSensorstate != mCurrThermalState) {
            mCurrEventType = (currSensorstate < mCurrThermalState) ?
                    ThermalManager.THERMAL_LOW_EVENT : ThermalManager.THERMAL_HIGH_EVENT;
            int currMaxSensorState = getMaxSensorState();
            if ((mCurrEventType == ThermalManager.THERMAL_HIGH_EVENT) ||
                    ((mCurrEventType == ThermalManager.THERMAL_LOW_EVENT) &&
                            (currMaxSensorState < mCurrThermalState))) {
                int sensorID = getSensorIdWithMaxState();
                updateZoneAttributes(sensorID,
                        currMaxSensorState, getSensorTempFromID(sensorID));
                retVal = true;
            }
        }
        return retVal;
    }

    public void updateLastKnownZoneState() {
        synchronized (sZoneAttribLock) {
            mLastKnownZoneState = mCurrThermalState;
            Log.i(TAG, "updateLastKnownZoneState: laststate:" + mLastKnownZoneState);
        }
    }

    public int getLastKnownZoneState() {
        synchronized (sZoneAttribLock) {
            Log.i(TAG, "getLastKnownZoneState: laststate:" + mLastKnownZoneState);
            return mLastKnownZoneState;
        }
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
        synchronized (sZoneAttribLock) {
            mLastKnownZoneState = mCurrThermalState;
            mSensorIDwithMaxTemp = sensorID;
            mCurrThermalState = state;
            mZoneTemp = temp;
            Log.i(TAG, "updateZoneAttrib: lastState:" + mLastKnownZoneState +
                    "currstate:" + mCurrThermalState);
        }
    }

    private void sendThermalEvent (int eventType, int thermalState, int temp) {
        ThermalEvent event = new ThermalEvent(mZoneID, eventType, thermalState, temp, mZoneName);
        try {
            ThermalManager.sEventQueue.put(event);
        } catch (InterruptedException ex) {
            Log.i(TAG, "InterruptedException while sending thermal event");
        }
    }

    private void setModemSensorThreshold(boolean flag, ThermalSensor t, int minTemp, int maxTemp) {
        String str;
        if (mPhoneService == null) return;
        try {
            Log.i(TAG,"Setting Thresholds for Modem Sensor: " +
                    t.getSensorName() + "--Min: " + minTemp + "Max: " + maxTemp);
            // convert temp format from millidegrees to format expected by oemTelephony class
            minTemp = minTemp / 10;
            maxTemp = maxTemp / 10;
            mPhoneService.ActivateThermalSensorNotification(flag,
                    t.getSensorID(), minTemp, maxTemp);
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
        int finalval = ThermalManager.INVALID_TEMP;

        if (mPhoneService == null) return -1;

        try {
            value = mPhoneService.getThermalSensorValue(t.getSensorID());
        } catch (RemoteException e) {
            Log.i(TAG, "Remote Exception while reading temp for sensor:" + t.getSensorName());
            return ThermalManager.INVALID_TEMP;
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
        if (finalval == ThermalManager.INVALID_TEMP) {
            Log.i(TAG, "readSensorTemp():finalval for sensor:"+ t.getSensorName() + " is invalid");
        } else {
            Log.i(TAG, "readSensorTemp():finalval for sensor:"+ t.getSensorName() + " is " +
                    finalval);
        }
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

    private int getSensorIdWithMaxState() {
        int maxIndex = 0;
        int maxState = ThermalManager.THERMAL_STATE_OFF, currState;
        int sensorID = -1;

        for (ThermalSensor t : mThermalSensors) {
            currState = t.getSensorThermalState();
            if (maxState < currState) {
                maxState = currState;
                sensorID = t.getSensorID();
            }
        }
        return sensorID;
    }

    private int getSensorTempFromID(int sensorID) {
        ThermalSensor t = getThermalSensorObject(sensorID);
        if (t == null) return ThermalManager.INVALID_TEMP;
        return t.getCurrTemp();
    }

    private ThermalSensor getThermalSensorObject(int sensorID) {
        for (ThermalSensor t : mThermalSensors) {
            if (t.getSensorID() == sensorID) {
                return t;
            }
        }
        return null;
    }

    private void setAirplaneMode(boolean enable) {
        int state = enable ? 1 : 0;

        // Change the system setting
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                state);
        // Post the intent
        Log.i(TAG, "sending AIRPLANE_MODE_CHANGED INTENT with enable:" + enable);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", enable);
        mContext.sendBroadcast(intent);
    }

    private boolean getAirplaneMode() {
        return (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, -1) == 1) ?
                        true : false;
    }

    // this fucntion is called in context of UI thread from a Async Task.
    // Modem zone switched platform to AIRPLANE mode and sleeps for specified
    // time. before turning AIRPLANE mode OFF
    private void sleep(int millisec) {
        try {
            Thread.sleep(millisec);
        } catch (InterruptedException iex){}
    }

    private void vibrate() {
        Notification n = new Notification();

        // after a 100ms delay, vibrate for 200ms then pause for another
        // 100ms and then vibrate for 500ms
        n.vibrate = new long[]{0, 200, 100, 500};
        if (mNotificationManager != null) {
            mNotificationManager.notify(0, n);
        }
    }

    private class CriticalStateMonitor extends AsyncTask<Void, String, Integer>{
        @Override
        protected Integer doInBackground(Void... arg0) {
            if (getCriticalMonitorStatus() == true) {
                return STATUS_MONITOR_RUNNING;
            }
            setCriticalMonitorStatus(true);
            // if last known state of zone was already CRITICAL,
            // user need not be notified again, while the polling begins again.
            if (getLastKnownZoneState() != mModemOffState) {
                vibrate();
                publishProgress("Modem heating up!" +
                        "Switching to Airplane mode for sometime");
            }
            Log.i(TAG, "setting airplaneMode ON...");
            setAirplaneMode(true);
            sleep(DEFAULT_WAIT_POLL_TIME);
            Log.i(TAG, "setting airplaneMode OFF...");
            setAirplaneMode(false);
            setCriticalMonitorStatus(false);
            return STATUS_SUCCESS;
        }

        @Override
        protected void onProgressUpdate(String ...arg){
            String str = arg[0];
            super.onProgressUpdate(arg);
            Toast.makeText(mContext, str, Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onPostExecute(Integer result){
            super.onPostExecute(result);
            Log.i(TAG, "monitor return status = " + result);
            if (result == STATUS_SUCCESS) {
                synchronized(sMonitorStateLock) {
                    if (getServiceState() != ServiceState.STATE_POWER_OFF &&
                            getZoneMonitorStatus() == false) {
                        // this handles a scenario where critical monitor turns ON airplane mode,
                        // but user forcefully turns it OFF, but modem temp is still critical.
                        // start monitoring doesn't set thresholds and doesn't call critical monitor
                        // since one instance of the async task is already running. In that case
                        // once the critical monitor finishes, no ACTION_SERVICE_STATE_CHANGE intent
                        // will be sent, as system is already out of AIRPLANE mode.
                        // to handle this, we should check if service state is not OFF,
                        // and monitorStatus is false .
                        // This check is synchronized with sMonitorStateLock.
                        setZoneMonitorStatus(true);
                        startMonitoring();
                    }
                }
            }
        }
    }

    private void handleSensorThesholdReached(Intent intent) {

        int currSensorstate, currZoneState;
        int minTemp, maxTemp;
        ThermalSensor t;

        synchronized (sZoneAttribLock) {
            int sensorID = intent.getIntExtra(OemTelephonyConstants.MODEM_SENSOR_ID_KEY, 0);
            int temperature = intent.getIntExtra(
                    OemTelephonyConstants.MODEM_SENSOR_TEMPERATURE_KEY, 0);
            temperature *= 10;// convert to millidegree celcius
            t = getThermalSensorObject(sensorID);
            if (t == null) return;

            Log.i(TAG, "Got notification for Sensor:" + t.getSensorName()
                    + " with Current Temperature " + temperature);

            // this method is triggered asynchonously for any sensor that trips the
            // threshold; The zone state, is the max of all the sensor states; If a
            // sensor moves to a state, that the zone is already in, no action is taken.
            // Only the upper and lower thresholds for the given state are reactivated
            // for the respective sensor.
            // if the sensor moves to a different state, this gets updated synchronously
            // in zone state variables

            currSensorstate = ThermalManager.calculateThermalState(
                    temperature, t.getTempThresholds());
            t.setSensorThermalState(currSensorstate);
            t.setCurrTemp(temperature);

            if (isZoneStateChanged(currSensorstate)) {
                sendThermalEvent(mCurrEventType, mCurrThermalState, mZoneTemp);
            }

            if (currSensorstate == ThermalManager.THERMAL_STATE_CRITICAL &&
                    mIsCriticalShutdownEnable == true) {
                // if shutdown flag is enabled for critical state, The intent sent to
                // Thermal Cooling takes care of platform shutfdown. so just return
                return;
            } else if (currSensorstate == mModemOffState) {
                triggerCriticalMonitor();
                setZoneMonitorStatus(false);
            } else {
                // if temp below critical, reset thresholds
                // reactivate thresholds
                minTemp = t.getLowerThresholdTemp(currSensorstate);
                maxTemp = t.getUpperThresholdTemp(currSensorstate);
                int debounceInterval = getDBInterval();
                minTemp -= debounceInterval;
                setModemSensorThreshold(true, t, minTemp, maxTemp);
                setZoneMonitorStatus(true);
            }
        }
    }

    private void handleServiceStateChange(Intent intent ) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }

        ServiceState ss = ServiceState.newFromBundle(extras);
        if (ss == null) return;
        boolean monitorStatus;
        int newServiceState;
        int oldServiceState;
        synchronized(sMonitorStateLock) {
            newServiceState = ss.getState();
            oldServiceState = getServiceState();
            setServiceState(newServiceState);
            monitorStatus = getZoneMonitorStatus();
            Log.i(TAG, "<old, new> servicestate = " + oldServiceState + ", " + newServiceState);
            Log.i(TAG, "<monitor status> = " + monitorStatus);
            if (newServiceState == ServiceState.STATE_POWER_OFF) {
                setZoneMonitorStatus(false);
            } else if (oldServiceState ==  ServiceState.STATE_POWER_OFF ||
                    monitorStatus == false) {
                setZoneMonitorStatus(true);
                startMonitoring();
            }
        }
    }

    private void handleEmergencyCallIntent(Intent intent) {
        boolean callStatus = intent.getBooleanExtra("emergencyCallOngoing", false);
        Log.i(TAG, "emergency call intent received, callStatus = " + callStatus);
        synchronized(sEmergencyCallLock) {
            sOnGoingEmergencyCall = callStatus;
            // if emergency call has ended, check if critical state monitor is pending
            if (callStatus == false) {
                if (sCriticalMonitorPending == false) {
                    return;
                }
                sCriticalMonitorPending = false;
                // if critical shutdown enable , just exit. since an intent is sent
                // to ThermalCooling to shutdown the platform
                if (getLastKnownZoneState() == ThermalManager.THERMAL_STATE_CRITICAL &&
                        mIsCriticalShutdownEnable == true) {
                    return;
                }
                // if critical monitor is pending start a async task.
                if (getCriticalMonitorStatus() == false) {
                    CriticalStateMonitor monitor = new CriticalStateMonitor();
                    monitor.execute();
                }
            }
        }
    }

    public static boolean isEmergencyCallOnGoing() {
        synchronized(sEmergencyCallLock) {
            return sOnGoingEmergencyCall;
        }
    }

    private boolean getCriticalMonitorPendingStatus(){
        synchronized(sEmergencyCallLock) {
            return sCriticalMonitorPending;
        }
    }

    private void setCriticalMonitorPendingStatus(boolean flag){
        synchronized(sEmergencyCallLock) {
            sCriticalMonitorPending = flag;
        }
    }

    private static boolean getCriticalMonitorStatus() {
        synchronized(sCriticalMonitorLock) {
            return sIsCriticalMonitorStarted;
        }
    }

    private static void setCriticalMonitorStatus(boolean flag) {
        synchronized(sCriticalMonitorLock) {
            sIsCriticalMonitorStarted = flag;
        }
    }

    private void triggerCriticalMonitor() {
        // check for ongoing emergency call, if no call in progress start a new critical
        // monitor if not already started. if call in progress set the pending critical
        // monitor flag and exit. When emergency call exits, the intent handler checks
        // for this flag and starts a new monitor if needed.
        if (isEmergencyCallOnGoing() == false && getCriticalMonitorStatus() ==  false) {
                CriticalStateMonitor monitor = new CriticalStateMonitor();
                monitor.execute();
        } else {
            setCriticalMonitorPendingStatus(true);
        }
    }
}
