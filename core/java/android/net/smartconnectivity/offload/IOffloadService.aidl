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

import android.net.LinkProperties;

import android.net.smartconnectivity.offload.IOffloadEventListener;

/**
 * Interface used to control network offload events.
 * @hide
 */
 /** {@hide} */
interface IOffloadService {

    /**
     * Helps the ConnectivityService to check with offload service for the preferred access
     * for establishing the PDN connection associated with the APN.
     * @param apnName the name of the APN
     * @param apnType the type of APN
     * @return true if the Wi-Fi is turned ON, associated and the preferred
     * access set as Wi-Fi in the policy provisioned either statically
     * or through ANDSF.
     */
    boolean isPreferredAccesTypeWifi(String apnType, String apnName);

    /**
     * Responsible for establishment of S2b connection by establishing an
     * IKEv2/IPSec tunnel with the ePDG. ePDG FQDN is constructed by offload service MM in
     * accordance with the FQDN construction procedures using the PLMN IDs as
     * specified in TS24.302. Note: In case of initial attach,
     * the LinkAddresses and LinkDnses can be NULL.
     * @param apnType the type of APN
     * @param apnName the name of the APN
     * @param isHandover flag that specifies whether it's a handover/initial
     * attach
     * @param link link properties to initiate the connection with.
     * @return <code>APN_ALREADY_ACTIVE</code> if the current APN services the requested type.<br/>
     * <code>APN_TYPE_NOT_AVAILABLE</code> if the policy does not support the requested APN.<br/>
     * <code>APN_REQUEST_STARTED</code> if the request has been initiated.<br/>
     * <code>APN_REQUEST_FAILED</code> if the request was invalid.<br/>
     */
    int setupDataCall(String apnType, String apnName, boolean isHandover,
            in LinkProperties link);

    /**
     * Responsible for cleaning up of the S2b connection
     * @param apnType the type of APN for tearing down the PDN
     * connection associated with the APN
     * @param apnName the name of the APN for tearing down the PDN
     * connection associated with the APN
     * @param cid Connection ID specifies the specific PDN connection
     * to be cleaned up if there are more than 1 PDN connection associated with
     * a specific APN. Caller specifies Connection ID as -1 in case all
     * connections are to be torn don on the specific APN.
     * @return <code>APN_ALREADY_ACTIVE</code> if the default APN
     * is already active.<br/>
     * <code>APN_REQUEST_STARTED</code> if the request to switch to the default
     * APN has been initiated.<br/>
     * <code>APN_REQUEST_FAILED</code> if the request was invalid.<br/>
     */
    int cleanUpDataCall(String apnType, String apnName, int cid);

    /**
     * Responsible of notifying offload service which APNs are started over mobile.
     * @param apnType the type of APN
     * @param apnName the name of the APN
     * @param cid Connection ID specifies the specific PDN connection
     * @param apnState ordinal value of NetworkInfo.State.
     * @return false on error.
     */
    boolean notifyMobileApnStatus(String apnType, String apnName, int cid, int apnState);

    /**
     * Get whether S2B is enabled in offload service or not.
     * @return true if enabled else false.
     */
    boolean isS2bEnabled();

    /**
     * Get whether S2B is enabled for default(MOBILE) APN in offload policy or not.
     * @return true if enabled else false.
     */
    boolean isS2bEnabledForDefaultApn();

    /**
     * Register all APN's for which offload service shall notify availability when S2B connection
     * is available to be established.
     * @param apnType the type of APN
     * @param apnName the name of the APN.
     * @return false on error.
     */
    boolean registerApn(String apnType, String apnName);

    /**
     * Subscribes a listener for offload events.
     */
    void addEventListener(IOffloadEventListener listener);

    /**
     * Unsubscribes a listener for offload events.
     */
    void removeEventListener(IOffloadEventListener listener);
}
