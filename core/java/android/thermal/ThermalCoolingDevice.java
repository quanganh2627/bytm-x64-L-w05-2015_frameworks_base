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

import java.util.ArrayList;
import java.lang.reflect.Method;
import android.util.Log;

/**
 * This class contains the cooling device specific information.
 * It also contains a reference to the actual throttle function.
 *@hide
 */
public class ThermalCoolingDevice {
    private static final String TAG = "ThermalCoolingDevice";
    private String mDeviceName;
    private String mClassPath;
    private String mThrottlePath;
    private int mCurrentThermalState;
    private int mDeviceId;
    private Class mDeviceClass;
    private Method mThrottleMethod;
    /** Maintains list of zoneid's under which this coolingdevice falls. */
    private ArrayList<Integer> mZoneIdList = new ArrayList<Integer>();
    /** Maintains corresponding state of zone present in mZoneidList */
    private ArrayList<Integer> mZoneStateList = new ArrayList<Integer>();

    public void setDeviceName(String Name) {
        mDeviceName = Name;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public void setDeviceId(int deviceId) {
        mDeviceId = deviceId;
    }

    public int getDeviceId() {
        return mDeviceId;
    }

    public String getClassPath() {
        return mClassPath;
    }

    public void setClassPath(String Path) {
        mClassPath = Path;
    }

    public Class getDeviceClass() {
        return mDeviceClass;
    }

    public void setDeviceClass(Class cls) {
        mDeviceClass = cls;
    }

    public Method getThrottleMethod() {
        return mThrottleMethod;
    }

    public void setThrottleMethod(Method method) {
        mThrottleMethod = method;
    }

    public String getThrottlePath() {
        return mThrottlePath;
    }

    public void setThrottlePath(String Path) {
        mThrottlePath = Path;
    }

    public ArrayList<Integer> getZoneIdList() {
        return mZoneIdList;
    }

    public ArrayList<Integer> getZoneStateList() {
        return mZoneStateList;
    }

    /**
     * Sets the current thermal state of cooling device which
     * will be maximum of all states of zones under which this
     * cooling device falls.
     */
    private void updateMaxThermalState() {
        int state = 0;
        for (Integer coolingDevState : mZoneStateList) {
            if (state < coolingDevState) state = coolingDevState;
        }
        mCurrentThermalState = state;
    }

    /**
     * Adds zoneID and its thermal state to mListOfZoneIDs and
     * mListOfTStatesOfZones array. If zoneId exists then its thermal
     * state is updated else zoneId and its state will be added to array.
     */
    public void addZoneState(int zoneId, int state) {
        if (mZoneIdList.isEmpty()) {
            mZoneIdList.add(zoneId);
            mZoneStateList.add(state);
            mCurrentThermalState = state;
            return;
        }
        int zoneIdIndex = mZoneIdList.indexOf(zoneId);
        if (zoneIdIndex == -1) {
            mZoneIdList.add(zoneId);
            mZoneStateList.add(state);
            updateMaxThermalState();
        } else {
            if (state == 0) {
                // Removing entry from array list if state is NORMAL.
                state = mZoneStateList.remove(zoneIdIndex);
                zoneId = mZoneIdList.remove(zoneIdIndex);
            } else {
                mZoneStateList.set(zoneIdIndex, state);
            }
            if (mZoneIdList.isEmpty()) mCurrentThermalState = 0;
            else updateMaxThermalState();
        }
    }

    /** Return true if cooling device can be de-throttled otherwise false. */
    public boolean isDeviceDeThrottlingAllowed(int zoneId, int state) {
        int index = 0;
        if (mZoneIdList.isEmpty() || state >= mCurrentThermalState) return false;
        for (Integer coolingDevZoneId : mZoneIdList) {
            if (coolingDevZoneId != zoneId && mCurrentThermalState == mZoneStateList.get(index)) {
                return false;
            }
            index++;
        }
        return true;
    }

    /** Return true if cooling device can be throttled otherwise false. */
    public boolean isDeviceThrottlingAllowed(int zoneId, int state) {
        if (mZoneIdList.isEmpty() || state > mCurrentThermalState) return true;

        return false;
    }
}
