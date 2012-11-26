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
import java.util.Scanner;
import java.util.regex.Pattern;
import android.util.Log;

/**
 * The CPUMaxFreqControl class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED} Intent.
 *@hide
 */
public class CPUMaxFreqControl {
    private static final String TAG = "CPUMaxFreqControl";

    /* sysfs path for throttle devices */
    private static final String mCPUDeviceSysfsPath = "/sys/devices/system/cpu/";
    private static final String mCPUThrottleSysfsPath = "/cpufreq/scaling_max_freq";
    private static final String mCPUAvailFreqsPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies";

    /* Data to detect the platform, product and hardware */
    private static final String mSPIDPlatformIDPath = "/sys/spid/platform_family_id";
    private static final String mSPIDProductIDPath = "/sys/spid/product_line_id";
    private static final String mSPIDHardwareIDPath = "/sys/spid/hardware_id";

    /* SPID values for PLatform, hardware and product ID */
    private static final String mSPIDPlatformIDMfld = "0000";
    private static final String mSPIDPlatformIDCtp = "0002";
    private static final String mSPIDProductIDLexProd = "0004";
    private static final String mSPIDProductIDLexEng = "8004";
    private static final String mSPIDProductIDPRxProd = "0000";
    private static final String mSPIDProductIDPRxEng = "8000";
    private static final String mSPIDHardwareIDPR4 = "0007";


    /* CPU related data */
    private static String mCPUInfoSysfsPath = "/proc/interrupts";
    private static int mProcessorCount;
    private static int mMaxFreqCount = 10;
    private static int mAvailFreq[] = new int[mMaxFreqCount];
    private static int mAvailFreqCount;
    private static boolean mNoCPUMaxScalingFreqs = false;
    private static int mMaxScalingFreq[] = new int[4];   /* max scaling freqs */

    private static void getProdCpuMaxScalingFreqs() {
       String platformId = SysfsManager.readSysfs(mSPIDPlatformIDPath);
       String productId = SysfsManager.readSysfs(mSPIDProductIDPath);
       String hardwareId = SysfsManager.readSysfs(mSPIDHardwareIDPath);
       int i = 0;

       if ((platformId == null) || (productId == null) || (hardwareId == null)) return;

       /* Need to update the file with new set of max scaling freqs
          when a platform has less than 2 available freqs */
       if (mAvailFreqCount <= 2) {
            mNoCPUMaxScalingFreqs = true;
            Log.i(TAG, "Available freq <= 2");
            return;
        }

       /* MaxScaling frequencies follow the sequence
          Normal, Warning, Alert and Critical states
          for mMaxScalingFreq[0] - mMaxScalingFreq[3] */

       /* Normal state freq always the max available freq */
       mMaxScalingFreq[0] = mAvailFreq[0];

       if (platformId.equals(mSPIDPlatformIDCtp)) {
       /* CTP device */
            mMaxScalingFreq[1] = 1800000;
            mMaxScalingFreq[2] = 1400000;
            mMaxScalingFreq[3] = 900000;

       } else if (platformId.equals(mSPIDPlatformIDMfld)) {
          /* Medfield Device */
          if ((productId.equals(mSPIDProductIDLexProd)) || (productId.equals(mSPIDProductIDLexEng))) {
             /* Lex */
             mMaxScalingFreq[1] = 1200000;
             mMaxScalingFreq[2] = 900000;
             mMaxScalingFreq[3] = 600000;
          } else if ((productId.equals(mSPIDProductIDPRxProd)) || (productId.equals(mSPIDProductIDPRxEng))) {
             /* PRx */
             if (hardwareId.equals(mSPIDHardwareIDPR4)) {
                /* PR4*/
                mMaxScalingFreq[1] = 1400000;
                mMaxScalingFreq[2] = 900000;
                mMaxScalingFreq[3] = 600000;
             } else {
                /* Non-PR4 */
                mMaxScalingFreq[1] = 1200000;
                mMaxScalingFreq[2] = 900000;
                mMaxScalingFreq[3] = 600000;
             }
          } else {
             mMaxScalingFreq[1] = 1400000;
             mMaxScalingFreq[2] = 900000;
             mMaxScalingFreq[3] = 600000;

             Log.i(TAG, "SPID does not match with available Medfield product id");
          }
       } else {
            mMaxScalingFreq[1] = mAvailFreq[1];
            mMaxScalingFreq[2] = mAvailFreq[2];
            mMaxScalingFreq[3] = mAvailFreq[mAvailFreqCount - 1];

            Log.i(TAG, "SPID does not match with the available Platform id");
       }

       Log.i(TAG, "Computed max cpu scaling freq array");
       for (i = 0; i < 4; i++)
            Log.i(TAG, "freq " +  mMaxScalingFreq[i]);
   }

