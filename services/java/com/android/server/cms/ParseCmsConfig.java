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
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class ParseCmsConfig {
    private ArrayList<ThrottleTrigger> mThrottleTriggers = new ArrayList<ThrottleTrigger>();
    private ArrayList<ContributingDevice> mCDevs = new ArrayList<ContributingDevice>();
    private ThrottleTrigger mThrottleTrigger;
    private ContributingDevice.throttleTrigger mCDevTT;
    private State mState;

    private ContributingDevice mCDev;
    private String mText;

    public ArrayList<ThrottleTrigger> getThrottleTriggers() {
        return mThrottleTriggers;
    }

    public ArrayList<ContributingDevice> getContributingDevices() {
        return mCDevs;
    }

    public boolean parseCmsThrottleConfig() {
        XmlPullParserFactory factory = null;
        XmlPullParser parser = null;
        InputStream is = null;
        InputStreamReader isr = null;
        try {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            is = new FileInputStream("/system/etc/cms_throttle_config.xml");
            isr = new InputStreamReader(is);
            parser = factory.newPullParser();
            parser.setInput(isr);

            int eventType = parser.getEventType();
            int count = 0;
            boolean isFirstState = true;
            while (eventType != XmlPullParser.END_DOCUMENT) {

                if (parser.getName() != null) {

                    switch (eventType) {
                        case XmlPullParser.START_DOCUMENT:
                            break;
                        case XmlPullParser.START_TAG:
                            if (parser.getName().equals("throttleTrigger")) {
                                mThrottleTrigger = new ThrottleTrigger();
                            } else if (parser.getName().equals("name")) {
                                mThrottleTrigger.setName(parser.nextText());
                            } else if (parser.getName().equals("normal") ||
                                parser.getName().equals("warning") ||
                                parser.getName().equals("alert") ||
                                parser.getName().equals("critical")) {
                                mState = new State();
                            } else if (parser.getName().contains("level")) {
                                mState.setLevel(Integer.parseInt(parser.nextText()));
                            } else if (parser.getName().contains("temp")) {
                                mState.setTemp(Integer.parseInt(parser.nextText()));
                            } else if (parser.getName().contains("devID")) {
                                mState.addDevID(Integer.parseInt(parser.nextText()));
                            }
                            break;
                        case XmlPullParser.TEXT:
                            break;
                        case XmlPullParser.END_TAG:
                            if (parser.getName().equals("throttleTrigger")) {
                               mThrottleTriggers.add(mThrottleTrigger);
                            } else if (parser.getName().equals("normal") ||
                                    parser.getName().equals("warning") ||
                                    parser.getName().equals("alert") ||
                                    parser.getName().equals("critical")) {
                                mThrottleTrigger.addState(mState);
                            }
                    }
                }

                eventType = parser.next();
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (isr != null) {
                try {
                    isr.close();
                } catch (IOException e) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean parseCmsDeviceConfig() {
        mCDevs = new ArrayList<ContributingDevice>();
        XmlPullParserFactory factory = null;
        XmlPullParser parser = null;
        InputStream is = null;
        InputStreamReader isr = null;
        try {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            is = new FileInputStream("/system/etc/cms_device_config.xml");
            isr = new InputStreamReader(is);
            parser = factory.newPullParser();
            parser.setInput(isr);

            int eventType = parser.getEventType();
            int count = 0;
            boolean isFirstState = true;
            while (eventType != XmlPullParser.END_DOCUMENT) {

                if (parser.getName() != null) {
                    switch (eventType) {
                        case XmlPullParser.START_DOCUMENT:
                            break;
                        case XmlPullParser.START_TAG:
                            if (parser.getName().equals("contributingDevice")) {
                                mCDev = new ContributingDevice();
                            } else if (parser.getName().equals("devName")) {
                                mCDev.setName(parser.nextText());
                            } else if (parser.getName().equals("devID")) {
                                mCDev.setID(Integer.parseInt(parser.nextText()));
                            } else if (parser.getName().equals("throttleTrigger")) {
                                mCDevTT = mCDev.new throttleTrigger();
                            } else if (parser.getName().equals("normal") ||
                                   parser.getName().equals("warning") ||
                                   parser.getName().equals("alert") ||
                                   parser.getName().equals("critical")) {
                                mCDevTT.addStateID();
                                mCDevTT.addThrottleValue(Integer.parseInt(parser.nextText()));
                            }
                            break;
                        case XmlPullParser.TEXT:
                            break;
                        case XmlPullParser.END_TAG:
                            if (parser.getName().equals("contributingDevice")) {
                                mCDevs.add(mCDev);
                            } else if (parser.getName().equals("throttleTrigger")) {
                                mCDev.addThrottleTrigger(mCDevTT);
                            }
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (isr != null) {
                try {
                    isr.close();
                } catch (IOException e) {
                    return false;
                }
            }
        }
        return true;
    }
}
