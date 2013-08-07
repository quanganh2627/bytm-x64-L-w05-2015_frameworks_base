/*
 * Copyright 2013 Intel Corporation All Rights Reserved.
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

import android.os.AsyncTask;
import android.os.SystemProperties;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.app.Notification;
import android.app.NotificationManager;
import android.util.Log;
import android.widget.Toast;

/**
 * This class is reponsible for notifying user before shutdown
 * initiated by Thermal.
 *@hide
 */

public class ThermalNotifier {

    private NotificationManager mNotificationManager;
    private Context mContext;
    private static final String TAG = "ThermalNotifier";
    private static final String THERMAL_SHUTDOWN_NOTIFY_PATH = "/sys/module/intel_mid_osip/parameters/force_shutdown_occured";

    public ThermalNotifier(Context context) {
        mContext = context;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_THERMAL_SHUTDOWN);
        mContext.registerReceiver(new ShutdownNotifyReceiver(), filter);

        mNotificationManager = (NotificationManager) mContext.getSystemService(
                               Context.NOTIFICATION_SERVICE);
    }

    private void vibrate() {
        Notification notify = new Notification();
        notify.defaults |= Notification.DEFAULT_VIBRATE;

        if (mNotificationManager != null) {
            mNotificationManager.notify(TAG, 0, notify);
        }
    }

    private void tone() {
        Notification notify = new Notification();
        notify.defaults |= Notification.DEFAULT_SOUND;

        if (mNotificationManager != null) {
            mNotificationManager.notify(TAG, 0, notify);
        }
    }

    private void vibrateWithTone() {
        Notification notify = new Notification();
        notify.defaults |= Notification.DEFAULT_SOUND;
        notify.defaults |= Notification.DEFAULT_VIBRATE;
        if (mNotificationManager != null) {
            mNotificationManager.notify(TAG, 0, notify);
        }
    }

    private final class ShutdownNotifyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            new ShutdownDisplay().execute();
        }
    }

    private class ShutdownDisplay extends AsyncTask<Void, String, String> {

        @Override
        protected String doInBackground(Void... arg) {
            if ("1".equals(SystemProperties.get("persist.thermal.shutdown.msg", "0"))) {
                publishProgress("Shutting down due to Thermal critical event");
            }
            if ("1".equals(SystemProperties.get("persist.thermal.shutdown.vibra", "0"))) {
                if ("1".equals(SystemProperties.get("persist.thermal.shutdown.tone", "0"))) {
                    vibrateWithTone();
                } else {
                    vibrate();
                }
            } else if ("1".equals(SystemProperties.get("persist.thermal.shutdown.tone", "0"))) {
                tone();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... arg) {
            String msg = arg[0];
            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onPostExecute(String arg) {
            // Initiate Shutdown
            Log.i(TAG, "Platform shutdown initiated from ThermalService.");
            SysfsManager.writeSysfs(THERMAL_SHUTDOWN_NOTIFY_PATH, 1);
            Intent criticalIntent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
            criticalIntent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
            criticalIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(criticalIntent);

        }
    }
}
