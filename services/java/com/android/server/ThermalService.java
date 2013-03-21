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

package com.android.server;

import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.os.Handler;
import java.lang.reflect.Array;
import java.lang.ClassLoader;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlPullParserException;
import java.lang.NullPointerException;
import java.lang.SecurityException;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.BufferedReader;
import android.thermal.ThermalZone;
import android.thermal.ThermalSensor;
import android.thermal.ThermalServiceEventQueue;
import android.thermal.ThermalEvent;
import android.thermal.ThermalCooling;
import android.thermal.ModemZone;
import android.thermal.ThermalManager;
/**
 * The ThermalService class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED} Intent.
 *@hide
 */
public class ThermalService extends Binder {

    private static final String TAG = ThermalService.class.getSimpleName();
    private Context mContext;
    private Handler mHandler = new Handler();
    private ThermalCooling mCoolingManager;

    public class ThermalParser {
       // Names of the XML Tags
       private static final String PINFO = "PlatformInfo";
       private static final String SENSOR = "Sensor";
       private static final String ZONE = "Zone";
       private static final String MIDTHERMAL = "thermalconfig";
       private static final String THRESHOLD = "Threshold";
       private static final String POLLDELAY = "PollDelay";
       private boolean done = false;

       private ThermalManager.PlatformInfo mPlatformInfo;
       private ThermalSensor mCurrSensor;
       private ThermalZone mCurrZone;
       private ArrayList<ThermalSensor> mCurrSensorList;
       private ArrayList<ThermalZone> mThermalZones;
       private ArrayList<Integer> mThresholdList;
       private ArrayList<Integer> mPollDelayList;
       XmlPullParserFactory mFactory;
       XmlPullParser mParser;
       int tempZoneId = -1;
       FileReader mInputStream = null;
       ThermalParser(String fname) {
          try {
               mFactory = XmlPullParserFactory.newInstance(System.getProperty(XmlPullParserFactory.PROPERTY_NAME), null);
               mFactory.setNamespaceAware(true);
               mParser = mFactory.newPullParser();
          } catch (SecurityException e) {
               Log.e(TAG, "SecurityException caught in ThermalParser");
          } catch (IllegalArgumentException e) {
               Log.e(TAG, "IllegalArgumentException caught in ThermalParser");
          } catch (XmlPullParserException xppe) {
               xppe.printStackTrace();
               Log.e(TAG, "XmlPullParserException caught in ThermalParser");
          }

          try {

               mInputStream = new FileReader(fname);
               mPlatformInfo = null;
               mCurrSensor = null;
               mCurrZone = null;
               mCurrSensorList = null;
               mThermalZones = null;
               if (mInputStream == null) return;
               if (mParser != null) {
                   mParser.setInput(mInputStream);
               }
          } catch (FileNotFoundException e) {
              Log.e(TAG, "FileNotFoundException Exception in ThermalParser()");
          } catch (XmlPullParserException e) {
              Log.e(TAG, "XmlPullParserException Exception in ThermalParser()");
          }
       }

       public ThermalManager.PlatformInfo getPlatformInfo() {
          return mPlatformInfo;
       }

       public ArrayList<ThermalZone> getThermalZoneList() {
          return mThermalZones;
       }

       public void parse() {
       if (mInputStream == null) return;
       /* if mParser is null, close any open stream before exiting */
       if (mParser == null) {
           try {
               mInputStream.close();
           } catch (IOException e) {
               Log.i(TAG,"IOException caught in parse() function");
           }
           return;
       }

       try {
               int mEventType = mParser.getEventType();
               while (mEventType != XmlPullParser.END_DOCUMENT && !done) {
                  switch (mEventType) {
                  case XmlPullParser.START_DOCUMENT:
                       Log.i(TAG, "StartDocument");
                       break;
                  case XmlPullParser.START_TAG:
                       processStartElement(mParser.getName());
                       break;
                  case XmlPullParser.END_TAG:
                       processEndElement(mParser.getName());
                       break;
                  }
                  mEventType = mParser.next();
               }
               // end of parsing, close the stream
               if (mInputStream != null) mInputStream.close();
          } catch (XmlPullParserException xppe) {
               xppe.printStackTrace();
          } catch (Exception e) {
               e.printStackTrace();
          }
       }

