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
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemProperties;
import android.util.Log;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.app.ActivityManagerNative;
import android.app.Instrumentation;
import android.view.KeyEvent;
import android.os.PowerManager;
import android.os.SystemClock;

/**
 * DPTF service takes care of thermal management actions like system shutdown,
 * system suspend on thermal action uevent received from esif driver.
 * esif driver triggers uevent based on decision from dptf module.
 * This is enabled based on the system property 'persist.service.dptf'
 * @hide
 */
public class DptfService extends Binder {
    private static final String TAG = DptfService.class.getSimpleName();
    private Context mContext;
    private Instrumentation mInstr;
    public static String uEventListen = "DPTF:THERMAL_REQUEST_ACTION";

    /* Register for boot complete Intent */
    public DptfService(Context context) {
        super();

        Log.i(TAG, "Initializing Dptf Service");
        mContext = context;
        mInstr = new Instrumentation();

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
            try {
                // watch for thermal event
                dptfObserver.startObserving(uEventListen);
            } catch (Exception e) {
                Log.d(TAG, "Exception in starting Dptf uevent observer"+e) ;
            }
        }
    }

    private final UEventObserver dptfObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            String action;
            Log.d(TAG, "DPTF uEvent Received: "+event);
            if (event.get(uEventListen) != null) {
                action = event.get(uEventListen);
                if (action.equals("shutdown")) {
                    triggerShutdown();
                } else if (action.equals("suspend")) {
                    triggerSuspend();
                }
            }
        }
    };

    private void triggerShutdown() {
        try {
            if (ActivityManagerNative.isSystemReady()) {
                Log.d(TAG, "Systen shutting down due to critical temperature");
                Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
                intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivityAsUser(intent, UserHandle.CURRENT);
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception in triggering thermal shutdown"+e);
        }
    }

    private void triggerSuspend() {
        try {
            if (ActivityManagerNative.isSystemReady()) {
                Log.d(TAG, "System suspending based on thermal action from dptf");
                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                pm.goToSleep(SystemClock.uptimeMillis());
            }
        } catch (Exception e) {
                Log.d(TAG, "Exception in triggering system suspend"+e);
        }
    }

}
