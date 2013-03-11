/*
 * Copyright 2010 Giesecke & Devrient GmbH.
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

import java.util.Map;
import java.util.TreeMap;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import org.simalliance.openmobileapi.service.ISmartcardService;
import org.simalliance.openmobileapi.service.ISmartcardSystemService;
import org.simalliance.openmobileapi.service.ISmartcardServiceCallback;
import org.simalliance.openmobileapi.service.SmartcardError;
import android.util.Log;


public class SmartcardSystemService extends ISmartcardSystemService.Stub {

    public static final String SMARTCARD_SERVICE_TAG = "SmartcardSystemService";
    private static final boolean LOCAL_LOGD = false;

    public volatile Exception lastException;

    private volatile ISmartcardService smartcardService;

    private final ISmartcardServiceCallback callback = new ISmartcardServiceCallback.Stub() {};

    private static String bytesToString(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    private byte[] stringToByteArray(String s) {
        byte[] b = new byte[s.length() / 2];
        for(int i = 0; i < b.length; i++)
            b[i] = (byte) Integer.parseInt(s.substring(2*i, 2*i+2), 16);
        return b;
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public SmartcardSystemService(Context context) {
        super();
        if (context == null)
            throw new NullPointerException("context must not be null");

        ServiceConnection connection = new ServiceConnection() {
            public synchronized void onServiceConnected(ComponentName className, IBinder service) {
                smartcardService = ISmartcardService.Stub.asInterface(service);
                if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "Smartcard system service onServiceConnected");
            }

            public void onServiceDisconnected(ComponentName className) {
                smartcardService = null;
                if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "Smartcard system service onServiceDisconnected");
            }
        };

        context.bindService(new Intent(ISmartcardService.class.getName()), connection, Context.BIND_AUTO_CREATE);
    }

    public void closeChannel(long hChannel) throws RemoteException {
        if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "called: closeChannel(" + hChannel +")");
        SmartcardError error = new SmartcardError();
        smartcardService.closeChannel(hChannel, error);
        lastException = error.createException();
        if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "SmartcardError: " + error.toString());
    }

    public String getReaders() throws RemoteException {
        if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "called: getReaders()");
        SmartcardError error = new SmartcardError();
        String[] result = smartcardService.getReaders(error);
        for (int i=0; i<result.length; i++) {
            Log.i(SMARTCARD_SERVICE_TAG, "getReaders(" + i + ") returned: " + result[i]);
        }
        lastException = error.createException();
        String readerlist = "";
        for (int i=0; i<result.length;++i) {
            readerlist += result[i];
            readerlist += ";";      //add separator
        }
        return readerlist;
    }

    public boolean isCardPresent(String reader) throws RemoteException {
        SmartcardError error = new SmartcardError();
        boolean result = smartcardService.isCardPresent(reader, error);
        if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "isCardPresent(" + reader + ") returned: " + result);
        lastException = error.createException();
        return result;
    }

    public long openBasicChannel(String reader) throws RemoteException {
        if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "called: openBasicChannel(" + reader + ")");
        SmartcardError error = new SmartcardError();
        long channelValue =smartcardService.openBasicChannel(reader, callback, error);
        lastException = error.createException();
        return channelValue;
    }

    public long openBasicChannelAid(String reader, String aid) throws RemoteException {
        if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "called: openBasicChannelAid (" + reader + ")");
        SmartcardError error = new SmartcardError();
        long channelValue =smartcardService.openBasicChannelAid(reader, stringToByteArray(aid), callback, error);
        lastException = error.createException();
        return channelValue;
    }

    public long openLogicalChannel(String reader, String aid) throws RemoteException {
        if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "called: openLogicalChannel(" + reader + ", " + aid + ")");
        SmartcardError error = new SmartcardError();
        long channelValue = smartcardService.openLogicalChannel(reader, stringToByteArray(aid), callback, error);
        lastException = error.createException();
        return channelValue;
    }

    public String transmit(long hChannel, String command) throws RemoteException {
        if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "called: transmit(" + hChannel + ", " + command + ")");
        SmartcardError error = new SmartcardError();
        byte[] cmd = hexStringToByteArray(command);
        if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "transmitting: " + bytesToString(cmd));
        String strResponse = "";
        try {
            byte[] rsp = smartcardService.transmit(hChannel, cmd, error);
            if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "transmit returned: " + bytesToString(rsp));
            strResponse = bytesToString(rsp);
        } catch (Exception e) {
            Log.w(SMARTCARD_SERVICE_TAG, "transmit exception: " + e.toString());
            Log.w(SMARTCARD_SERVICE_TAG, "transmit Error object: " + error.toString());
        }
        if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "transmit returned: " + strResponse);
        lastException = error.createException();
        return strResponse;
    }

    public String getLastError() {
        if (LOCAL_LOGD) Log.i(SMARTCARD_SERVICE_TAG, "called: getLastError");
        String strErrorMessage = new String("");
        if (lastException != null) {
            strErrorMessage = lastException.getMessage();
            if (strErrorMessage == null) {
                strErrorMessage = lastException.toString();
            }
            Log.w(SMARTCARD_SERVICE_TAG, "getLastError - message "+strErrorMessage);
        }
        return strErrorMessage;
    }
}