       void processStartElement(String name) {
          String zoneName;
          try {
               if (name.equalsIgnoreCase(PINFO)) {
                   mPlatformInfo = new ThermalManager.PlatformInfo();
               } else if (name.equalsIgnoreCase(SENSOR)) {
                   if (mCurrSensorList == null)
                      mCurrSensorList = new ArrayList<ThermalSensor>();
                      mCurrSensor = new ThermalSensor();
               } else if (name.equalsIgnoreCase(ZONE)) {
                   if (mThermalZones == null)
                       mThermalZones = new ArrayList<ThermalZone>();
               } else {
                   // Retrieve Platform Information
                   if (mPlatformInfo != null && name.equalsIgnoreCase("PlatformThermalStates"))
                       mPlatformInfo.mMaxThermalStates = Integer.parseInt(mParser.nextText());
                   // Retrieve Zone Information
                   else if (name.equalsIgnoreCase("ZoneName") && tempZoneId != -1) {
                        // if modem create a object of type modem and assign to base class
                        zoneName = mParser.nextText();
                        if (zoneName.contains("Modem")) {
                           mCurrZone = new ModemZone(mContext);// upcasting to base class
                        } else {
                           mCurrZone = new ThermalZone();
                        }
                        if (mCurrZone != null) {
                            mCurrZone.setZoneName(zoneName);
                            mCurrZone.setZoneId(tempZoneId);
                        }
                   } else if (name.equalsIgnoreCase("ZoneID"))
                       tempZoneId = Integer.parseInt(mParser.nextText());
                   else if (name.equalsIgnoreCase("SupportsUEvent") && mCurrZone != null)
                       mCurrZone.setSupportsUEvent(Integer.parseInt(mParser.nextText()));
                   else if (name.equalsIgnoreCase("SensorLogic") && mCurrZone != null)
                       mCurrZone.setSensorLogic(Integer.parseInt(mParser.nextText()));
                   else if (name.equalsIgnoreCase("DebounceInterval") && mCurrZone != null)
                       mCurrZone.setDBInterval(Integer.parseInt(mParser.nextText()));
                   else if (name.equalsIgnoreCase(POLLDELAY) && mCurrZone != null) {
                       mPollDelayList = new ArrayList<Integer>();
                   }
                   // Retrieve Sensor Information
                   else if (name.equalsIgnoreCase("SensorID") && mCurrSensor != null)
                       mCurrSensor.setSensorID(Integer.parseInt(mParser.nextText()));
                   else if (name.equalsIgnoreCase("SensorName") && mCurrSensor != null)
                       mCurrSensor.setSensorName(mParser.nextText());
                   else if (name.equalsIgnoreCase("SensorPath") && mCurrSensor != null)
                       mCurrSensor.setSensorPath(mParser.nextText());
                   else if (name.equalsIgnoreCase("InputTemp") && mCurrSensor != null)
                       mCurrSensor.setInputTempPath(mParser.nextText());
                   else if (name.equalsIgnoreCase("HighTemp") && mCurrSensor != null)
                       mCurrSensor.setHighTempPath(mParser.nextText());
                   else if (name.equalsIgnoreCase("LowTemp") && mCurrSensor != null)
                       mCurrSensor.setLowTempPath(mParser.nextText());
                   else if (name.equalsIgnoreCase("UEventDevPath") && mCurrSensor != null)
                       mCurrSensor.setUEventDevPath(mParser.nextText());
                   else if (name.equalsIgnoreCase(THRESHOLD) && mCurrSensor != null) {
                       mThresholdList = new ArrayList<Integer>();
                   }
                   // Poll delay info
                   else if (name.equalsIgnoreCase("DelayTOff") && mPollDelayList != null) {
                       mPollDelayList.add(Integer.parseInt(mParser.nextText()));
                   } else if (name.equalsIgnoreCase("DelayNormal") && mPollDelayList != null) {
                       mPollDelayList.add(Integer.parseInt(mParser.nextText()));
                   } else if (name.equalsIgnoreCase("DelayWarning") && mPollDelayList != null) {
                       mPollDelayList.add(Integer.parseInt(mParser.nextText()));
                   } else if (name.equalsIgnoreCase("DelayAlert") && mPollDelayList != null) {
                       mPollDelayList.add(Integer.parseInt(mParser.nextText()));
                   } else if (name.equalsIgnoreCase("DelayCritical") && mPollDelayList != null) {
                       mPollDelayList.add(Integer.parseInt(mParser.nextText()));
                   }
                   // Threshold info
                   else if (name.equalsIgnoreCase("ThresholdTOff") && mThresholdList != null) {
                       mThresholdList.add(Integer.parseInt(mParser.nextText()));
                   } else if (name.equalsIgnoreCase("ThresholdNormal") && mThresholdList != null) {
                       mThresholdList.add(Integer.parseInt(mParser.nextText()));
                   } else if (name.equalsIgnoreCase("ThresholdWarning") && mThresholdList != null) {
                       mThresholdList.add(Integer.parseInt(mParser.nextText()));
                   } else if (name.equalsIgnoreCase("ThresholdAlert") && mThresholdList != null) {
                       mThresholdList.add(Integer.parseInt(mParser.nextText()));
                   } else if (name.equalsIgnoreCase("ThresholdCritical") && mThresholdList != null) {
                       mThresholdList.add(Integer.parseInt(mParser.nextText()));
                   }
                }
           } catch (Exception e) {
               e.printStackTrace();
           }
       }

