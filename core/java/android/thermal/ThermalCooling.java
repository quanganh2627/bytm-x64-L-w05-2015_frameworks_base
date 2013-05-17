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

import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.regex.Pattern;
import android.util.Log;
import android.content.Context;
import java.lang.reflect.Array;
import java.lang.ClassLoader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlPullParserException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import android.thermal.ThermalZone;
import android.thermal.SysfsManager;
import android.thermal.ThermalCoolingDevice;
import android.thermal.ThermalManager;
import android.content.ContentResolver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
// Telephony intent
import com.android.internal.telephony.TelephonyIntents;

/**
 * The ThermalCooling class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED} Intent.
 *@hide
 */
public class ThermalCooling {
    public static final String TAG = "ThermalCooling";
    private static final String THERMAL_SHUTDOWN_NOTIFY_PATH = "/sys/module/intel_mid_osip/parameters/force_shutdown_occured";
    private Context mContext;
    //Brightness related information
    private static int sMaxBrightness;
    private static boolean sIsBrightnessThrottled = false;
    private static final int sAlertBrightnessRatio = 50;
    private static final int sDefaultBrightness = 102;
    private static final Object sBrightnessLock = new Object();
    // Emergency call related info
    private static boolean sOnGoingEmergencyCall = false;
    // count to keep track of zones in critical state, waiting for shutdown
    private int mCriticalZonesCount = 0;
    private static final Object sCriticalZonesCountLock = new Object();



    /**
     * this is the basic parser class which parses the
     * thermal_throttle_config.xml file.
     */
    public class ThermalParser {
       private static final String MIDTHERMAL = "thermalthrottleconfig";
       private static final String CDEVINFO = "ContributingDeviceInfo";
       private static final String ZONETHROTINFO = "ZoneThrottleInfo";
       private static final String COOLINGDEVICEINFO = "CoolingDeviceInfo";
       private static final String THROTTLEMASK = "ThrottleDeviceMask";
       private static final String DETHROTTLEMASK = "DethrottleDeviceMask";
       private ArrayList<Integer> mTempMaskList;
       private boolean isThrottleMask = false;
       private boolean done = false;

       XmlPullParserFactory mFactory;
       XmlPullParser mParser;
       ThermalCoolingDevice mDevice;
       ThermalManager.ZoneCoolerBindingInfo mZone;
       FileReader mInputStream = null;

       ThermalParser(String fname) {
          try {
               mFactory = XmlPullParserFactory.newInstance(System.getProperty(XmlPullParserFactory.PROPERTY_NAME), null);
               mFactory.setNamespaceAware(true);
               mParser = mFactory.newPullParser();
          } catch (XmlPullParserException xppe) {
            Log.e(TAG, "mParser NewInstance Exception");
          }

          try {
               mInputStream = new FileReader(fname);
               if (mInputStream == null) return;
               if (mParser != null) {
                   mParser.setInput(mInputStream);
               }
               mDevice = null;
               mZone = null;
          } catch (XmlPullParserException xppe) {
               Log.e(TAG, "mParser setInput XmlPullParserException");
          } catch (FileNotFoundException e) {
               Log.e(TAG, "mParser setInput FileNotFoundException");
          }

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
               // end of parsing close the input stream
               if (mInputStream != null) mInputStream.close();
          } catch (XmlPullParserException xppe) {
               Log.i(TAG, "XmlPullParserException caught in parse() function");
          } catch (IOException e) {
                Log.i(TAG,"IOException caught in parse() function");
          }
       }

