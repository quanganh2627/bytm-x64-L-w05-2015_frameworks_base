/*
 * Copyright 2014 Intel Corporation All Rights Reserved.
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

package com.android.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkStateTracker;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.util.Slog;

import com.intel.cam.api.CamManager;

/** CAM Application Continuity Specific Code **/
class CamApplicationContinuity {

    private Context mContext;
    private Handler mHandler;

    CamApplicationContinuity(ConnectivityService cs, Context context, Handler Handler) {
        mConnecitvityService = cs;
        mContext = context;
        mHandler = Handler;
    }

    void initializeAppContinuity() {
        // Get instance of CamManager
        mCamManager = CamManager.getInstance(mContext);

        mFilter = new IntentFilter();
        mFilter.addAction(CamManager.CAM_INTERFACE_CHANGE_ACTION);
        mFilter.addAction(CamManager.CAM_CONNECT_WWAN_ACTION);

        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (action.equals(CamManager.CAM_INTERFACE_CHANGE_ACTION)) {
                            log("Received CAM_INTERFACE_CHANGE_ACTION action in connectivity "
                                    + "Service with arg "
                                    + intent.getIntExtra(CamManager.EXTRA_INTERFACE_CHANGE, 1));
                            int value = intent.getIntExtra(CamManager.EXTRA_INTERFACE_CHANGE, 1);
                            sendCamMessage(action, value);
                        } else if (action.equals(CamManager.CAM_CONNECT_WWAN_ACTION)) {
                            log("Received CAM_CONNECT_WWAN_ACTION action in connectivity Service");
                            sendCamMessage(action, 0);
                        }
                    }
                }, mFilter);
    }

    // REMOVE THIS
    CamManager getCamManager() {
        return mCamManager;
    }

    void resetNeedToTearDown() {
        mNeedToTearDown = true;
    }

    boolean camNeedToTearDown(NetworkInfo info) {
        log("mNeedToTearDown: " + mNeedToTearDown + "Is App Continuity Enabled: "
                + isAppContinuityEnabled(info.getType()));
        return (mNeedToTearDown == true && !isAppContinuityEnabled(info.getType()));
    }

    void handleNewDefaultConnection(int newNetType, NetworkInfo info) {
        // This part of the code is required for handling the App Continuity
        // feature
        if (APP_CONT_DBG) {
            log("APPCONT:Policy requires Not Tearing Down mNeedToTearDown=" + mNeedToTearDown
                    + "isAppContinuityFeatureEnabled(): " + isAppContinuityFeatureEnabled());
        }
        if (isAppContinuityFeatureEnabled())
        {
            /*
             * When MOBILE network is brought up when already connected to a bad
             * WiFi network we need to send an event to CAM informing the bring-up
             * of WWAN
             */
            if (mNeedToTearDown == false && newNetType == ConnectivityManager.TYPE_MOBILE) {
                // send and Event

                Intent intent = new Intent(CamManager.CAM_WWAN_CONNECTED_ACTION);
                final long ident = Binder.clearCallingIdentity();

                try {
                    if (APP_CONT_DBG) {
                        log("APPCONT:Sending Event to CAM, WWAN is now up");
                    }
                    // mContext.sendBroadcast(intent);
                    mContext.sendBroadcast(intent);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
            /*
             * For handling tear down of WWAN when manual connection to WiFi network
             * (which is not CAM managed) is done and WWAN was up
             */
            else if (newNetType == ConnectivityManager.TYPE_WIFI) {
                if (APP_CONT_DBG) {
                    log("APPCONT: Try getting the Mobile State Tracker Explicitly calling teardown "
                            + "for 3G");
                }
                // NetworkStateTracker thisConnectedNet =
                // mNetTrackers[ConnectivityManager.TYPE_MOBILE];
                NetworkStateTracker thisConnectedNet = mConnecitvityService
                        .getNetworkStateTrackerInfo();
                NetworkInfo getWWANInfo = thisConnectedNet.getNetworkInfo();

                if (getWWANInfo != null) {
                    // Verify that the connection is a manual connection
                    if (!isAppContinuityEnabled(info.getType())) {
                        if (getWWANInfo.isConnectedOrConnecting()) {
                            if (APP_CONT_DBG) {
                                log("APPCONT: CAM_INTERFACE_CHANGE_ACTION - Calling "
                                        + "teardown on MOBILE");
                            }
                            mConnecitvityService.teardown(thisConnectedNet);
                        }
                        else if (APP_CONT_DBG) {
                            log("APPCONT: CAM_INTERFACE_CHANGE_ACTION - Did Not called teardown "
                                    + "on WWAN, not in connected state");
                        }
                    }
                }
                else if (APP_CONT_DBG) {
                    log("APPCONT: CAM_INTERFACE_CHANGE_ACTION - Found WWAN info NULL");
                }
            }
        }
    }

    /**
     * Handles the WWAN->WLAN and bring-up WWAN events
     */
    void handleInterfaceChangeEvents(int eventType, int eventData) {
        if (isAppContinuityFeatureEnabled())
        {
            // Event indicating selection of best WLAN network and request to
            // bring-down the WWAN
            if (eventType == EVENT_SET_CAM_INTERFACE_CHANGE_ACTION) {
                // Handles only 3G-WiFi case here
                log("APPCONT: CAM_INTERFACE_CHANGE_ACTION " + eventData);
                NetworkInfo info;
                if (eventData == 1) {
                    // 3G - WiFi case
                    info = mConnecitvityService.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                    // NetworkStateTracker thisConnectedNet =
                    // mNetTrackers[ConnectivityManager.TYPE_MOBILE];
                    NetworkStateTracker thisConnectedNet = mConnecitvityService
                            .getNetworkStateTrackerInfo();
                    if (thisConnectedNet != null) {
                        NetworkInfo getWWANInfo = thisConnectedNet.getNetworkInfo();
                        if (getWWANInfo != null) {
                            if (getWWANInfo.isConnectedOrConnecting()) {
                                log("APPCONT: CAM_INTERFACE_CHANGE_ACTION - Calling teardown "
                                        + "on MOBILE");
                                mConnecitvityService.teardown(thisConnectedNet);
                            } else {
                                log("APPCONT: CAM_INTERFACE_CHANGE_ACTION - Did Not called teardown"
                                        + " on WWAN, not in connected state");
                            }
                        } else {
                            log("APPCONT: CAM_INTERFACE_CHANGE_ACTION - Found WWAN info NULL");
                        }
                    }
                    // handleConnect(info);
                }
            }
            // Event requesting bring-up of the WWAN interface when connected WLAN
            // has bad quality
            else if (eventType == EVENT_SET_CAM_CONNECT_WWAN) {
                // Handles only WiFi-3G case here
                log("APPCONT: Event  EVENT_SET_CAM_CONNECT_WWAN ++");

                mNeedToTearDown = false;

                log("APPCONT: Try getting the Mobile State Tracker");
                // NetworkStateTracker checkTracker =
                // mNetTrackers[ConnectivityManager.TYPE_MOBILE];
                NetworkStateTracker checkTracker = mConnecitvityService.getNetworkStateTrackerInfo();
                if (checkTracker != null) {
                    NetworkInfo checkInfo = checkTracker.getNetworkInfo();

                    if (checkInfo != null) {
                        if (!checkInfo.isConnectedOrConnecting()
                               || checkTracker.isTeardownRequested()) {
                            checkInfo.setFailover(true);
                            log("APPCONT: Trying to reconnnect with Mobile");
                            checkTracker.reconnect();
                        }
                    }
                }
                else {
                    log("APPCONT: Mobile State Tracker being null");
                }
            }
        }
        else {
            log("APPCONT: Application Continuity Feature is Disabled");
        }
    }

    /**
     * Used for checking the 3G-WiFi case
     * @params: Type of the network: Mobile/WiFi/Bluetooth etc., This function
     *          checks specially for WLAN connections.
     * @returns: true if the connectivity change is going from 3G to WiFi
     */
    boolean isAppContinuityEnabled(int type) {
        if (null == mCamManager) {
            // Attempt to get the instance of Cam Manager again.
            // Will fail if Cam Service is not installed.
            mCamManager = CamManager.getInstance(mContext);
            if (mCamManager == null) {
                log("isAppContinuityEnabled() CAM is NULL");
                return false;
            }
        }

        if (ConnectivityManager.TYPE_WIFI != type) {
            log("isAppContinuityEnabled() Interface is not WiFi");
            return false;
        }

        if (!mCamManager.isBoundToService()) {
            log("isAppContinuityEnabled() Not bound to service");
            return false;
        }

        if (CamManager.CAM_STATE_ENABLED != mCamManager.getCamState()) {
            return false;
        } else {
            boolean mIsCamMobileConnectedNetwork = mCamManager.isConnectionForCamNetwork();
            if (mIsCamMobileConnectedNetwork && mCamManager.isSmartSelectionEnabled()) {
                log("isAppContinuityEnabled(): Returns true");
                return true;
            } else {
                log("isAppContinuityEnabled(): Returns false");
                return false;
            }
        }
    }

    /**
     * Used for verifying whether App Continuity Feature is Enabled
     * @returns: true if the App Continuity is enabled otherwise returns false
     */
    boolean isAppContinuityFeatureEnabled() {
        if (null == mCamManager) {
            log("isAppContinuityEnabled() CAM is NULL");
            // Attempt to get the instance of Cam Manager again.
            // Will fail if Cam Service is not installed.
            mCamManager = CamManager.getInstance(mContext);
            if (mCamManager == null) {
                log("isAppContinuityEnabled() CAM is NULL");
                return false;
            }
        }

        if (!mCamManager.isBoundToService()) {
            log("isAppContinuityEnabled() Not bound to service");
            return false;
        }

        if (CamManager.CAM_STATE_ENABLED != mCamManager.getCamState()) {
            return false;
        }

        return true;
    }

    /** CAM Application Continuity Specific Code **/

    /**
     * This method will be called from the CamApplicationContinuity code for sending events to
     * InternalHandler of Connectivity service.
     */
    void sendCamMessage(String eventType, int value) {
        if (eventType.equals(CamManager.CAM_INTERFACE_CHANGE_ACTION)) {
            Message msg = new Message();
            msg.what = EVENT_SET_CAM_INTERFACE_CHANGE_ACTION;
            msg.arg1 = value;
            mHandler.sendMessage(msg);
        }
        else if (eventType.equals(CamManager.CAM_CONNECT_WWAN_ACTION)) {
            Message msg = new Message();
            msg.what = EVENT_SET_CAM_CONNECT_WWAN;
            mHandler.sendMessage(msg);
        }
    }

    private void log(String s) {
        Slog.d(TAG, s);
    }

    private ConnectivityService mConnecitvityService;

    /**
     * Used internally for handling the Application Continuity feature, wherein
     * this flag will be set to false to explicitly inform the handleConnect not
     * to tear down the WWAN connection when already connected to WiFi network
     * When App Continuity feature is disabled this flag is never set to false
     * ensuring the normal flow of operations.
     */
    private boolean mNeedToTearDown = true;

    private CamManager mCamManager;
    private IntentFilter mFilter = null;
    // private AppContinuity appC;
    private static final String TAG = "[APCONT]";

    /**
     * Used internally to send a message when CAM requires WiFi network to be
     * set and mobile network to be teardown
     */
    private static final int EVENT_SET_CAM_INTERFACE_CHANGE_ACTION = 17;

    /**
     * Used internally to send a message when CAM requires connecting to WWAN
     * network before disconnecting WLAN network
     */
    private static final int EVENT_SET_CAM_CONNECT_WWAN = 18;

    /**
     * Used internally to enable Application Continuity feature AppContinuity
     * class uses these variables
     */
    private static final boolean APP_CONT_DBG = false;

}