    private static void getNumberOfProcessors() throws IOException {
        BufferedReader myReader = null;
        try {
            myReader = new BufferedReader(new FileReader(mCPUInfoSysfsPath));
            //In first line: NoOfProcessors = NoOfColumns
            String line = myReader.readLine();
            for (String token : line.split(" ")) {
                if (token.startsWith("C"))
                   mProcessorCount++;
            }
        }
        catch (Exception e) {
             Log.i(TAG,"Unable to count Processors");
        }
        finally {
            /* ensure the underlying stream is always closed */
            myReader.close();
        }
        Log.i(TAG,"Number of mProcessorCount" + mProcessorCount);
    }

    /* function reads the available cpu frequencies */
    private static void readAvailFreq() {
        BufferedReader myReader = null;
        try {
            myReader = new BufferedReader(new FileReader(mCPUAvailFreqsPath));
            int i = 0;
            String line;
            while ((line = myReader.readLine()) != null) {
                for (String token : line.split(" ")) {
                    try {
                        mAvailFreq[i++] = Integer.parseInt(token);
                        Log.i(TAG, "Freq:"+mAvailFreq[i-1]);
                    } catch (NumberFormatException ex) {
                        Log.i(TAG, token + " is not a number");
                    }
                }
            }
            mAvailFreqCount = i;
            myReader.close();
        }
        catch(IOException e) {
            Log.i(TAG,"IOException on read available frequencies");
        }
    }

    public static void initializeCpuFreqThrottling() {
        /* read the available frequencies */
        readAvailFreq();

        /* determine number of cpu cores on the platform */
        try {
            getNumberOfProcessors();
        }
        catch (IOException e) {
            /* We should never come here */
            Log.i(TAG, "Exception occured while reading number of processors");
            return;
        }

        /* compute the scaling max frequencies used for
           cpu freq throttling based on device's spid */
          getProdCpuMaxScalingFreqs();
    }

    public static int getMaxCpuFreq() {
        int currCpuMaxFreq;
        String processorString = "cpu0";
        currCpuMaxFreq = SysfsManager.readSysfsAsInt(mCPUDeviceSysfsPath+processorString+mCPUThrottleSysfsPath);
        return currCpuMaxFreq;
    }

    public static void throttleDevice(int tstate) {
        Log.d(TAG, "throttleDevice called with" + tstate);

       /* check if scaling frequencies are available */
        if (mNoCPUMaxScalingFreqs || (mProcessorCount == 0)) {
           Log.i(TAG, "Throttling CPU max freq not possible." +
                      "Scaling frequencies not available" +
                      "or Processor count is zero");
           return;
        }

        /* get the new scaling max scaling freq */
        int newMaxCpuFreq = mMaxScalingFreq[tstate];
        Log.i(TAG, "Throttling CPU max freq value is." + newMaxCpuFreq);

        /* loop through and write scaling max freq of
           all logical cpus for a given core */
        String path;
        String cpuString = "cpu";
        for (int i = 0; i < mProcessorCount; i++) {
            path = mCPUDeviceSysfsPath+cpuString+i+mCPUThrottleSysfsPath;
            SysfsManager.writeSysfs(path,newMaxCpuFreq);
        }
    }

    public static void init(String path) {
       /* Initialize scalingfactors */
       initializeCpuFreqThrottling();
    }
}

