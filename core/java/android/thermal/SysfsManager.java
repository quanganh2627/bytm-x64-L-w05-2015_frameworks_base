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
public class SysfsManager
{
    private static final String TAG = "Thermal:SysfsManager";

    public static boolean isFileExists(String path) {
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
          if (br != null) {
              val = br.readLine();
              br.close();
          }
       } catch (IOException ioe) {
            Log.i(TAG, "caught IOException in readSysfs() for file:" + path);
       }
       return val;
    }

    public static int readSysfsAsInt(String path) {
        int val = -1;
        try {
            String tempStr = readSysfs(path);
            if (tempStr != null) {
                val = Integer.parseInt(tempStr.trim());
            }
        } catch (NumberFormatException e) {
            Log.i(TAG, "NumberFormatException in readSysfsAsInt() for file:" + path);
        }
        return val;
    }

    public static void writeSysfs(String path, int value) {
       BufferedWriter bw = null;
       FileWriter fw = null;

       if (!isFileExists(path)) {
           Log.i(TAG, "writeSysfs failed:"+ path + "does not exist");
           return;
       }
       try {
            fw = new FileWriter(path);
            bw = new BufferedWriter(fw);
            bw.write(Integer.toString(value));
       } catch (IOException ioe) {
           Log.i(TAG, "IOException caught at writeSysfs() for file:" + path);
       } finally {
           try {
               if (bw != null) bw.close();
               if (fw != null) fw.close();
           } catch (IOException ioe) {
               Log.i(TAG, "IOException caught at finally block in writeSysfs");
               ioe.printStackTrace();
           }
       }
    }
}
