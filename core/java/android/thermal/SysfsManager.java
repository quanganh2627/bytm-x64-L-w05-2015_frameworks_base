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
import java.io.File;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;

/**
 * The SysfsManager class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_THERMAL_ZONE_STATE_CHANGED} Intent.
 *@hide
 */
class SysfsManager
{
    private static final String TAG = "Thermal:SysfsManager";

    private static boolean isFileExists(String path) {
         return (new File(path)).exists();
    }

    public static String readSysfs(String path) {
        if (!isFileExists(path)) {
            Log.i(TAG, path + "does not exist");
            return null;
        }

        BufferedReader br = null;
        String val = null;
        try {
          br = new BufferedReader(new FileReader(path));
          val = br.readLine();
          br.close();
       }
       catch(IOException ioe){
          Log.i(TAG, "IOException in readSysfs. Path:" + path);
       }
       return val;
    }

    public static int readSysfsAsInt(String path) {
       int val = -1;
       try {
          val = Integer.parseInt((readSysfs(path)).trim());
       } catch (NumberFormatException nfe) {
          Log.i(TAG, "NumberFormatException in readSysfsAsInt. Path:" + path);
       }
       return val;
    }

    public static void writeSysfs(String path, int value) {
       BufferedWriter bw = null;
       try {
            bw = new BufferedWriter(new FileWriter(path));
            bw.write(Integer.toString(value));
            bw.close();
        }
        catch (IOException ioe) {
            Log.i(TAG, "IOException in writeSysfs. Path:" + path + " value:" + value);
        }
    }
}
