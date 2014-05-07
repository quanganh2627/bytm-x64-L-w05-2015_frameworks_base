/*
 * Copyright (C) 2014 Intel Corporation, All rights Reserved
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

import android.net.smartconnectivity.offload.OffloadConnectionStateChanged;
import android.net.smartconnectivity.offload.OffloadHandoverIndication;
import android.net.smartconnectivity.offload.OffloadAvailabilityIndication;

/**
 * Callback methods for network offload events.
 *
 *
 */
 /** {@hide} */
oneway interface IOffloadEventListener {

    /**
     * Notifies of a connection state change.
     */
    void onConnectionStateChanged(in OffloadConnectionStateChanged state);

    /**
     * Notifies of a handover indication.
     */
    void onHandoverIndication(in OffloadHandoverIndication indication);

    /**
     * Notifies of an availability indication.
     */
    void onAvailabilityIndication(in OffloadAvailabilityIndication indication);
}
