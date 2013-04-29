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
import java.io.File;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import android.thermal.ThermalSensor;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.lang.reflect.*;
import android.thermal.ThermalServiceEventQueue;
import android.thermal.ThermalEvent;

/**
 * The ThermalZone class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED} Intent.
 *
 */
public class ThermalZone {

    private static final String TAG = "ThermalZone";
    /**
     * Extra for {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the thermal zone.
     */
    public static final String EXTRA_ZONE = "zone";
    /**
     * Extra for {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the thermal state of the zone.
     */
    public static final String EXTRA_STATE = "state";
    /**
     * Extra for {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the thermal event type for the zone.
     */
    public static final String EXTRA_EVENT = "event";
    /**
     * Extra for {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * integer containing the temperature of the zone.
     */
    public static final String EXTRA_TEMP = "temp";
    /**
     * Extra for {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED}:
     * String containing the name of the zone.
     */
    public static final String EXTRA_NAME = "name";

    /* values for "STATE" field in the THERMAL_STATE_CHANGED Intent */
    public static final int THERMAL_STATE_OFF = -1;
    public static final int THERMAL_STATE_NORMAL = 0;
    public static final int THERMAL_STATE_WARNING = 1;
    public static final int THERMAL_STATE_ALERT = 2;
    public static final int THERMAL_STATE_CRITICAL = 3;

    public static final String STATE_NAMES[] = {"OFF", "NORMAL", "WARNING", "ALERT", "CRITICAL"};
    /* values of the "EVENT" field in the THERMAL_STATE_CHANGED intent */
    /* Indicates type of event */
    public static final int THERMAL_LOW_EVENT = 1;
    public static final int THERMAL_HIGH_EVENT = 2;
    /* Thermal Zone related members */
    protected int mZoneID;               /* ID of the Thermal zone */
    protected int mCurrThermalState;     /* Current thermal state of the zone */
    protected int mCurrEventType;        /* specifies thermal event type, HIGH or LOW */
    protected String mZoneName;          /* Name of the Thermal zone */
    protected ArrayList <ThermalSensor> mThermalSensors = new ArrayList <ThermalSensor>();
                                        /* List of sensors under this thermal zone */
    protected int mZoneTemp;             /* Temperature of the Thermal Zone */
    private int mDebounceInterval;     /* Debounce value to avoid thrashing of throttling actions */
    private int mPollDelayList[];          /* Delay between sucessive polls */
    private boolean mSupportsUEvent;   /* Determines if Sensor supports Uvevents */
    private boolean mSensorLogic;      /* AND or OR logic to be used to determine thermal state of zone */
    private static final int INVALID_TEMP = 0xDEADBEEF;

    public void printAttrs() {
        Log.i(TAG, "mZoneID:" + Integer.toString(mZoneID));
        Log.i(TAG, "mDBInterval: " + Integer.toString(mDebounceInterval));
        Log.i(TAG, "mZoneName:" + mZoneName);
        Log.i(TAG, "mSupportsUEvent:" + Boolean.toString(mSupportsUEvent));
        Log.i(TAG, "mSensorLogic:" + Boolean.toString(mSensorLogic));

        for (int val : mPollDelayList)
            Log.i(TAG, Integer.toString(val));

        for (ThermalSensor ts : mThermalSensors)
            ts.printAttrs();
    }

    public ThermalZone() {
        Log.i(TAG, "ThermalZone constructor called");
        mCurrThermalState = THERMAL_STATE_OFF;
        mZoneTemp = INVALID_TEMP;
    }

    public static String getStateAsString(int index) {
        index++;
        if (index < -1 || index > 3)
           return "Invalid";

        return STATE_NAMES[index];
    }

    public static String getEventTypeAsString(int type) {
        return type == 1 ? "LOW" : "HIGH";
    }

    public void setSensorList(ArrayList<ThermalSensor> ThermalSensors) {
        mThermalSensors = ThermalSensors;
    }

    public ArrayList<ThermalSensor> getThermalSensorList() {
        return mThermalSensors;
    }

    public int getCurrThermalState() {
        return mCurrThermalState;
    }

    public void setCurrThermalState(int state) {
        mCurrThermalState = state;
    }

