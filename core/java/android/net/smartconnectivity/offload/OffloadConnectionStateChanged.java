/*
 * Copyright (C) 2014 Intel Corporation, All Rights Reserved
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

package android.net.smartconnectivity.offload;

import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * @hide
 */
public class OffloadConnectionStateChanged implements Parcelable {

    private NetworkInfo.DetailedState mState;
    private String mReason;
    private String mApnType;
    private boolean mIsRoaming;
    private boolean mIsAvailable;
    private LinkProperties mLinkProperties = new LinkProperties();
    private LinkCapabilities mLinkCapabilities = new LinkCapabilities();

    public OffloadConnectionStateChanged() {
        this(NetworkInfo.DetailedState.DISCONNECTED, "", "", false, false);
    }

    public OffloadConnectionStateChanged(NetworkInfo.DetailedState state,
            String reason,
            String apnType,
            boolean isRoaming,
            boolean isAvailable) {
        mState = state;
        mReason = reason;
        mApnType = apnType;
        mIsRoaming = isRoaming;
        mIsAvailable = isAvailable;
    }

    public OffloadConnectionStateChanged(Parcel p) {
        mState = NetworkInfo.DetailedState.values()[p.readInt()];
        mReason = p.readString();
        mApnType = p.readString();
        mIsRoaming = p.readInt() != 0;
        mIsAvailable = p.readInt() != 0;
        mLinkProperties = p.readParcelable(LinkProperties.class.getClassLoader());
        mLinkCapabilities = p.readParcelable(LinkCapabilities.class.getClassLoader());
    }

    public void setState(NetworkInfo.DetailedState state) {
        mState = state;
    }

    public NetworkInfo.DetailedState getState() {
        return mState;
    }

    public String getReason() {
        return mReason;
    }

    public void setReason(String reason) {
        this.mReason = reason;
    }

    public String getApnType() {
        return mApnType;
    }

    public void setApnType(String apnType) {
        this.mApnType = apnType;
    }

    public boolean isRoaming() {
        return mIsRoaming;
    }

    public void setRoaming(boolean roaming) {
        mIsRoaming = roaming;
    }

    public boolean isAvailable() {
        return mIsAvailable;
    }

    public void setAvailable(boolean available) {
        mIsAvailable = available;
    }

    public LinkProperties getLinkProperties() {
        return mLinkProperties;
    }

    public void setLinkProperties(LinkProperties linkProperties) {
        if (linkProperties == null) {
            this.mLinkProperties = new LinkProperties();
        } else {
            this.mLinkProperties = linkProperties;
        }
    }

    public LinkCapabilities getLinkCapabilities() {
        return mLinkCapabilities;
    }

    public void setLinkCapabilities(LinkCapabilities linkCapabilities) {
        if (linkCapabilities == null) {
            this.mLinkCapabilities = new LinkCapabilities();
        } else {
            this.mLinkCapabilities = linkCapabilities;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mState.ordinal());
        dest.writeString(getReason());
        dest.writeString(getApnType());
        dest.writeInt(mIsRoaming ? 1 : 0);
        dest.writeInt(mIsAvailable ? 1 : 0);
        dest.writeParcelable(getLinkProperties(), flags);
        dest.writeParcelable(getLinkCapabilities(), flags);
    }

    /**
     * Parcelable creator
     */
    public static final Parcelable.Creator<OffloadConnectionStateChanged> CREATOR =
            new Parcelable.Creator<OffloadConnectionStateChanged>() {
                public OffloadConnectionStateChanged createFromParcel(Parcel source) {
                    return new OffloadConnectionStateChanged(source);
                }

                public OffloadConnectionStateChanged[] newArray(int size) {
                    return new OffloadConnectionStateChanged[size];
                }
            };

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("state: ").append(mState);
        sb.append(" reason :").append(mReason);
        sb.append(" apnType: ").append(mApnType);
        sb.append(" roaming: ").append(mIsRoaming);
        sb.append(" available: ").append(mIsAvailable);
        sb.append(" linkProperties: ").append(mLinkProperties);
        sb.append(" linkCapabilities: ").append(mLinkCapabilities);
        return sb.toString();
    }
}