       void processStartElement(String name) {
           if (name == null) return;
           try {
                if (name.equalsIgnoreCase(CDEVINFO)) {
                    if (mDevice == null)
                        mDevice = new ThermalCoolingDevice();
                } else if (name.equalsIgnoreCase(ZONETHROTINFO)) {
                    if (mZone == null)
                        mZone = new ThermalManager.ZoneCoolerBindingInfo();
                } else if (name.equalsIgnoreCase(COOLINGDEVICEINFO) && mZone != null) {
                        if (mZone.getCoolingDeviceInfoList() == null) {
                            mZone.initializeCoolingDeviceInfoList();
                        }
                        mZone.createNewCoolingDeviceInstance();
                } else {
                    // Retrieve zone and contirbuting device mapping
                    if (name.equalsIgnoreCase("ZoneID") && mZone != null) {
                        mZone.setZoneID(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("CriticalShutDown") && mZone != null) {
                        mZone.setCriticalActionShutdown(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase(THROTTLEMASK) && mZone != null) {
                        mTempMaskList = new ArrayList<Integer>();
                        isThrottleMask = true;
                    } else if (name.equalsIgnoreCase(DETHROTTLEMASK) && mZone != null) {
                        mTempMaskList = new ArrayList<Integer>();
                        isThrottleMask = false;
                    } else if (name.equalsIgnoreCase("CoolingDevId") && mZone != null) {
                        mZone.getLastCoolingDeviceInstance().setCoolingDeviceId(Integer.parseInt(mParser.nextText()));
                    }
                    //device mask
                    else if (name.equalsIgnoreCase("Normal") && mTempMaskList != null) {
                        mTempMaskList.add(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("Warning") && mTempMaskList != null) {
                        mTempMaskList.add(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("Alert") && mTempMaskList != null) {
                        mTempMaskList.add(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("Critical") && mTempMaskList != null) {
                        mTempMaskList.add(Integer.parseInt(mParser.nextText()));
                    }

                    // Retrieve contributing device information
                    if (name.equalsIgnoreCase("CDeviceName") && mDevice != null) {
                        mDevice.setDeviceName(mParser.nextText());
                    } else if (name.equalsIgnoreCase("CDeviceID") && mDevice != null) {
                        mDevice.setDeviceId(Integer.parseInt(mParser.nextText()));
                    } else if (name.equalsIgnoreCase("CDeviceClassPath") && mDevice != null) {
                        mDevice.setClassPath(mParser.nextText());
                    } else if (name.equalsIgnoreCase("CDeviceThrottlePath") && mDevice != null) {
                        mDevice.setThrottlePath(mParser.nextText());
                    }
                }
           } catch (XmlPullParserException e) {
                Log.i(TAG,"XmlPullParserException caught in processStartElement():");
           } catch (IOException e) {
                Log.i(TAG,"IOException caught in processStartElement():");
           }
       }

       void processEndElement(String name) {
           if (name == null) return;
           if (name.equalsIgnoreCase(CDEVINFO) && mDevice != null) {
                if (loadCoolingDevice(mDevice)) {
                    ThermalManager.listOfCoolers.put(mDevice.getDeviceId(),mDevice);
                }
                mDevice = null;
           } else if (name.equalsIgnoreCase(ZONETHROTINFO) && mZone != null) {
                ThermalManager.listOfZones.put(mZone.getZoneID(),mZone);
                mZone = null;
           } else if ((name.equalsIgnoreCase(THROTTLEMASK) ||
                     name.equalsIgnoreCase(DETHROTTLEMASK)) && mZone != null) {
                if (isThrottleMask) {
                    mZone.getLastCoolingDeviceInstance().setThrottleMaskList(mTempMaskList);
                } else {
                    mZone.getLastCoolingDeviceInstance().setDeThrottleMaskList(mTempMaskList);
                }
                isThrottleMask = false;
                mTempMaskList = null;
           } else if (name.equalsIgnoreCase(MIDTHERMAL)) {
                Log.i(TAG, "Parsing Finished..");
                done = true;
           } else if (name.equalsIgnoreCase(COOLINGDEVICEINFO) && mZone != null) {
                mZone.addCoolingDeviceToList(mZone.getLastCoolingDeviceInstance());
           }
       }
    }

    public boolean init(Context context) {
       Log.i(TAG, "Thermal Cooling manager init() called");

       ThermalParser parser = new ThermalParser(ThermalManager.THROTTLE_FILE_PATH);
       if (parser == null) return false;
       parser.parse();

       mContext = context;
       // Register for thermal zone state changed notifications
       IntentFilter filter = new IntentFilter();
       filter.addAction(Intent.ACTION_THERMAL_ZONE_STATE_CHANGED);
       mContext.registerReceiver(new ThermalZoneReceiver(), filter);

       // register for ongoing emergency call intent
       IntentFilter emergencyIntentFilter = new IntentFilter();
       emergencyIntentFilter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALL_STATUS_CHANGED);
       mContext.registerReceiver(new EmergencyCallReceiver(), emergencyIntentFilter);
       return true;
    }

    private final class EmergencyCallReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_EMERGENCY_CALL_STATUS_CHANGED)) {
                boolean callStatus = intent.getBooleanExtra("emergencyCallOngoing", false);
                Log.i(TAG, "emergency call intent received, callStatus = " + callStatus);
                updateCallStatus(callStatus);
                // if emergency call has ended, check if any zone is in critical state
                // if true, initiate shutdown
                if (callStatus == false) {
                    checkShutdownCondition();
                }
            }
        }
    }

    private static synchronized void updateCallStatus(boolean flag) {
        sOnGoingEmergencyCall = flag;
    }

    public static synchronized boolean isEmergencyCallOnGoing() {
        return sOnGoingEmergencyCall;
    }

    private void checkShutdownCondition() {
        synchronized (sCriticalZonesCountLock) {
            if (mCriticalZonesCount > 0) {
                Log.i(TAG, "checkShutdownCondition(): criticalZonesCount : " + mCriticalZonesCount + " shuting down...");
                doShutdown();
            }
        }
    }

    private void incrementCrticalZoneCount() {
        synchronized(sCriticalZonesCountLock) {
            mCriticalZonesCount++;
        }
    }

    private final class ThermalZoneReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Retrieve the type of THERMAL ZONE, STATE and TYPE
            String zoneName = intent.getStringExtra(ThermalManager.EXTRA_NAME);
            int thermZone = intent.getIntExtra(ThermalManager.EXTRA_ZONE, 0);
            int thermState = intent.getIntExtra(ThermalManager.EXTRA_STATE, 0);
            int thermEvent = intent.getIntExtra(ThermalManager.EXTRA_EVENT, 0);
            int zoneTemp = intent.getIntExtra(ThermalManager.EXTRA_TEMP, 0);
            Log.i(TAG, "Received THERMAL INTENT:" +
            " of event type " + thermEvent + " with state " + thermState + " at temperature " + zoneTemp +
            " from " + zoneName + " with state " + ThermalZone.getStateAsString(thermState) +
            " for " + ThermalZone.getEventTypeAsString(thermEvent) + " event" +
            " at Temperature " + zoneTemp);
            if ((thermState == ThermalManager.THERMAL_STATE_CRITICAL) &&
                (initiateShutdown(thermZone))) {
                if (!isEmergencyCallOnGoing()) {
                    doShutdown();
                } else {
                    // increment the count of zones in critical state pending on shutdown
                    incrementCrticalZoneCount();
                }
            }
            /* if THERMALOFF is the zone state, it is gauranteed that the zone has transistioned
            from a higher state, due to a low event, to THERMALOFF.Hence take dethrottling action
            corrosponding to NORMAL */
            if (thermState == ThermalManager.THERMAL_STATE_OFF) thermState = ThermalManager.THERMAL_STATE_NORMAL;
            handleThermalEvent(thermZone, thermEvent, thermState);
        }
    }

    private boolean loadCoolingDevice(ThermalCoolingDevice device) {
        Class cls;
        Method throttleMethod;

        if (device.getClassPath() == null) {
           Log.i(TAG, "ClassPath not found");
           return false;
        }
        /* Load the contributing device class */
        try {
            cls = Class.forName(device.getClassPath());
            device.setDeviceClass(cls);
        } catch (Throwable e) {
            Log.i(TAG, "Unable to load class " + device.getClassPath());
            return false;
        }

        /* Initialize the contributing device class */
        try {
            Class partypes[] = new Class[1];
            partypes[0] = String.class;
            Method init = cls.getMethod("init", partypes);
            Object arglist[] = new Object[1];
            arglist[0] = device.getThrottlePath();
            init.invoke(cls, arglist);
        } catch (NoSuchMethodException e) {
            Log.i(TAG, "NoSuchMethodException caught in device class init: " + device.getClassPath());
        } catch (SecurityException e) {
            Log.i(TAG, "SecurityException caught in device class init: " + device.getClassPath());
        } catch (IllegalAccessException e) {
            Log.i(TAG, "IllegalAccessException caught in device class init: " + device.getClassPath());
        } catch (IllegalArgumentException e) {
            Log.i(TAG,"IllegalArgumentException caught in device class init: " + device.getClassPath());
        } catch (ExceptionInInitializerError e) {
            Log.i(TAG, "ExceptionInInitializerError caught in device class init: " + device.getClassPath());
        } catch (InvocationTargetException e) {
            Log.i(TAG, "InvocationTargetException caught in device class init: " + device.getClassPath());
        }

        /* Get the throttle from contributing device class */
        try {
            Class partypes[] = new Class[1];
            partypes[0] = Integer.TYPE;
            throttleMethod = cls.getMethod("throttleDevice", partypes);
            device.setThrottleMethod(throttleMethod);
        } catch (NoSuchMethodException e) {
            Log.i(TAG, "NoSuchMethodException caught initializing throttle funciton ");
        } catch (SecurityException e) {
            Log.i(TAG, "SecurityException caught initializing throttle funciton ");
        }

        return true;
    }

    // Method to do actual shutdown. It writes a 1 in OSIP Sysfs and
    // sends the shutdown intent
    private void doShutdown() {
        /* sending shutdown INTENT */
        Intent statusIntent = new Intent();
        statusIntent.setAction(Intent.ACTION_THERMAL_SHUTDOWN);
        mContext.sendBroadcast(statusIntent);

        SysfsManager.writeSysfs(THERMAL_SHUTDOWN_NOTIFY_PATH, 1);
        Intent criticalIntent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
        criticalIntent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
        criticalIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(criticalIntent);
    }
    /* Method to handle the thermal event based on HIGH or LOW event*/
    public static boolean initiateShutdown(int zoneID) {
         ThermalManager.ZoneCoolerBindingInfo zone = ThermalManager.listOfZones.get(zoneID);

         if (zone == null) return false;

         return (zone.getCriticalActionShutdown() == 1);
    }

    /* Method to handle the thermal event based on HIGH or LOW event*/
    public static void handleThermalEvent(int zoneId, int eventType, int thermalState) {
         ThermalCoolingDevice tDevice;
         int deviceId;
         int existingState, targetState;
         int currThrottleMask, currDethrottleMask;

         ThermalManager.ZoneCoolerBindingInfo zoneCoolerBindInfo = ThermalManager.listOfZones.get(zoneId);
         if (zoneCoolerBindInfo == null) return;

         if (zoneCoolerBindInfo.getCoolingDeviceInfoList() == null) return;

         if (ThermalManager.THERMAL_HIGH_EVENT == eventType) {
             for (ThermalManager.ZoneCoolerBindingInfo.CoolingDeviceInfo CdeviceInfo : zoneCoolerBindInfo.getCoolingDeviceInfoList()) {

                 currThrottleMask = CdeviceInfo.getThrottleMaskList().get(thermalState);
                 deviceId = CdeviceInfo.getCoolingDeviceId();

                 tDevice = ThermalManager.listOfCoolers.get(deviceId);
                 if (tDevice == null) continue;

                 if (currThrottleMask == ThermalManager.THROTTLE_MASK_ENABLE) {
                    existingState = tDevice.getThermalState();
                    tDevice.updateZoneState(zoneId, thermalState);
                    targetState = tDevice.getThermalState();

                    /* Do not throttle if device is already in desired state. (We can save Sysfs write) */
                    if (existingState != targetState) throttleDevice(deviceId, targetState);

                 } else {
                    /* If throttle mask is not enabled, don't do anything here. No-Op */
                 }
             }
         }

         if (ThermalManager.THERMAL_LOW_EVENT == eventType) {
            for (ThermalManager.ZoneCoolerBindingInfo.CoolingDeviceInfo CdeviceInfo : zoneCoolerBindInfo.getCoolingDeviceInfoList()) {

                 currDethrottleMask = CdeviceInfo.getDeThrottleMaskList().get(thermalState);
                 deviceId = CdeviceInfo.getCoolingDeviceId();

                 tDevice = ThermalManager.listOfCoolers.get(deviceId);
                 if (tDevice == null) continue;

                 existingState = tDevice.getThermalState();
                 tDevice.updateZoneState(zoneId, thermalState);
                 targetState = tDevice.getThermalState();

                 /* Do not dethrottle if device is already in desired state. (We can save Sysfs write) */
                 if ((existingState != targetState) &&
                     (currDethrottleMask == ThermalManager.DETHROTTLE_MASK_ENABLE))  throttleDevice(deviceId, targetState);
             }
         }
    }

    /* Method to throttle contributing device */
    private static void throttleDevice(int contributorID, int throttleLevel) {
       /* Retrieve the contributing device based on ID */
       ThermalCoolingDevice dev  = ThermalManager.listOfCoolers.get(contributorID);
       if (dev != null) {
           Class c = dev.getDeviceClass();
           Method throt = dev.getThrottleMethod();
           if (throt == null)
              return;
           Object arglist[] = new Object[1];
           arglist[0] = new Integer(throttleLevel);
           /* invoke the throttle method passing the
              throttle level as parameter */
           try {
                throt.invoke(c, arglist);
           } catch (IllegalAccessException e) {
                Log.i(TAG, "IllegalAccessException caught throttleDevice() ");
           } catch (IllegalArgumentException e) {
                Log.i(TAG, "IllegalArgumentException caught throttleDevice() ");
           } catch (ExceptionInInitializerError e) {
                Log.i(TAG, "ExceptionInInitializerError caught throttleDevice() ");
           } catch (SecurityException e) {
                Log.i(TAG, "SecurityException caught throttleDevice() ");
           } catch (InvocationTargetException e) {
                Log.i(TAG, "InvocationTargetException caught throttleDevice() ");
           }
       } else {
           Log.i(TAG, "throttleDevice: Unable to retrieve contributing device " + contributorID);
       }
    }
}
