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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;

/**
 * @hide
 */
public final class OffloadManager {

    private static final String TAG = "OffloadManager";

    public static final String ACTION_SERVICE_BIND_ERROR =
            "android.net.smartconnectivity.offload.SERVICE_BIND_ERROR";
    public static final String ACTION_SERVICE_BOUND =
            "android.net.smartconnectivity.offload.SERVICE_BOUND";
    public static final String ACTION_SERVICE_UNBOUND =
            "android.net.smartconnectivity.offload.SERVICE_UNBOUND";

    private IOffloadService mService;
    private Context mContext;
    private static OffloadManager sInstance;

    private OffloadManager() {
    }

    /**
     * Gets the single instance of OffloadManager.
     *
     * @param context the context
     * @return single instance of OffloadManager
     */
    public static synchronized OffloadManager getInstance(Context context) {
        Log.v(TAG, "getInstance() called");
        if (sInstance == null) {
            sInstance = new OffloadManager();
        }
        if (!sInstance.initService(context)) {
            sInstance = null;
        }
        return sInstance;
    }

    /**
     * Releases the instance of OffloadManager. Remember to call the release, before
     * the Context gets destroyed.
     *
     * @param context the context
     */
    public static synchronized void release(Context context) {
        if (sInstance != null) {
            sInstance.releaseService(context);
        } else {
            Log.v(TAG, "release() called");
        }
    }

    /**
     * Release service.
     *
     * @param context the context
     */
    private void releaseService(Context context) {
        if (mServiceConnection != null) {
            try {
                Log.v(TAG, "Calling unbindservice() ");
                context.unbindService(mServiceConnection);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Exception handling  of releaseService()");
                return;
            }
            mContext.unregisterReceiver(mReceiver);
            mService = null;
        }
    }

    /**
     * Initialise the service.
     *
     * @param context the context
     * @return true, if successful
     */
    private boolean initService(Context context) {
        if (context == null) {
            Log.e(TAG, "initService(): context is null");
            return false;
        }
        mContext = context;

        if (mService == null) {
            Log.d(TAG, "initService(): mService is null");
            IntentFilter filter = new IntentFilter();
            filter.addAction(OffloadManager.ACTION_SERVICE_BIND_ERROR);
            mContext.registerReceiver(mReceiver, filter);
            boolean bind = context.bindService(new Intent(IOffloadService.class
                    .getName()), mServiceConnection, Context.BIND_AUTO_CREATE);
            return bind;
        }
        return true;
    }

