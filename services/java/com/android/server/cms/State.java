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

public class State {
    private static final int MAX_DEVICES = 10;
    private static int mStateCnt = 0;
    private int mStateID;
    private int mLevel;
    private int mTemp;
    private ArrayList<Integer> mDevIDs = new ArrayList<Integer>();

    public State() {
        this.mStateID = mStateCnt++;
    }

    public void addDevID(int id) {
        mDevIDs.add(id);
    }

    public ArrayList<Integer> getDevIDList() {
        return mDevIDs;
    }

    public int getLevel() {
        return mLevel;
    }

    public void setLevel(int level) {
        this.mLevel = level;
    }

    public int getTemp() {
        return mTemp;
    }

    public void setTemp(int temp) {
        this.mTemp = temp;
    }
}
