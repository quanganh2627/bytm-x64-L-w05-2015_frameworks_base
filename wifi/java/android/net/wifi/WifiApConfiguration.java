/*
 * Copyright (C) 2008 The Android Open Source Project
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


/**
 * A class representing a configured Wi-Fi hotspot.
 * @hide
 */
public class WifiApConfiguration extends WifiConfiguration {

    public WifiChannel channel;
    public String hwMode;
    public boolean is80211n;

    public static final String HW_MODE_A = "a";
    public static final String HW_MODE_AC = "c";
    public static final String HW_MODE_BG = "g";

    /** @hide */
    public WifiApConfiguration() {
        super();
        channel = new WifiChannel(WifiChannel.DEFAULT_2_4_CHANNEL);
        hwMode = HW_MODE_BG;
        is80211n = true;
    }

    /** @hide */
    public WifiApConfiguration(WifiConfiguration src) {
        this();
        this.networkId = src.networkId;
        this.status = src.status;
        this.disableReason = src.disableReason;
        this.SSID = src.SSID;
        this.BSSID = src.BSSID;
        this.bgscan = src.bgscan;
        this.preSharedKey = src.preSharedKey;
        this.wepKeys = src.wepKeys;
        this.wepTxKeyIndex = src.wepTxKeyIndex;
        this.priority = src.priority;
        this.hiddenSSID = src.hiddenSSID;
        this.allowedKeyManagement = src.allowedKeyManagement;
        this.allowedProtocols = src.allowedProtocols;
        this.allowedAuthAlgorithms = src.allowedAuthAlgorithms;
        this.allowedPairwiseCiphers = src.allowedPairwiseCiphers;
        this.allowedGroupCiphers = src.allowedGroupCiphers;
        this.enterpriseFields = src.enterpriseFields;
        this.ipAssignment = src.ipAssignment;
        this.proxySettings = src.proxySettings;
        this.linkProperties = src.linkProperties;
    }

    /** Implement the Parcelable interface {@hide} */
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface {@hide} */
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(channel, flags);
        dest.writeString(hwMode);
        dest.writeInt(is80211n ? 1 : 0);
    }

    /** @hide */
    public void resetRadioConfig() {
        channel = new WifiChannel(WifiChannel.DEFAULT_2_4_CHANNEL);
        hwMode = HW_MODE_BG;
        is80211n = true;
    }

    /** @hide */
    public boolean isRadioDefault() {
        return (channel.getChannel() == WifiChannel.DEFAULT_2_4_CHANNEL &&
                hwMode == HW_MODE_BG && is80211n);
    }

    /** Implement the Parcelable interface {@hide} */
    public static final Creator<WifiApConfiguration> CREATOR =
        new Creator<WifiApConfiguration>() {
            public WifiApConfiguration createFromParcel(Parcel in) {
                WifiConfiguration superConfig = WifiConfiguration.CREATOR.createFromParcel(in);
                WifiApConfiguration config = new WifiApConfiguration(superConfig);
                config.channel = in.readParcelable(null);
                config.hwMode = in.readString();
                config.is80211n = (in.readInt() == 1);
                return config;
            }

            public WifiApConfiguration[] newArray(int size) {
                return new WifiApConfiguration[size];
            }
        };
}
