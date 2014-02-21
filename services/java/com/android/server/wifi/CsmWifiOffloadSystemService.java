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

import android.content.Context;
import android.os.RemoteException;
import android.os.SystemClock;

import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.server.wifi.ICsmWifiOffloadSystemService;
import com.intel.cws.cwsservicemanagerclient.CsmClient;
/**
 * CsmWifiOffloadSystemService handles remote WiFi offload operation requests by implementing
 * the ICsmWifiOffloadSystemService interface.
 *
 * @hide
 */
public class CsmWifiOffloadSystemService extends ICsmWifiOffloadSystemService.Stub {

    private static final String CSM_SERVICE_TAG = "CsmWifiOffloadService";

    private static final boolean DEBUG = Log.isLoggable(CSM_SERVICE_TAG, Log.DEBUG);

    private CsmWifiOffloadClient CsmOffloadClient;

    public CsmWifiOffloadSystemService(Context context) {
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }

        CsmOffloadClient = new CsmWifiOffloadClient(context);
    }

    private static byte[] hexStringToByteArray(String s) {
        if (null == s) {
            return null;
        }

        // parameter size should be even; if not, add a not significant "0" on left
        String tmp = (s.length() % 2 == 0) ? s : ("0" + s);

        int len = tmp.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(tmp.charAt(i), 16) << 4)
                    + Character.digit(tmp.charAt(i+1), 16));
        }
        return data;
    }

    /**
    * Open channel with the UICC
    * It returns true if the channel has been correctly open or if it already exists
    * @hide
    */
    public boolean uiccOpenChannel() throws RemoteException {
        if (DEBUG) Log.d(CSM_SERVICE_TAG, "Entering uiccOpenChannel");

        if (CsmOffloadClient != null) {
            CsmOffloadClient.uiccBeginTransaction();
            return true;
        }
        return false;
    }
    /**
    * Close channel with the UICC
    * It returns true if the channel exists and has been correctly closed
    * @hide
    */
    public boolean uiccCloseChannel() throws RemoteException {
      if (DEBUG) Log.d(CSM_SERVICE_TAG, "Entering uiccCloseChannel");

      if (CsmOffloadClient != null) {
          CsmOffloadClient.uiccEndTransaction();
          return true;
      }
      return false;
    }

    /* Transmit APDU data to the UICC
    * It returns the string returned from the UICC (error codes included)
    * @hide
    */
    public String uiccTransmitAPDU(int inst, int p1, int p2, int p3, String command)
            throws RemoteException {
        if (DEBUG) Log.d(CSM_SERVICE_TAG, "Entering uiccTransmitAPDU");

        String ret_fail = new String("");

        if (CsmOffloadClient != null) {
            byte[] cmd = hexStringToByteArray(command);
            if (null == cmd) {
                Log.e(CSM_SERVICE_TAG, "uiccTransmitAPDU fail due to null command");
                return ret_fail;
            }

            String result = CsmOffloadClient.uiccTransmitAPDU(inst, p1 , p2, p3, cmd);
            if (DEBUG) Log.d(CSM_SERVICE_TAG, "uiccTransmitAPDU result: " + result);
            return result;
        }
        return ret_fail;
    }


    private class CsmWifiOffloadClient extends CsmClient {
        private static final long SIM_CHECK_DELAY_MS = 7000;

        private boolean mIsSimPresent;
        private long mModemUpTimestamp;

        public CsmWifiOffloadClient(Context context) {
            super(context, CSM_ID_WIFI_OFFLOAD, 1);
            if (DEBUG) Log.d(CSM_SERVICE_TAG, "Create CsmWifiOffloadClient object");
            csmActivateSimStatusReceiver();
            mIsSimPresent = false;
            mModemUpTimestamp = 0;
        }

        public void uiccBeginTransaction() throws RemoteException {
            if (DEBUG) {
                Log.d(CSM_SERVICE_TAG, "Entering CsmWifiOffloadClient / uiccBeginTransaction");
            }
            if (mIsSimPresent && (mCwsServiceMgr != null)) {
                mCwsServiceMgr.uiccBeginTransaction(CSM_ID_WIFI_OFFLOAD);
            }
            else {
                Log.e(CSM_SERVICE_TAG, "CsmWifiOffloadClient / Call "
                        + "uiccBeginTransaction whereas SIM is not present !");
            }
        }

        public void uiccEndTransaction() throws RemoteException {
            if (DEBUG) {
                Log.d(CSM_SERVICE_TAG, "Entering CsmWifiOffloadClient / uiccEndTransaction");
            }
            if (mIsSimPresent && (mCwsServiceMgr != null)) {
                mCwsServiceMgr.uiccEndTransaction(CSM_ID_WIFI_OFFLOAD);
            }
            else {
                Log.e(CSM_SERVICE_TAG, "CsmWifiOffloadClient / Call uiccEndTransaction"
                        + " whereas SIM is not present !");
            }
        }

        public String uiccTransmitAPDU(int inst, int p1, int p2, int p3, byte[] command)
            throws RemoteException {
            if (DEBUG) Log.d(CSM_SERVICE_TAG, "Entering CsmWifiOffloadClient / uiccTransmitAPDU");

            long delaySoFar = SystemClock.elapsedRealtime() - mModemUpTimestamp;
            if (mModemUpTimestamp != 0 && !mIsSimPresent && delaySoFar >= SIM_CHECK_DELAY_MS) {
                Log.e(CSM_SERVICE_TAG, "SIM presence = " + mIsSimPresent + " probably not consistent !!");
                mIsSimPresent = TelephonyManager.getDefault().hasIccCard();
                Log.e(CSM_SERVICE_TAG, "Reloaded SIM presence = " + mIsSimPresent);
            }

            if (mIsSimPresent && (mCwsServiceMgr != null)) {
                return mCwsServiceMgr.uiccTransmitAPDU(CSM_ID_WIFI_OFFLOAD, inst, p1, p2, p3, command);
            }
            else {
                Log.e(CSM_SERVICE_TAG, "CsmWifiOffloadClient / Call uiccTransmitAPDU "
                        + "whereas SIM is not present !");
            }

            String ret = new String("");
            return ret;
        }

        @Override
        public void csmClientModemAvailable() {
            if (DEBUG) Log.d(CSM_SERVICE_TAG, "Entering CsmWifiOffloadClient/..ModemAvailable");
            mModemUpTimestamp = SystemClock.elapsedRealtime();
            return;
        }

        @Override
        public void csmClientModemUnavailable() {
            if (DEBUG) Log.d(CSM_SERVICE_TAG, "Entering CsmWifiOffloadClient/..ModemUnavailable");
            mIsSimPresent = false;
            mModemUpTimestamp = 0;
            return;
        }

        @Override
        public void onSimLoaded() {
            if (DEBUG) Log.d(CSM_SERVICE_TAG, "Entering CsmWifiOffloadClient / onSimLoaded");
            mIsSimPresent = true;
            return;
        }

        @Override
        public void onSimAbsent() {
            if (DEBUG) Log.d(CSM_SERVICE_TAG, "Entering CsmWifiOffloadClient / onSimAbsent");
            mIsSimPresent = false;
            return;
        }
    }

}
