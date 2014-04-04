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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
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
import android.os.SystemProperties;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.ClassLoader;
import java.lang.NullPointerException;
import java.lang.reflect.Array;
import java.lang.SecurityException;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * The ThermalService monitors the Thermal zones on the platform.
 * The number of thermal zones and sensors associated with the zones are
 * obtained from the thermal_sensor_config.xml file. When any thermal zone
 * crosses the thresholds configured in the xml, a Thermal Intent is sent.
 * ACTION_THERMAL_ZONE_STATE_CHANGED
 * The Thermal Cooling Manager acts upon this intent and throttles
 * the corresponding cooling device.
 *
 * @hide
 */
public class ThermalService extends Binder {
    private static final String TAG = ThermalService.class.getSimpleName();
    private Context mContext;
    private Handler mHandler = new Handler();
    private ThermalCooling mCoolingManager;

    public class ThermalParser {
        // Names of the XML Tags
        private static final String PINFO = "PlatformInfo";
        private static final String SENSOR_ATTRIB = "SensorAttrib";
        private static final String SENSOR = "Sensor";
        private static final String ZONE = "Zone";
        private static final String THERMAL_CONFIG = "thermalconfig";
        private static final String THRESHOLD = "Threshold";
        private static final String POLLDELAY = "PollDelay";
        private static final String MOVINGAVGWINDOW = "MovingAverageWindow";
        private static final String ZONELOGIC = "ZoneLogic";
        private static final String WEIGHT = "Weight";
        private static final String ORDER = "Order";
        private static final String OFFSET = "Offset";
        private static final String ZONETHRESHOLD = "ZoneThreshold";
        private boolean mDone = false;
        private ThermalManager.PlatformInfo mPlatformInfo = null;
        private ThermalSensor mCurrSensor = null;
        private ThermalZone mCurrZone = null;
        private ArrayList<ThermalSensorAttrib> mCurrSensorAttribList = null;
        private ThermalSensorAttrib mCurrSensorAttrib = null;
        private ArrayList<ThermalZone> mThermalZones = null;
        private ArrayList<Integer> mPollDelayList = null;
        private ArrayList<Integer> mMovingAvgWindowList = null;
        private ArrayList<Integer> mWeightList = null;
        private ArrayList<Integer> mOrderList = null;
        private ArrayList<Integer> mZoneThresholdList = null;
        private String mSensorName = null;
        XmlPullParserFactory mFactory = null;
        XmlPullParser mParser = null;
        int mTempZoneId = -1;
        String mTempZoneName = null;
        FileReader mInputStream = null;
        ThermalParser(String fname) {
            try {
                mFactory = XmlPullParserFactory.newInstance(System.
                        getProperty(XmlPullParserFactory.PROPERTY_NAME), null);
                mFactory.setNamespaceAware(true);
                mParser = mFactory.newPullParser();
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException caught in ThermalParser");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "IllegalArgumentException caught in ThermalParser");
            } catch (XmlPullParserException xppe) {
                Log.e(TAG, "XmlPullParserException caught in ThermalParser");
            }

            try {
                mInputStream = new FileReader(fname);
                mPlatformInfo = null;
                mCurrSensor = null;
                mCurrZone = null;
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

        ThermalParser() {
            mParser = mContext.getResources().
                    getXml(ThermalManager.THERMAL_SENSOR_CONFIG_XML_ID);
        }

        public ThermalManager.PlatformInfo getPlatformInfo() {
            return mPlatformInfo;
        }

        public ArrayList<ThermalZone> getThermalZoneList() {
            return mThermalZones;
        }

        public boolean parse() {
            if (ThermalManager.sIsOverlays == false && mInputStream == null) return false;
            /* if mParser is null, close any open stream before exiting */
            if (mParser == null) {
                try {
                    if (mInputStream != null) {
                        mInputStream.close();
                    }
                } catch (IOException e) {
                    Log.i(TAG, "IOException caught in parse() function");
                }
                return false;
            }

            boolean ret = true;
            try {
                int mEventType = mParser.getEventType();
                while (mEventType != XmlPullParser.END_DOCUMENT && !mDone) {
                    switch (mEventType) {
                        case XmlPullParser.START_DOCUMENT:
                            Log.i(TAG, "StartDocument");
                            break;
                        case XmlPullParser.START_TAG:
                            if (!processStartElement(mParser.getName())) {
                                if (mInputStream != null) mInputStream.close();
                                return false;
                            }
                            break;
                        case XmlPullParser.END_TAG:
                            processEndElement(mParser.getName());
                            break;
                    }
                    mEventType = mParser.next();
                }
            } catch (XmlPullParserException xppe) {
                Log.i(TAG, "XmlPullParserException caught in parse() function");
                ret = false;
            } catch (IOException e) {
                Log.i(TAG, "IOException caught in parse() function");
                ret = false;
            } finally {
                try {
                    // end of parsing, close the stream
                    // close is moved here, since if there is an exception
                    // while parsing doc, input stream needs to be closed
                    if (mInputStream != null) mInputStream.close();
                } catch (IOException e) {
                    Log.i(TAG, "IOException caught in parse() function");
                    ret = false;
                }
                return ret;
            }
        }

        boolean processStartElement(String name) {
            if (name == null)
                return false;
            String zoneName;
            boolean ret = true;
            try {
                if (name.equalsIgnoreCase(PINFO)) {
                    mPlatformInfo = new ThermalManager.PlatformInfo();
                } else if (name.equalsIgnoreCase(SENSOR)) {
                    if (mCurrSensor == null) {
                        mCurrSensor = new ThermalSensor();
                    }
                } else if (name.equalsIgnoreCase(SENSOR_ATTRIB)) {
                    if (mCurrSensorAttribList == null) {
                        mCurrSensorAttribList = new ArrayList<ThermalSensorAttrib>();
                    }
                    mCurrSensorAttrib = new ThermalSensorAttrib();
                } else if (name.equalsIgnoreCase(ZONE)) {
                    if (mThermalZones == null)
                        mThermalZones = new ArrayList<ThermalZone>();
                } else {
                    // Retrieve Platform Information
                    if (mPlatformInfo != null && name.equalsIgnoreCase("PlatformThermalStates")) {
                        mPlatformInfo.mMaxThermalStates = Integer.parseInt(mParser.nextText());
                        // Retrieve Zone Information
                    } else if (name.equalsIgnoreCase("ZoneName") && mTempZoneId != -1) {
                        // if modem create a object of type modem and assign to base class
                        mTempZoneName = mParser.nextText();
                    } else if (name.equalsIgnoreCase(ZONELOGIC) && mTempZoneId != -1
                            && mTempZoneName != null) {
                        String zoneLogic = mParser.nextText();
                        if (zoneLogic.equalsIgnoreCase("VirtualSkin")) {
                            mCurrZone = new VirtualThermalZone();
                        } else if (zoneLogic.equalsIgnoreCase("Modem")) {
                            mCurrZone = new ModemZone(mContext);
                        } else {
                            // default zone raw
                            mCurrZone = new RawThermalZone();
                        }
                        if (mCurrZone != null) {
                            mCurrZone.setZoneName(mTempZoneName);
                            mCurrZone.setZoneId(mTempZoneId);
                            mCurrZone.setZoneLogic(zoneLogic);
                        }
                    } else if (name.equalsIgnoreCase("ZoneID")) {
                        mTempZoneId = Integer.parseInt(mParser.nextText());
                    } else if (name.equalsIgnoreCase("SupportsUEvent") && mCurrZone != null)
                        mCurrZone.setSupportsUEvent(Integer.parseInt(mParser.nextText()));
                    else if (name.equalsIgnoreCase("SupportsEmulTemp") && mCurrZone != null)
                        mCurrZone.setEmulTempFlag(Integer.parseInt(mParser.nextText()));
                    else if (name.equalsIgnoreCase("DebounceInterval") && mCurrZone != null)
                        mCurrZone.setDBInterval(Integer.parseInt(mParser.nextText()));
                    else if (name.equalsIgnoreCase(POLLDELAY) && mCurrZone != null) {
                        mPollDelayList = new ArrayList<Integer>();
                    } else if (name.equalsIgnoreCase(OFFSET) && mCurrZone != null) {
                        mCurrZone.setOffset(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase(ZONETHRESHOLD) && mCurrZone != null) {
                        mZoneThresholdList = new ArrayList<Integer>();
                    } else if (name.equalsIgnoreCase("ZoneThresholdTOff")
                            && mZoneThresholdList != null) {
                        mZoneThresholdList.add(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("ZoneThresholdNormal")
                            && mZoneThresholdList != null) {
                        mZoneThresholdList.add(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("ZoneThresholdWarning")
                            && mZoneThresholdList != null) {
                        mZoneThresholdList.add(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("ZoneThresholdAlert")
                            && mZoneThresholdList != null) {
                        mZoneThresholdList.add(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("ZoneThresholdCritical")
                            && mZoneThresholdList != null) {
                        mZoneThresholdList.add(Integer.parseInt(mParser.nextText()));
                    }

                    // Retrieve Sensor Information
                    else if (name.equalsIgnoreCase("SensorName")) {
                        if (mCurrSensorAttrib != null) {
                            mCurrSensorAttrib.setSensorName(mParser.nextText());
                        } else if (mCurrSensor != null) {
                            mCurrSensor.setSensorName(mParser.nextText());
                        }
                    } else if (name.equalsIgnoreCase("SensorPath") && mCurrSensor != null)
                        mCurrSensor.setSensorPath(mParser.nextText());
                    else if (name.equalsIgnoreCase("InputTemp") && mCurrSensor != null)
                        mCurrSensor.setInputTempPath(mParser.nextText());
                    else if (name.equalsIgnoreCase("HighTemp") && mCurrSensor != null)
                        mCurrSensor.setHighTempPath(mParser.nextText());
                    else if (name.equalsIgnoreCase("LowTemp") && mCurrSensor != null)
                        mCurrSensor.setLowTempPath(mParser.nextText());
                    else if (name.equalsIgnoreCase("UEventDevPath") && mCurrSensor != null)
                        mCurrSensor.setUEventDevPath(mParser.nextText());
                    else if (name.equalsIgnoreCase("ErrorCorrection") && mCurrSensor != null)
                        mCurrSensor.setErrorCorrectionTemp(Integer.parseInt(mParser.nextText()));
                    else if (name.equalsIgnoreCase(WEIGHT) && mCurrSensorAttrib != null) {
                        if (mWeightList == null) {
                            mWeightList = new ArrayList<Integer>();
                        }
                        if (mWeightList != null) {
                            mWeightList.add(Integer.parseInt(mParser.nextText()));
                        }
                    } else if (name.equalsIgnoreCase(ORDER) && mCurrSensorAttrib != null) {
                        if (mOrderList == null) {
                            mOrderList = new ArrayList<Integer>();
                        }
                        if (mOrderList != null) {
                            mOrderList.add(Integer.parseInt(mParser.nextText()));
                        }
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
                    // Moving Average window
                    else if (name.equalsIgnoreCase(MOVINGAVGWINDOW)
                            && mCurrZone != null) {
                        mMovingAvgWindowList = new ArrayList<Integer>();
                    } else if (name.equalsIgnoreCase("WindowTOff")
                            && mMovingAvgWindowList != null) {
                        mMovingAvgWindowList.add(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("WindowNormal")
                            && mMovingAvgWindowList != null) {
                        mMovingAvgWindowList.add(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("WindowWarning")
                            && mMovingAvgWindowList != null) {
                        mMovingAvgWindowList.add(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("WindowAlert")
                            && mMovingAvgWindowList != null) {
                        mMovingAvgWindowList.add(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("WindowCritical")
                            && mMovingAvgWindowList != null) {
                        mMovingAvgWindowList.add(Integer.parseInt(mParser.nextText()));
                    }
                }
            } catch (XmlPullParserException e) {
                Log.i(TAG, "XmlPullParserException caught in processStartElement()");
                ret = false;
            } catch (IOException e) {
                Log.i(TAG, "IOException caught in processStartElement()");
                ret = false;
            } finally {
                return ret;
            }
        }

        void processEndElement(String name) {
            if (name.equalsIgnoreCase(SENSOR)) {
                // insert in map, only if no sensor with same name already in map
                if (mCurrSensor == null) return;
                if (ThermalManager.getSensor(mCurrSensor.getSensorName()) == null) {
                    ThermalManager.sSensorMap.put(mCurrSensor.getSensorName(), mCurrSensor);
                    mCurrSensor.printAttrs();
                } else {
                    Log.i(TAG, "sensor:" + mCurrSensor.getSensorName() + " already present");
                }
                mCurrSensor = null;
            } else if (name.equalsIgnoreCase(SENSOR_ATTRIB) && mCurrSensorAttribList != null) {
                if (mCurrSensorAttrib != null) {
                    mCurrSensorAttrib.setWeights(mWeightList);
                    mCurrSensorAttrib.setOrder(mOrderList);
                }
                mWeightList = null;
                mOrderList = null;
                if (mCurrSensorAttrib != null
                        && ThermalManager.getSensor(mCurrSensorAttrib.getSensorName()) != null) {
                    // this is valid sensor, so now update the zone sensorattrib list
                    // and sensor list.This check is needed to avoid a scenario where
                    // a invalid sensor name might be included in sensorattrib list.
                    // This check filters out all invalid sensor attrib.
                    mCurrSensorAttribList.add(mCurrSensorAttrib);
                }
            } else if (name.equalsIgnoreCase(ZONE) && mCurrZone != null
                    && mThermalZones != null) {
                mCurrZone.setSensorList(mCurrSensorAttribList);
                // check to see if zone is active
                mCurrZone.computeZoneActiveStatus();
                mCurrZone.startEmulTempObserver();
                mThermalZones.add(mCurrZone);
                mCurrZone = null;
                mTempZoneId = -1;
                mTempZoneName = null;
                mCurrSensorAttribList = null;
            } else if (name.equalsIgnoreCase(POLLDELAY) && mCurrZone != null) {
                mCurrZone.setPollDelay(mPollDelayList);
                mPollDelayList = null;
            } else if (name.equalsIgnoreCase(MOVINGAVGWINDOW) && mCurrZone != null) {
                mCurrZone.setMovingAvgWindow(mMovingAvgWindowList);
                mMovingAvgWindowList = null;
            } else if (name.equalsIgnoreCase(THERMAL_CONFIG)) {
                Log.i(TAG, "Parsing Finished..");
                mDone = true;
            } else if (name.equalsIgnoreCase(ZONETHRESHOLD) && mCurrZone != null) {
                mCurrZone.setZoneTempThreshold(mZoneThresholdList);
                mZoneThresholdList = null;
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

        /* Method to consume thermal event */
        public void consume (ThermalEvent event) {
            Intent statusIntent = new Intent();
            statusIntent.setAction(ThermalManager.ACTION_THERMAL_ZONE_STATE_CHANGED);

            statusIntent.putExtra(ThermalManager.EXTRA_NAME, event.zoneName);
            statusIntent.putExtra(ThermalManager.EXTRA_ZONE, event.zoneID);
            statusIntent.putExtra(ThermalManager.EXTRA_EVENT, event.eventType);
            statusIntent.putExtra(ThermalManager.EXTRA_STATE, event.thermalLevel);
            statusIntent.putExtra(ThermalManager.EXTRA_TEMP, event.zoneTemp);

            /* Send the Thermal Intent */
            mContext.sendBroadcast(statusIntent);
        }
    }

    /* Register for boot complete Intent */
    public ThermalService(Context context) {
        super();

        Log.i(TAG, "Initializing Thermal Manager Service");
        mContext = context;
        mCoolingManager = new ThermalCooling();

        /* Wait for the BOOT Completion */
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        mContext.registerReceiver(new BootCompReceiver(), filter);
    }

    private void configureTurboProperties() {
        String prop = SystemProperties.get("persist.thermal.turbo.dynamic");

        if (prop.equals("0")) {
            ThermalManager.sIsDynamicTurboEnabled = false;
            Log.i(TAG, "Dynamic Turbo disabled through persist.thermal.turbo.dynamic");
        } else if (prop.equals("1")) {
            ThermalManager.sIsDynamicTurboEnabled = true;
            Log.i(TAG, "Dynamic Turbo enabled through persist.thermal.turbo.dynamic");
        } else {
            // Set it to true so that we don't write ThermalManager.DISABLE_DYNAMIC_TURBO
            // into any cooling device based on this.
            ThermalManager.sIsDynamicTurboEnabled = true;
            Log.i(TAG, "property persist.thermal.turbo.dynamic not present");
        }
    }

    /* Handler to boot complete intent */
    private final class BootCompReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            ThermalManager.loadiTUXVersion();
            if (!ThermalUtils.configFilesExist(mContext)) {
                Log.i(TAG, "Thermal config files do not exist. Exiting ThermalService");
                return;
            }

            /* Set Dynamic Turbo status based on the property */
            configureTurboProperties();

            /* Start and Initialize the Thermal Cooling Manager */
            if (mCoolingManager == null) {
                Log.i(TAG, "mCoolingManager is null. Exiting ThermalService");
                return;
            }

            if (!mCoolingManager.init(mContext)) {
                Log.i(TAG, "mCoolingManager.init() failed. Exiting ThermalService");
                return;
            }

            /* Parse the thermal configuration file to determine zone/sensor information */
            ThermalParser mThermalParser;
            if (!ThermalManager.sIsOverlays) {
                mThermalParser = new ThermalParser(ThermalManager.SENSOR_FILE_PATH);
            } else {
                mThermalParser = new ThermalParser();
            }
            if (mThermalParser == null) {
                Log.i(TAG, "ThermalParser creation failed. Exiting ThermalService");
                return;
            }

            if (!mThermalParser.parse()) {
                mCoolingManager.unregisterReceivers();
                ThermalManager.unregisterZoneReceivers();
                Log.i(TAG, "ThermalManagement XML Parsing Failed. Exiting ThermalService");
                return;
            }

            /* Retrieve the platform information after parsing */
            ThermalManager.sPlatformInfo = mThermalParser.getPlatformInfo();
            ThermalManager.sPlatformInfo.printAttrs();

            /* Retrieve the Zone list after parsing */
            ThermalManager.sThermalZonesList = mThermalParser.getThermalZoneList();

            /* print the parsed information */
            for (ThermalZone tz : ThermalManager.sThermalZonesList) {
                tz.printAttrs();
            }

            /* if no active zone, just exit Thermal manager Service */
            if (ThermalManager.isThermalServiceNeeded() == false) {
                Log.i(TAG, "No active thermal zones. Exiting ThermalService");
                mCoolingManager.unregisterReceivers();
                ThermalManager.unregisterZoneReceivers();
                return;
            }

            /* initialize zone critical pending map */
            ThermalManager.initializeZoneCriticalPendingMap();
            /* read persistent system properties for shutdown notification */
            ThermalManager.readShutdownNotiferProperties();

            /* initialize the thermal notifier thread */
            Notify notifier = new Notify(ThermalManager.sEventQueue);
            new Thread(notifier, "ThermalNotifier").start();

            /* start monitoring the thermal zones */
            ThermalManager.startMonitoringZones();
        }
    }
}