    public int getCurrEventType() {
        return mCurrEventType;
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

    public void setSensorLogic(int flag) {
        mSensorLogic = (flag == 1);
    }

    public boolean getSensorLogic() {
        return mSensorLogic;
    }

    public void setDBInterval(int interval) {
        mDebounceInterval = interval;
    }

    public int getDBInterval() {
        return mDebounceInterval;
    }

    public void setPollDelay(ArrayList<Integer> delayList) {
        if (delayList == null ) {
            Log.i(TAG, "setPollDelay input is null");
            mPollDelayList = null;
            return;
        }
        mPollDelayList = new int[delayList.size()];
        if (mPollDelayList == null) {
            Log.i(TAG, "failed to create poll delaylist");
            return;
        }
        try {
            for (int i = 0; i < delayList.size(); i++) {
                mPollDelayList[i] = delayList.get(i);
            }
       } catch (IndexOutOfBoundsException e) {
           Log.i(TAG, "IndexOutOfBoundsException caught in setPollDelay()\n");
       }
    }

    /* in polldelay array,index of TOFF =0, Normal = 1, Warning = 2, Alert = 3, Critical = 4.
       Thermal Zone states are enumarated as TOFF = -1,Normal =0,Warning = 1,Alert = 2,Critical = 3.
       Hence we add 1 while querying poll delay */
    public int getPollDelay(int index) {
        index ++;
        /* if polldelay is requested for an invalid state,
           donot return invalid number like -1, return the delay corresponding
           to normal state */
        if (index < 0 || index >= mPollDelayList.length) index = THERMAL_STATE_NORMAL + 1;

        return mPollDelayList[index];
    }

    public class monitorThermalZone implements Runnable {
        Thread t;
        monitorThermalZone() {
           String threadName = "ThermalZone" + getZoneId();
           t = new Thread (this, threadName);
           t.start();
        }

        public void run() {
           try {
            while (true) {
               if (isZoneStateChanged()) {
                  ThermalEvent event = new ThermalEvent(mZoneID, mCurrEventType, mCurrThermalState, mZoneTemp, mZoneName);
                  try {
                      ThermalServiceEventQueue.eventQueue.put(event);
                  }
                  catch (InterruptedException ex) {
                      Log.i(TAG, "caught InterruptedException in posting to event queue");
                  }
               }
               Thread.sleep(getPollDelay(mCurrThermalState));
            }
          } catch (InterruptedException iex) {
            Log.i(TAG, "caught InterruptedException in run()");
          }
       }
    }

    public void update() {
        Log.i(TAG, " state of thermal zone " + mZoneID + " changed to " + mCurrThermalState + " at temperature " + mZoneTemp);
        ThermalEvent event = new ThermalEvent(mZoneID, mCurrEventType, mCurrThermalState, mZoneTemp, mZoneName);
        try {
           ThermalServiceEventQueue.eventQueue.put(event);
        }
        catch (InterruptedException ex) {
            Log.i(TAG, "caught InterruptedException in update()");
        }
    }

    private UEventObserver mThermalZoneObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
               Log.i(TAG, "UEvent received ");
               if (isZoneStateChanged()) {
                   update();
               }
        }
    };

    public void startMonitoring() {
        if (!mSupportsUEvent) {
            new monitorThermalZone();
        }
        else {
            mThermalZoneObserver.startObserving("SUBSYSTEM=thermal");
        }
    }

    public boolean isZoneStateChanged() {
        int newMaxSensorState = -1;
        int tempSensorState = -1;
        int currMaxTemp = INVALID_TEMP;
        int oldZoneState = mCurrThermalState;

        /* browse through all sensors and update sensor states, and record the max
        sensor state and max sensor temp */
        for (ThermalSensor ts : mThermalSensors) {
            /* updateSensorAttributes updates sensor state.Debaounce Interval is passed
            as input, so that for LOWEVENT, if decrease in sensor temp is less than debounce
            interval, sensor state change is ignored and original state is maintained */
            ts.updateSensorAttributes(mDebounceInterval);
            tempSensorState = ts.getSensorThermalState();
            if (tempSensorState > newMaxSensorState) {
                newMaxSensorState = tempSensorState;
                currMaxTemp = ts.getCurrTemp();
            }
        }

        /* zone state is always max of sensor states. newMaxSensorState is
        supposed to be new zone state. But if zone is already in that state,
        no intent needs to be sent, hence return false */
        if (newMaxSensorState == oldZoneState) return false;

        /* else update the current zone state, zone temp*/
        mCurrThermalState = newMaxSensorState;
        /* set the Event type */
        mCurrEventType = mCurrThermalState > oldZoneState ? THERMAL_HIGH_EVENT : THERMAL_LOW_EVENT;
        /* set zone temp equal to the max sensor temp */
        mZoneTemp = currMaxTemp;

        return true;

    }
}
