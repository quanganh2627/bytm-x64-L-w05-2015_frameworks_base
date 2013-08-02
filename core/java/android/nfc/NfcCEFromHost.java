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
 * This class provides the primary API for managing all host Card Emulation aspects.
 * @hide
 */
public final class NfcCEFromHost {

    private static final String TAG = "NfcCEFromHost";
    private INfcCEFromHost mService;

    /**
     * @hide
     */
    public NfcCEFromHost(INfcCEFromHost mCEFromHostService) {
        mService = mCEFromHostService;
    }

    /** TODO: change comments
     * Open a connection to the embedded Secure Element
     *
     * @return handle to be used to communicate with the Secure Element
     */
    public boolean setCEFromHostTypeA(String pkg, byte sak, byte[] atqa, byte[] app_data) throws IOException {
        try {
            boolean status = mService.setCEFromHostTypeA(pkg, sak, atqa, app_data);
            // Handle potential errors
            if (status) {
                return status;
            } else {
                throw new IOException("Unable to Enable Host Card Emulation");
            }
         } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in setCEFromHostTypeA(): ", e);
                throw new IOException("RemoteException in setCEFromHostTypeA()");
         }
    }

    /** TODO: change comments
     * Open a connection to the embedded Secure Element
     *
     * @return handle to be used to communicate with the Secure Element
     */
    public boolean setCEFromHostTypeB(String pkg, byte[] atqb, byte[] hi_layer_resp, int afi) throws IOException {
        try {
            boolean status = mService.setCEFromHostTypeB(pkg, atqb, hi_layer_resp, afi);
            // Handle potential errors
            if (status) {
                return status;
            } else {
                throw new IOException("Unable to Enable Host Card Emulation");
            }
         } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in setCEFromHostTypeB(): ", e);
                throw new IOException("RemoteException in setCEFromHostTypeB()");
         }
    }


    /** TODO: change comments
     * Open a connection to the embedded Secure Element
     *
     * @return handle to be used to communicate with the Secure Element
     */
    public void resetCEFromHostType(String pkg) throws IOException {
        try {
            mService.resetCEFromHostType(pkg);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in resetCEFromHostType(): ", e);
            throw new IOException("RemoteException in resetCEFromHostType()");
        }
    }


    /** TODO: change comments
     * Open a connection to the embedded Secure Element
     *
     * @return handle to be used to communicate with the Secure Element
     */
    public byte[] receiveCEFromHost(String pkg) throws IOException {
        // Perform Receive
        try {
            byte[] response = mService.receiveCEFromHost(pkg);
            // Handle potential errors
            if (response == null) {
                throw new IOException("Receive APDU failed");
            }
            return response;
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in receiveCEFromHost(): ", e);
            throw new IOException("RemoteException in receiveCEFromHost()");
        }
    }

    /** TODO: change comments
     * Open a connection to the embedded Secure Element
     *
     * @param data Data to be send to the Secure Element
     *
     * @return handle to be used to communicate with the Secure Element
     */
    public boolean sendCEFromHost(String pkg, byte [] data) throws IOException {
        // Perform Send
        try {
            boolean response = mService.sendCEFromHost(pkg, data);
            // Handle potential errors
            if (response != true) {
                throw new IOException("Send APDU failed");
            }
            return response;
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in sendCEFromHost(): ", e);
            throw new IOException("RemoteException in sendCEFromHost()");
        }
    }

}
