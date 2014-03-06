/*
 * Copyright 2013 Intel Corporation All Rights Reserved.
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

package android.net.wifi;

import android.os.Parcel;
import android.os.Parcelable;


/**
 * A class representing a configured Wi-Fi hotspot.
 * @hide
 */
public class WifiApConfiguration implements Parcelable {

    public WifiChannel mChannel;
    public String mHwMode;
    public boolean mIs80211n;
    public String mIpAddress;
    public String mNetMask;

    public static final String HW_MODE_A = "a";
    public static final String HW_MODE_AC = "c";
    public static final String HW_MODE_BG = "g";
    public final static String DEFAULT_SERVER_ADDRESS = "192.168.43.1";
    public final static String DEFAULT_NETMASK = "255.255.255.0";

    /** @hide */
    public WifiApConfiguration() {
        super();
        mChannel = new WifiChannel(WifiChannel.DEFAULT_2_4_CHANNEL);
        mHwMode = HW_MODE_BG;
        mIs80211n = true;
        mIpAddress = DEFAULT_SERVER_ADDRESS;
        mNetMask = DEFAULT_NETMASK;
    }

    /** copy constructor {@hide} */
    public WifiApConfiguration(WifiApConfiguration source) {
        if (source != null) {
            mChannel = source.mChannel;
            mHwMode = source.mHwMode;
            mIs80211n = source.mIs80211n;
            mIpAddress = source.mIpAddress;
            mNetMask = source.mNetMask;
        }
    }

    /** Implement the Parcelable interface {@hide} */
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface {@hide} */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mChannel, flags);
        dest.writeString(mHwMode);
        dest.writeInt(mIs80211n ? 1 : 0);
        dest.writeString(mIpAddress);
        dest.writeString(mNetMask);
    }

    /** @hide */
    public void resetRadioConfig() {
        mChannel = new WifiChannel(WifiChannel.DEFAULT_2_4_CHANNEL);
        mHwMode = HW_MODE_BG;
        mIs80211n = true;
    }

    /** @hide */
    public boolean isRadioDefault() {
        return (mChannel.getChannel() == WifiChannel.DEFAULT_2_4_CHANNEL &&
                mHwMode == HW_MODE_BG && mIs80211n);
    }

    /** @hide */
    public void setChannel(int chan) {
        mChannel.setChannel(chan);
    }

    /** @hide */
    public int getChannel() {
        return mChannel.getChannel();
    }

    /** Implement the Parcelable interface {@hide} */
    public static final Creator<WifiApConfiguration> CREATOR
        = new Creator<WifiApConfiguration>() {
        public WifiApConfiguration createFromParcel(Parcel in) {
            WifiApConfiguration config = new WifiApConfiguration();

            if (config != null) {
                config.mChannel = in.readParcelable(null);
                config.mHwMode = in.readString();
                config.mIs80211n = (in.readInt() == 1);
                config.mIpAddress = in.readString();
                config.mNetMask = in.readString();
            }
            return config;
        }

        public WifiApConfiguration[] newArray(int size) {
            return new WifiApConfiguration[size];
        }
    };
}