    /**
     * Checks if the given context is bound to service.
     *
     * @return true, if is bound to service
     */
    public boolean isBoundToService() {
        if (mService == null) {
            return false;
        }
        return true;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG, "BroadcastReceiver " + action);
            if (OffloadManager.ACTION_SERVICE_BIND_ERROR.equals(action)) {
                context.unbindService(mServiceConnection);
            }
        }
    };

    // Class for getting notified when connected/disconnected with Service
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            /*
             * This is called when the connection with the service has been
             * established, giving us the service object we can use to
             * interact with the service. We are communicating with our
             * service through an IDL interface, so get a client-side
             * representation of that from the raw service object.
             */
            Log.d(TAG, "onServiceConnected");
            mService = IOffloadService.Stub.asInterface(service);
            Intent intent = new Intent(OffloadManager.ACTION_SERVICE_BOUND);
            mContext.sendBroadcast(intent);
        }

        public void onServiceDisconnected(ComponentName className) {
            /*
             * This is called when the connection with the service has been
             * unexpectedly disconnected -- that is, its process crashed.
             */
            Log.e(TAG, "onServiceDisconnected");
            mService = null;
            Intent intent = new Intent(OffloadManager.ACTION_SERVICE_UNBOUND);
            mContext.sendBroadcast(intent);
        }
    };

    /**
     * Responsible for establishment of S2b connection by establishing an
     * IKEv2/IPSec tunnel with the ePDG. ePDG FQDN is constructed by offload service MM in
     * accordance with the FQDN construction procedures using the PLMN IDs as
     * specified in TS24.302. Note: In case of initial attach,
     * the LinkAddresses and LinkDnses can be NULL.
     *
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
    public int setupDataCall(String apnType, String apnName, boolean isHandover,
            LinkProperties link) {
        int ret = PhoneConstants.APN_REQUEST_FAILED;

        boolean mobileDataEnabled = false;
        try {
            mobileDataEnabled = Settings.Global.getInt(
                    mContext.getContentResolver(),
                    Settings.Global.MOBILE_DATA) != 0;
        } catch (SettingNotFoundException e1) {
            Log.e(TAG, e1.toString());
        }
        try {
            if (mobileDataEnabled) {
                if (mService == null) {
                    Log.e(TAG, "Service is null");
                    return ret;
                }
                switch (mService.setupDataCall(apnType, apnName, isHandover, link)) {
                    case 0:
                        ret = PhoneConstants.APN_REQUEST_STARTED;
                        break;
                    default:
                        ret = PhoneConstants.APN_REQUEST_FAILED;
                }
            } else {
                Log.e(TAG, "Mobile data disabled, not setting up data call");
                ret = PhoneConstants.APN_REQUEST_FAILED;
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
        return ret;
    }

    /**
     * Responsible for cleaning up of the S2b connection
     *
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
    public int cleanUpDataCall(String apnType, String apnName, int cid) {
        try {
            if (mService != null) {
                return mService.cleanUpDataCall(apnType, apnName, cid);
            } else {
                Log.e(TAG, "Service is null");
                return PhoneConstants.APN_REQUEST_FAILED;
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
            return PhoneConstants.APN_REQUEST_FAILED;
        }
    }

    /**
     * Helps the ConnectivityService to check with offload service for the preferred access
     * for establishing the PDN connection associated with the APN.
     *
     * @param apnName the name of the APN
     * @param apnType the type of APN
     * @return true if the Wi-Fi is turned ON, associated and the preferred
     * access set as Wi-Fi in the policy provisioned either statically
     * or through ANDSF.
     */
    public boolean isPreferredAccesTypeWifi(String apnType, String apnName) {
        try {
            if (mService != null) {
                return mService.isPreferredAccesTypeWifi(apnType, apnName);
            }
            else {
                Log.e(TAG, "Service is null");
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
            return false;
        }
    }

    /**
     * Responsible of notifying offload service which APNs are started over mobile.
     *
     * @param apnType the type of APN
     * @param apnName the name of the APN
     * @param cid Connection ID specifies the specific PDN connection
     * @param apnState ordinal value of NetworkInfo.State.
     * @return false on error.
     */
    public boolean notifyMobileApnStatus(String apnType, String apnName,
            int cid, NetworkInfo.State apnState) {
        try {
            // TODO: remove state convertion
            int convertedState = 0;
            switch (apnState) {
                case CONNECTED:
                    convertedState = 1;
                    break;
                default:
                    convertedState = 2;
            }
            if (mService != null) {
                return mService.notifyMobileApnStatus(apnType, apnName,
                        cid, convertedState);
            }
            else {
                Log.e(TAG, "Service is null");
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
            return false;
        }
    }

    /**
     * Get whether S2B is enabled in offload service or not.
     *
     * @return true if enabled else false.
     */
    public boolean isS2bEnabled() {
        try {
            if (mService != null) {
                return mService.isS2bEnabled();
            }
            else {
                Log.e(TAG, "Service is null");
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
            return false;
        }
    }

    /**
     * Get whether S2B is enabled for default(MOBILE) APN in offload policy or not.
     *
     * @return true if enabled else false.
     */
    public boolean isS2bEnabledForDefaultApn() {
        try {
            if (mService != null) {
                return mService.isS2bEnabledForDefaultApn();
            }
            else {
                Log.e(TAG, "Service is null");
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
            return false;
        }
    }

    /**
     * Register all APN's for which offload service shall notify, when S2B connection
     * is available to be established.
     *
     * @param apnType the type of APN
     * @param apnName the name of the APN.
     * @return false on error.
     */
    public boolean registerApnForS2b(String apnType, String apnName) {
        try {
            if (mService != null) {
                return mService.registerApn(apnType, apnName);
            }
            else {
                Log.e(TAG, "Service is null");
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
            return false;
        }
    }

    /**
     * Subscribes a listener for offload events.
     */
    public void addEventListener(IOffloadEventListener listener) {
        try {
            if (mService != null) {
                mService.addEventListener(listener);
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Unsubscribes a listener for offload events.
     */
    public void removeEventListener(IOffloadEventListener listener) {
        try {
            if (mService != null) {
                mService.removeEventListener(listener);
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }
}
