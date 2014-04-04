/*
 * Copyright 2014 Intel Corporation All Rights Reserved.
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.app.AlarmManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemProperties;
import android.util.Log;

/**
 * Wake service triggers periodic RTC wakeup from standby when WiFi is ON.
 * This is enabled based on the system property 'persist.service.wake.enable'
 * and the interval between wake up from standby is configurable through the
 * system property 'persist.wake.interval.secs'.
 * @hide
 */
public class WakeService extends Binder {
    private static final String TAG = WakeService.class.getSimpleName();
    private Context mContext;
    private PendingIntent pendingIntent;
    private boolean alarmStatus = false;
    private AlarmManager alarmManager;
    private static  WifiStateReceiver wifiReceiver;
    private static long wakeInterval;

    /* Register for boot complete Intent */
    public WakeService(Context context) {
        super();

        Log.i(TAG, "Initializing Wake Service");
        mContext = context;

        /* Wait for the BOOT Completion */
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        mContext.registerReceiver(new BootCompReceiver(), filter);
    }

    /* Handler to boot complete intent */
    private final class BootCompReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            wakeInterval = getInterval();

            IntentFilter wifiFilter = new IntentFilter();
            wifiFilter.addAction("android.net.wifi.STATE_CHANGE");
            mContext.registerReceiver(new WifiStateReceiver(), wifiFilter);

            Intent wake_intent = new Intent(mContext, WakeBroadcastReceiver.class);
            pendingIntent  = PendingIntent.getBroadcast(mContext, 0, wake_intent, 0);
        }
    }

    public class WifiStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkInfo networkInfo = intent
                .getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

            if (networkInfo != null) {
               if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                   //get the different network states
                   if (networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                      Log.d(TAG, "Type: " + networkInfo.getType()
                            + " State: " + networkInfo.getState());
                      startAlarm();
                   } else if (networkInfo.getState() == NetworkInfo.State.DISCONNECTED) {
                      Log.d(TAG, "Type: " + networkInfo.getType()
                            + " State: " + networkInfo.getState());
                      stopAlarm();
                   }
               }
            }

         }
    }

    public class WakeBroadcastReceiver extends BroadcastReceiver {
       @Override
       public void onReceive(Context context, Intent intent) {
           Log.d(TAG, "WakeBroadcastReceiver OnReceive");
       }
    }

    public void startAlarm() {
        Log.d(TAG,"alarmStatus: " + alarmStatus);

        try {
            if (!alarmStatus) {
              Log.d(TAG, "Starting alarm with interval: "+ wakeInterval + " msec");
              alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
                       + (1 * 1000), wakeInterval, pendingIntent);
              alarmStatus = true;
            }
        } catch (Exception e) {
              Log.e(TAG, "Exception in starting RTC wake up alarm"+ e);
        }
    }

    public void stopAlarm() {
        Log.d(TAG,"Stop Alarm " + alarmStatus);

        try {
           if (alarmStatus && (pendingIntent != null)) {
              Log.d(TAG, "Stopping the Alarm");

             alarmManager.cancel(pendingIntent);
             alarmStatus = false;
           }
        } catch (Exception e) {
             Log.e(TAG, "Exception in stopping RTC wake up alarm"+ e);
        }
    }

    public long getInterval() {
        // interval between wakeups from standby (in msec)
        long interval;

        try {
           interval = Long.parseLong(SystemProperties.get("persist.wake.interval.secs")) * 1000;
        } catch (Exception e) {
           Log.e(TAG, "Exception in getting property persist.wake.interval.secs "+ e);
           interval = 300 * 1000;  // default value of 300 s
        }

        return interval;
    }

}