       void processEndElement(String name) {
         if (name.equalsIgnoreCase(SENSOR) &&
            mCurrSensorList != null) {
            mCurrSensorList.add(mCurrSensor);
            mCurrSensor = null;
         } else if (name.equalsIgnoreCase(ZONE) &&
            mCurrZone != null &&
            mThermalZones != null) {
            mCurrZone.setSensorList(mCurrSensorList);
            mThermalZones.add(mCurrZone);
            mCurrSensorList = null;
            mCurrZone = null;
            tempZoneId = -1;
         } else if (name.equalsIgnoreCase(THRESHOLD) &&
           (mCurrSensor != null)) {
            mCurrSensor.setThermalThresholds(mThresholdList);
            mThresholdList = null;
         } else if (name.equalsIgnoreCase(POLLDELAY) &&
            (mCurrZone != null)) {
            mCurrZone.setPollDelay(mPollDelayList);
            mPollDelayList = null;
         } else if (name.equalsIgnoreCase(MIDTHERMAL)) {
            Log.i(TAG, "Parsing Finished..");
            done = true;
         }
       }
    }

    /* Class to notifying thermal events */
    public class Notify implements Runnable {
        private final BlockingQueue cQueue;
        Notify (BlockingQueue q) {
             cQueue = q;
        }

        public void run () {
           try {
                while (true) { consume((ThermalEvent) cQueue.take()); }
            } catch (InterruptedException ex) {
                Log.i(TAG, "caught InterruptedException in run()");
              }
        }

        /* method to consume thermal event */
        public void consume (ThermalEvent event) {
            /* Create the INTENT */
            Intent statusIntent = new Intent();
            statusIntent.setAction(Intent.ACTION_THERMAL_ZONE_STATE_CHANGED);

            /* Determine which THERMAL ZONE state changed
               and pack the INTENT appropriately and send */
            statusIntent.putExtra(ThermalManager.EXTRA_NAME, event.zoneName);
            statusIntent.putExtra(ThermalManager.EXTRA_ZONE, event.zoneID);
            statusIntent.putExtra(ThermalManager.EXTRA_EVENT, event.eventType);
            statusIntent.putExtra(ThermalManager.EXTRA_STATE, event.thermalLevel);
            statusIntent.putExtra(ThermalManager.EXTRA_TEMP, event.zoneTemp);

            /* Send the INTENT */
            mContext.sendBroadcast(statusIntent);
        }
    }

    /* Register for boot complete Intent */
    public ThermalService(Context context) {
        super();

        Log.i(TAG, "Initializing Thermal Manager Service");

        mContext = context;
        mCoolingManager = new ThermalCooling();

        // Wait for the BOOT Completion
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        mContext.registerReceiver(new BootCompReceiver(), filter);
    }

    /* Handler to boot complete intent */
    private final class BootCompReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.i(TAG, "Received Boot Complete message");

            if (!ThermalManager.configFilesExist()) {
                Log.i(TAG, "Thermal config files dont exist, exiting Thermal service...");
            }

            /* Start and Initialize the Thermal Cooling Manager */
            if (mCoolingManager != null) {
                if (!mCoolingManager.init(mContext)) return;
            }

            /* Parse the thermal configuration file to determine
               sensor information/zone information */
            ThermalParser mThermalParser = new ThermalParser(ThermalManager.SENSOR_FILE_PATH);
            try {
                 mThermalParser.parse();
            } catch (Exception e) {
                 Log.i(TAG, "ThermalManagement XML Parsing Failed");
                 return;
            }

            /* Retrieve the platform information after parsing */
            ThermalManager.mPlatformInfo = mThermalParser.getPlatformInfo();
            ThermalManager.mPlatformInfo.printAttrs();

            /* Retrieve the Zone list after parsing */
            ThermalManager.mThermalZonesList = mThermalParser.getThermalZoneList();

            /* print the parsed information */
            for (ThermalZone tz : ThermalManager.mThermalZonesList) {
                tz.printAttrs();
            }

            /* builds a map of active sensors */
            ThermalManager.buildSensorMap();

            /* initialize the thermal notifier thread */
            Notify notifier = new Notify(ThermalServiceEventQueue.eventQueue);
            new Thread(notifier, "ThermalNotifier").start();

            /* start monitoring the thermal zones */
            ThermalManager.startMonitoringZones();
        }
    }
}
