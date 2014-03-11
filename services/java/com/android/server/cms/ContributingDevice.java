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
package com.android.server.cms;
import java.util.ArrayList;

public class ContributingDevice {
    private int mDevID;
    private String mName;
    private ArrayList<throttleTrigger> mThrottleTriggers;
    private int mTriggercnt = 0;
    private boolean mThrottleOverride;

    public ContributingDevice() {
        mThrottleTriggers = new ArrayList<throttleTrigger>();
    }

    public void addThrottleTrigger(throttleTrigger tt) {
        mThrottleTriggers.add(tt);
    }

    public throttleTrigger getThrottleTriggerByID(int id) {
        return mThrottleTriggers.get(id);
    }

    public boolean getThrottleOverride() {
        return mThrottleOverride;
    }

    public void setThrottleOverride(boolean canOverride) {
        mThrottleOverride = canOverride;
    }

    public class throttleTrigger {

        private String mName;
        private int mStateCnt = 0;
        private ArrayList<Integer> mStateIDs;
        private ArrayList<Integer> mThrottleValues;
        private int mTriggerid;

        public throttleTrigger() {
            this.mTriggerid = mTriggercnt++;
            mStateIDs = new ArrayList<Integer>();
            mThrottleValues = new ArrayList<Integer>();
        }

        public void addStateID() {
            mStateIDs.add(mStateCnt++);
        }

        public void addThrottleValue(int value) {
            mThrottleValues.add(value);
        }

        public int getThrottleValue(int triggerid, int stateid) {
            return mThrottleTriggers.get(triggerid).mThrottleValues.get(stateid);
        }
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void setID(int id) {
        this.mDevID = id;
    }

    public int getID() {
        return this.mDevID;
    }
}
