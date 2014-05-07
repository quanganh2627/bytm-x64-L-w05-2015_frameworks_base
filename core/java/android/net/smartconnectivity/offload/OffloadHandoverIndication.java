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
public class OffloadHandoverIndication implements Parcelable {

    public static final int REASON_UNKNOWN = 0;
    public static final int REASON_S2B_TO_3GPP = 1;
    public static final int REASON_3GPP_TO_S2B = 2;

    public String mApnType;
    public int mReason;

    public OffloadHandoverIndication() {
        this("", REASON_UNKNOWN);
    }

    public OffloadHandoverIndication(String apnType, int reason) {
        mApnType = apnType;
        mReason = reason;
    }

    public OffloadHandoverIndication(Parcel p) {
        mApnType = p.readString();
        mReason = p.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mApnType);
        dest.writeInt(mReason);
    }

    /**
     * Parcelable creator
     */
    public static final Parcelable.Creator<OffloadHandoverIndication> CREATOR =
            new Parcelable.Creator<OffloadHandoverIndication>() {
                public OffloadHandoverIndication createFromParcel(Parcel source) {
                    return new OffloadHandoverIndication(source);
                }

                public OffloadHandoverIndication[] newArray(int size) {
                    return new OffloadHandoverIndication[size];
                }
            };

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("mApnType: ").append(mApnType);
        sb.append(" mReason: ").append(mReason);
        return sb.toString();
    }
}
