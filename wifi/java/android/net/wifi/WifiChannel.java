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

/**
 * A class storing channel number, band, and LTE conflict for wifi radio channel
 * mainly designed for Wifi Hotspot usage
 * @hide
 */
public class WifiChannel implements Parcelable, Comparable<WifiChannel> {

    public enum Band { BAND_2_4GHZ, BAND_5GHZ }

    public static final int DEFAULT_2_4_CHANNEL = 6;
    public static final int DEFAULT_5_CHANNEL = 36;

    private int mChannel;


    /** @hide */
    public WifiChannel(int channelNumber) {
        mChannel = channelNumber;
    }

    /** @hide */
    public WifiChannel(String channelNumber) {
        try {
            mChannel = Integer.parseInt(channelNumber);
        } catch (NumberFormatException e) {
            mChannel = DEFAULT_2_4_CHANNEL;
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
    public Boolean isLteConflict() {
        // TODO this will be used to flag conflicts with LTE
        return false;
    }

    /** @hide */
    @Override
    public String toString() {
        return Integer.toString(mChannel);
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
    }

    /** Implement the Parcelable interface */
    public static final Creator<WifiChannel> CREATOR
        = new Creator<WifiChannel>() {
        public WifiChannel createFromParcel(Parcel in) {
            return new WifiChannel(in.readInt());
        }

        public WifiChannel[] newArray(int size) {
            return new WifiChannel[size];
        }
    };
}
