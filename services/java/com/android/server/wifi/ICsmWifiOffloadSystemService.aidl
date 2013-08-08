/*
 * Copyright (C) Intel Corporation 2013
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

package com.android.server.wifi;


/**
 * Example of defining an interface for calling on to a remote service
 * (running in another process).
 * @hide
 */
interface ICsmWifiOffloadSystemService {
     /**
     * Open channel with the UICC
     * It returns true if the channel has been correctly open or if it already exists
     * @hide
     */
     boolean uiccOpenChannel();
     /**
     * Close channel with the UICC
     * It returns true if the channel exists and has been correctly closed
     * @hide
     */
     boolean uiccCloseChannel();
     /**
     * Transmit APDU data to the UICC
     * It returns the string returned from the UICC (error codes included)
     * @hide
     */
     String uiccTransmitAPDU(int inst, int p1, int p2, int p3, String command);
}