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

import android.os.Parcel;
import android.os.Parcelable;

/**
 * @hide
 */
public class OffloadAvailabilityIndication implements Parcelable {

    public String mApnType;
    public boolean mAvailable;
    public int mReason;

    public OffloadAvailabilityIndication() {
        this("", false, OffloadHandoverIndication.REASON_UNKNOWN);
    }

    public OffloadAvailabilityIndication(String apnType, boolean available, int reason) {
        mApnType = apnType;
        mAvailable = available;
        mReason = reason;
    }

    public OffloadAvailabilityIndication(Parcel p) {
        mApnType = p.readString();
        mAvailable = p.readInt() != 0;
        mReason = p.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mApnType);
        dest.writeInt(mAvailable ? 1 : 0);
        dest.writeInt(mReason);
    }

    /**
     * Parcelable creator
     */
    public static final Parcelable.Creator<OffloadAvailabilityIndication> CREATOR =
            new Parcelable.Creator<OffloadAvailabilityIndication>() {
                public OffloadAvailabilityIndication createFromParcel(Parcel source) {
                    return new OffloadAvailabilityIndication(source);
                }

                public OffloadAvailabilityIndication[] newArray(int size) {
                    return new OffloadAvailabilityIndication[size];
                }
            };

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("mApnType: ").append(mApnType);
        sb.append(" mAvailable: ").append(mAvailable);
        sb.append(" mReason: ").append(mReason);
        return sb.toString();
    }
}
