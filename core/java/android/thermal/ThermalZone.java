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

    /* values for "ThermalZone" field in the THERMAL_STATE_CHANGED */
    public static int THERMAL_ZONE_CPU;
    public static int THERMAL_ZONE_TOP_SKIN;
    public static int THERMAL_ZONE_BOTTOM_SKIN;
    public static int THERMAL_ZONE_BATTERY;
    public static int THERMAL_ZONE_TELEPHONY;

    /* values for "STATE" field in the THERMAL_STATE_CHANGED Intent */
    public static final int THERMAL_STATE_OFF = -1;
    public static final int THERMAL_STATE_NORMAL = 0;
    public static final int THERMAL_STATE_WARNING = 1;
    public static final int THERMAL_STATE_ALERT = 2;
    public static final int THERMAL_STATE_CRITICAL = 3;

    /* values of the "EVENT" field in the THERMAL_STATE_CHANGED intent */
    /* Indicates type of event */
    public static final int THERMAL_HIGH_EVENT = 2;
    public static final int THERMAL_LOW_EVENT = 1;

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

    public int getPollDelay(int index) {
        if (index < 0 || index >= mPollDelayList.length)
            return -1;
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
                  ThermalEvent event = new ThermalEvent(mZoneID, mCurrEventType, mCurrThermalState, mZoneTemp);
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
        ThermalEvent event = new ThermalEvent(mZoneID, mCurrEventType, mCurrThermalState, mZoneTemp);
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
               update();
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
        int newThermalState = 0;
        int threshold = 0;
        int lowEventThermalState = 0;
        int oneTimeExecution = 1;

        for (ThermalSensor ts : mThermalSensors) {
            ts.updateSensorTemp();
            mZoneTemp = ts.getCurrTemp();
            threshold = ts.getThermalThreshold(mCurrThermalState);
            int sensorState = ts.getCurrState();
            if (sensorState > newThermalState) newThermalState = sensorState;

            /* Debounce interval calculations not required for THERMAL_STATE_OFF */
            if (mCurrThermalState == THERMAL_STATE_OFF) continue;
            /* Parameter assignment for considering debounce interval */
            if (oneTimeExecution == 1) {
                oneTimeExecution = 0;
                lowEventThermalState = sensorState;
            }
            if (sensorState < mCurrThermalState && sensorState > lowEventThermalState) {
                lowEventThermalState = sensorState;
            }
        }
        if (newThermalState == mCurrThermalState) return false;

        /* set the Event type */
        mCurrEventType = newThermalState > mCurrThermalState ? THERMAL_HIGH_EVENT : THERMAL_LOW_EVENT;

        /* For THERMAL_STATE_OFF debounce interval is not considered */
        if (newThermalState == THERMAL_STATE_OFF) {
           mCurrThermalState = newThermalState;
           return true;
        }
        /* Consider the debounce interval while de-throtlling */
        if ((mCurrEventType == THERMAL_LOW_EVENT) && (mZoneTemp > (threshold - mDebounceInterval))) {
            Log.i(TAG, " THERMAL_LOW_EVENT rejected due to debounce interval ");
            return false;
        }

        /* update Current State for this thermal zone */
        mCurrThermalState = newThermalState;
        return true;
    }
}
