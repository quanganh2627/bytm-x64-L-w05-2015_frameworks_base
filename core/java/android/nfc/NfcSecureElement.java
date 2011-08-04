/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.nfc;

import android.nfc.tech.TagTechnology;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;

/**
 * This class provides the primary API for managing all embedded Secure Element aspects.
 * @hide
 */
public final class NfcSecureElement {

    private static final String TAG = "NfcSecureElement";
    private INfcSecureElement mService;

    /**
     * @hide
     */
    public NfcSecureElement(INfcSecureElement mSecureElementService) {
        mService = mSecureElementService;
    }

    /**
     * Open a connection to the embedded Secure Element
     *
     * @param seType type of the Secure Element to be used:
     *
     * @return handle to be used to communicate with the Secure Element
     */
    public int openSecureElementConnection(String seType) throws IOException {
        if (seType.equals(NfcAdapter.SMART_MX_ID)) { //{@link NfcAdapter#SMART_MX_ID}
            try {
                int handle = mService.openSecureElementConnection();
                // Handle potential errors
                if (handle != 0) {
                    return handle;
                } else {
                    throw new IOException("SmartMX connection not allowed");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in openSecureElementConnection(): ", e);
                throw new IOException("RemoteException in openSecureElementConnection()");
            }
        } else if (seType.equals(NfcAdapter.UICC_ID)) {
            throw new IOException("UICC connection not supported");
        } else {
            throw new IOException("Unknown Secure Element type");
        }
    }

    /**
     * Send data to the embedded Secure Element
     *
     * @param handle Secure Element handle
     * @param data Data to be send to the Secure Element
     * @return Secure Element response in a byte array
     */
    public byte [] exchangeAPDU(int handle,byte [] data) throws IOException {
        // Perform exchange APDU
        try {
            byte[] response = mService.exchangeAPDU(handle, data);
            // Handle potential errors
            if (response == null) {
                throw new IOException("Exchange APDU failed");
            }
            return response;
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in exchangeAPDU(): ", e);
            throw new IOException("RemoteException in exchangeAPDU()");
        }
    }

    /**
     * Close the embedded Secure Element connection
     *
     * @param handle Secure Element handle
     */
    public void closeSecureElementConnection(int handle) throws IOException {
        try {
            int status = mService.closeSecureElementConnection(handle);
            // Handle potential errors
            if (ErrorCodes.isError(status)) {
                throw new IOException("Error during the conection close");
            };
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in closeSecureElement(): ", e);
            throw new IOException("RemoteException in closeSecureElement()");
        }
    }


    /**
     * Returns target type. constants.
     *
     * @return Secure Element technology type. The possible values are defined in {@link TagTechnology}
     *
     */
    public int[] getSecureElementTechList(int handle) throws IOException {
        try {
            return mService.getSecureElementTechList(handle);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in getSecureElementTechList(): ", e);
            throw new IOException("RemoteException in getSecureElementTechList()");
        }
    }

    /**
     * Returns Secure Element UID.
     *
     * @return Secure Element UID.
     */
    public byte[] getSecureElementUid(int handle) throws IOException {
        byte[] uid = null;
        try {
            uid = mService.getSecureElementUid(handle);
            // Handle potential errors
            if (uid == null) {
                throw new IOException("Get Secure Element UID failed");
            }
            return uid;
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in getSecureElementUid(): ", e);
            throw new IOException("RemoteException in getSecureElementUid()");
        }
    }

}
