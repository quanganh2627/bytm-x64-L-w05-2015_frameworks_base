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

/* Intel - Wifi_Hotspot */
package android.net.wifi;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * A class storing channel number, band, and LTE conflict for wifi radio channel
 * mainly designed for Wifi Hotspot usage
 * @hide
 */
public class WifiChannel implements Parcelable, Comparable<WifiChannel> {

    public enum Band { BAND_2_4GHZ, BAND_5GHZ }
    public enum ChannelWidth { HT20, HT40, HT80, HT80P80, HT160}

    public static final int DEFAULT_2_4_CHANNEL = 6;
    public static final int DEFAULT_5_CHANNEL = 36;

    private static final int IEEE80211_HT_CAP_SGI_40          = 0x0040;
    private static final int IEEE80211_HT_CAP_SUP_WIDTH_20_40 = 0x0002;
    private static final int IEEE80211_VHT_CAP_SHORT_GI_80    = 0x0020;

    private int mChannel;
    private ChannelWidth mWidth;

    /** @hide */
    public WifiChannel(String channelNumber, String htCapab, String vhtCapab) {
        try {
            mChannel = Integer.parseInt(channelNumber);
        } catch (NumberFormatException e) {
            mChannel = DEFAULT_2_4_CHANNEL;
        }
        mWidth = ChannelWidth.HT20;
        try {
            int htCap  = Integer.parseInt(htCapab, 16);
            int vhtCap = Integer.parseInt(vhtCapab, 16);
            if (((htCap & IEEE80211_HT_CAP_SGI_40) == IEEE80211_HT_CAP_SGI_40) ||
                ((htCap & IEEE80211_HT_CAP_SUP_WIDTH_20_40) == IEEE80211_HT_CAP_SUP_WIDTH_20_40)) {
                mWidth = ChannelWidth.HT40;
            }
            if ((vhtCap & IEEE80211_VHT_CAP_SHORT_GI_80) == IEEE80211_VHT_CAP_SHORT_GI_80) {
                    mWidth = ChannelWidth.HT80;
            }
        } catch (NumberFormatException e) {
            mWidth = ChannelWidth.HT20;
        }
    }

    /** @hide */
    public WifiChannel(String channelNumber, ChannelWidth width) {
        try {
            mChannel = Integer.parseInt(channelNumber);
        } catch (NumberFormatException e) {
            mChannel = DEFAULT_2_4_CHANNEL;
        }
        mWidth = width;
    }

    /** @hide */
    public WifiChannel(int channelNumber, ChannelWidth width) {
        mChannel = channelNumber;
        mWidth = width;
    }

    /** @hide */
    public WifiChannel(String channelNumber, int width) {
        try {
            mChannel = Integer.parseInt(channelNumber);
        } catch (NumberFormatException e) {
            mChannel = DEFAULT_2_4_CHANNEL;
        }
        try {
            if (width < ChannelWidth.values().length) {
                mWidth = ChannelWidth.values()[width];
            } else {
                mWidth = ChannelWidth.HT20;
            }
        } catch (NumberFormatException e) {
            mWidth = ChannelWidth.HT20;
        }
    }

    /** @hide */
    public WifiChannel(int channelNumber, int width) {
        mChannel = channelNumber;
        try {
            if (width < ChannelWidth.values().length) {
                mWidth = ChannelWidth.values()[width];
            } else {
                mWidth = ChannelWidth.HT20;
            }
        } catch (NumberFormatException e) {
            mWidth = ChannelWidth.HT20;
        }
    }

    /** Implement the Parcelable interface **/
    public int describeContents() {
        return 0;
    }

    /** @hide */
    public int getChannel() {
        return mChannel;
    }

    /** @hide */
    public void setChannel(int channel) {
        mChannel = channel;
    }

    /** @hide */
    public Band getBand() {
        if (mChannel >= 1 && mChannel <= 14)
            return Band.BAND_2_4GHZ;
        return Band.BAND_5GHZ;
    }

    /** @hide */
    public ChannelWidth getWidth() {
        return mWidth;
    }

    /** @hide */
    public void setWidth(ChannelWidth width) {
        mWidth = width;
    }

    /** @hide */
    public void setWidth(int width) {
        try {
            if (width < ChannelWidth.values().length) {
                mWidth = ChannelWidth.values()[width];
            } else {
                mWidth = ChannelWidth.HT20;
            }
        } catch (NumberFormatException e) {
            mWidth = ChannelWidth.HT20;
        }
    }

    /** @hide */
    public Boolean isLteConflict() {
        // TODO this will be used to flag conflicts with LTE
        return false;
    }

    /** @hide */
    @Override
    public String toString() {
        return "Channel " + Integer.toString(mChannel) + " " +
               "Width " + mWidth.toString();
    }

    /** @hide */
    @Override
    public int compareTo(WifiChannel other) {
        return (other.mChannel - this.mChannel);
    }

    /** @hide */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WifiChannel))
            return false;
        return (this.compareTo((WifiChannel) other) == 0);
    }

    /** Implement the Parcelable interface */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mChannel);
        dest.writeInt(mWidth.ordinal());
    }

    /** Implement the Parcelable interface */
    public static final Creator<WifiChannel> CREATOR
        = new Creator<WifiChannel>() {
        public WifiChannel createFromParcel(Parcel in) {
            return new WifiChannel(in.readInt(), in.readInt());
        }

        public WifiChannel[] newArray(int size) {
            return new WifiChannel[size];
        }
    };
}
