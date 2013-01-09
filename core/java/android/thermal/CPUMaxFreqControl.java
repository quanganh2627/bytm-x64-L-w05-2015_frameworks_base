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

import android.util.Log;

/**
 * The CPUMaxFreqControl class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED} Intent.
 *@hide
 */
public class CPUMaxFreqControl {
    private static final String TAG = "CPUMaxFreqControl";

    // Sysfs path for throttle devices
    private static final String mCPUDeviceSysfsPath = "/sys/devices/system/cpu/";
    private static final String mCPUThrottleSysfsPath = "/cpufreq/scaling_max_freq";
    private static final String mCPUAvailFreqsPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies";
    private static final String mCPUPresentPath = "/sys/devices/system/cpu/present";

    // Data to detect the platform, product and hardware
    private static final String mSPIDPlatformIDPath = "/sys/spid/platform_family_id";
    private static final String mSPIDProductIDPath = "/sys/spid/product_line_id";
    private static final String mSPIDHardwareIDPath = "/sys/spid/hardware_id";

    // CPU related data
    private static int mProcessorCount;
    private static int mMaxFreqCount = 10;
    private static int mAvailFreq[] = new int[mMaxFreqCount];
    private static int mAvailFreqCount;
    private static boolean mNoCPUMaxScalingFreqs = false;
    private static int mMaxScalingFreq[] = new int[4];

    private static final String mDeviceIdentifier[][] = {
        // platform  product      hardware
        {"0000",    "8000",     "0007"},    // MFLD  PRxEng  PR4
        {"0000",    "0000",     "0007"},    // MFLD  PRxProd PR4
        {"0000",    "0000",     "0003"},    // MFLD  PR3.3   PR3.3
        {"0000",    "8000",     "0001"},    // MFLD  PR3.1   PR3.1
        {"0000",    "8004",     "0004"},    // MFLD  LEXEng  -
        {"0000",    "0004",     "0004"},    // MFLD  LEXProd -
        {"0000",    "any",      "any"},     // MFLD  Default case
        {"0002",    "8000",     "0016"},    // CTP   PR2Eng  PR2
        {"0002",    "any",      "any"},     // CTP   Default case
    };

    // Each Row in this array is matched with that of the same row in mDeviceIdentifier array
    private static final int mThrottleFreq[][] = {
        // Warning   Alert       Critical
        {1400000,   900000,     600000},
        {1400000,   900000,     600000},
        {1200000,   900000,     600000},
        {1200000,   900000,     600000},
        {1200000,   900000,     600000},
        {1200000,   900000,     600000},
        {1400000,   900000,     600000},    // Default set of frequencies for MFLD platform
        {1866000,   1333000,    933000},
        {1800000,   1400000,    900000},    // Default set of frequencies for CTP platform
    };

    private static int findIndex(String plat, String prod, String hw) {
        for (int i = 0; i < mDeviceIdentifier.length; i++) {
            if (plat.equals(mDeviceIdentifier[i][0]) && prod.equals(mDeviceIdentifier[i][1]) && hw.equals(mDeviceIdentifier[i][2]))
                return i;
        }
        return -1;
    }

    private static void computeCpuMaxScalingFreqs() {
       String platformId = SysfsManager.readSysfs(mSPIDPlatformIDPath);
       String productId = SysfsManager.readSysfs(mSPIDProductIDPath);
       String hardwareId = SysfsManager.readSysfs(mSPIDHardwareIDPath);

       if ((platformId == null) || (productId == null) || (hardwareId == null)) return;

       // Need to update the file with new set of max scaling freqs,
       // when a platform has less than 2 available freqs
       if (mAvailFreqCount <= 2) {
            mNoCPUMaxScalingFreqs = true;
            Log.i(TAG, "Available CPU freq <= 2");
            return;
        }

       int index = findIndex(platformId, productId, hardwareId);
       if (index == -1) {
           // We could not get an exact match. So, try for 'this' platform, but any product/HW combination.
           index = findIndex(platformId, "any", "any");
           if (index == -1) {
               Log.i(TAG, "Thermal plugin for CPU Freq control cannot detect the platform.\n" +
                           "Hence, Choosing a Random set of frequencies.\n" +
                           "The CPU throttling behavior is undefined.");
               mMaxScalingFreq[0] = mAvailFreq[0];
               mMaxScalingFreq[1] = mAvailFreq[1];
               mMaxScalingFreq[2] = mAvailFreq[2];
               mMaxScalingFreq[3] = mAvailFreq[mAvailFreqCount - 1];
               return;
           } else {
               Log.i(TAG, "Using default frequency set for platformId" + platformId);
           }
       }

       // For Normal State: Max Scaling frequency is always the Max available frequency
       mMaxScalingFreq[0] = mAvailFreq[0];
       mMaxScalingFreq[1] = mThrottleFreq[index][0];
       mMaxScalingFreq[2] = mThrottleFreq[index][1];
       mMaxScalingFreq[3] = mThrottleFreq[index][2];
    }

    private static void getNumberOfProcessors() {
        String cpu = SysfsManager.readSysfs(mCPUPresentPath);
        if (cpu == null) return;

        // This sysfs interface exposes the number of CPUs present in 0-N format,
        // when there are N+1 CPUs. Tokenize the string and find N
        try {
            mProcessorCount = Integer.parseInt(cpu.split("-")[1]) + 1;
        } catch (NumberFormatException ex) {
            Log.i(TAG, "NumberFormatException in getNumberOfProcessors");
        }
    }

    private static void readAvailFreq() {
        String line = SysfsManager.readSysfs(mCPUAvailFreqsPath);
        if (line == null) return;

        for (String token : line.split(" ")) {
            try {
                mAvailFreq[mAvailFreqCount++] = Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                Log.i(TAG, token + " is not a number");
            }
        }
    }

    private static void printAttrs() {
        Log.i(TAG, "Thermal plugin for CPU freq control: Initialized parameters:");
        Log.i(TAG, "Number of Processors present: " + mProcessorCount);
        
        Log.i(TAG, "Number of Available frequencies: " + mAvailFreqCount);
        for (int i = 0; i < mAvailFreqCount; i++)
            Log.i(TAG, "AvailableFrequency[" + i + "]: " + mAvailFreq[i]);
        
        Log.i(TAG, "Computed Max Scaling Array:");
        for (int i = 0; i < 4; i++)
             Log.i(TAG, "ScalingMaxFreq[" + i + "]: " + mMaxScalingFreq[i]);
    }

    public static void throttleDevice(int tstate) {
        // Check if scaling frequencies are available
        if (mNoCPUMaxScalingFreqs || (mProcessorCount == 0)) {
           Log.i(TAG, "Throttling CPU max freq not possible." +
                      "Scaling frequencies not available" +
                      "or Processor count is zero");
           return;
        }

        Log.d(TAG, "throttleDevice called with" + tstate);
        Log.i(TAG, "Throttling CPU max freq value is " + mMaxScalingFreq[tstate]);

        // Throttle frequency of all CPUs by writing into the Sysfs
        for (int i = 0; i < mProcessorCount; i++) {
            String path = mCPUDeviceSysfsPath + "cpu" + i + mCPUThrottleSysfsPath;
            SysfsManager.writeSysfs(path, mMaxScalingFreq[tstate]);
        }
    }

    public static void init(String path) {
       readAvailFreq();
       getNumberOfProcessors();
       computeCpuMaxScalingFreqs();
       printAttrs();
    }
}

