/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.server.BatteryService;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.StringTokenizer;


/**
 * <p> CurrentMgmtService manages the current drawn by the platform.
 * On receiving an ACTION_BATTERY_CHANGED intent, for a change in the
 * battery level, this service configures the BCU(Burst Control Unit)
 * registers, through the sysfs exposed by the BCU driver, to reduce
 * the current consumption.</p>
 */
class CurrentMgmtService extends Binder {

    private static final String TAG = "CurrentMgmtService";

    private static final String bcuPath = "/sys/devices/ipc/msic_ocd/msic_current";
    private static final String battLevel = "/batt_level";
    private static final String battCaps = "/avail_batt_caps";

    private int mNumBattLevels;

    //Battery Levels (in percentage):
    //This array contains the battery capacities corresponding to the
    //warning levels. Size of this array is 'mNumBattLevels'. This array
    //is initialized from the 'avail_batt_caps' sysfs interface.
    private int[] mBattLevels;

    private final class BCUReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int battLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);

            //If the BatteryLevel is less than the required thresholds,
            //configure the BCU to take some actions
            for (int i = mNumBattLevels - 1; i >= 0; i--) {
                if (battLevel <= mBattLevels[i]) {
                   configBcu(i);
                   return;
                }
            }
        }
    }

    public CurrentMgmtService(Context context) {
        getAvailBattCaps();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(new BCUReceiver(), filter);
    }

    private final void initBattCaps(String line) {
        int i = 0;
        StringTokenizer st = new StringTokenizer(line);
        //Find the number of tokens in the string. This corresponds to the
        //number of battery levels
        mNumBattLevels = st.countTokens();
        mBattLevels = new int[mNumBattLevels];
        while (st.hasMoreTokens()) {
            mBattLevels[i] = Integer.parseInt(st.nextToken());
            i++;
        }
    }

    private final void getAvailBattCaps() {
        String path = bcuPath + battCaps;
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(path));
            initBattCaps(br.readLine());
        } catch (IOException e) {
           Log.i(TAG, "ReadSysfs Failed");
        }
    }

    private final void configBcu(int level) {
        String path = bcuPath + battLevel;
        DataOutputStream dos;
        try {
            dos = new DataOutputStream(new FileOutputStream(path));
            dos.writeBytes(Integer.toString(level));
            dos.close();
        } catch (IOException e) {
           Log.i(TAG, "WriteSysfs Failed");
        }
    }
}
