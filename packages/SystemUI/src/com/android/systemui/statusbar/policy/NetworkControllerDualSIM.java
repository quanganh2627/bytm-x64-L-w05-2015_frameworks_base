/*
 * Copyright (C) 2013 Capital Alliance Software LTD (Pekall)
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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wimax.WimaxManagerConstants;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyIntents2;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.internal.util.AsyncChannel;
import com.android.systemui.DemoMode;
import com.android.systemui.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

/**
 *   NewrokControllerDualSIM handles the logic in showing network related
 *   status on status bar.
 */
public class NetworkControllerDualSIM extends BroadcastReceiver implements DemoMode {
    // debug
    static final String TAG = "StatusBar.NetworkControllerDualSIM";
    static final boolean DEBUG = false;
    static final boolean CHATTY = false; // additional diagnostics, but not logspew

    private static final int FLIGHT_MODE_ICON = R.drawable.stat_sys_signal_flightmode;

    // telephony
    boolean mHspaDataDistinguishable;
    final TelephonyManager mPhone, mPhone2;
    boolean mDataConnected[] = new boolean[2];
    IccCardConstants.State mSimState[] = { IccCardConstants.State.UNKNOWN, IccCardConstants.State.UNKNOWN };
    int mPhoneState[] = {TelephonyManager.CALL_STATE_IDLE, TelephonyManager.CALL_STATE_IDLE};
    int mDataNetType[] = {TelephonyManager.NETWORK_TYPE_UNKNOWN, TelephonyManager.NETWORK_TYPE_UNKNOWN};
    int mDataState[] = {TelephonyManager.DATA_DISCONNECTED, TelephonyManager.DATA_DISCONNECTED};
    int mDataActivity[] = {TelephonyManager.DATA_ACTIVITY_NONE, TelephonyManager.DATA_ACTIVITY_NONE};
    boolean mToos[] = {false, false};
    ServiceState mServiceState[] = {null, null};
    SignalStrength mSignalStrength[] = {null, null};
    int mDataIconList[][] = {TelephonyIcons.DATA_G_SLOT1[0], TelephonyIcons.DATA_G_SLOT2[0]};
    String mNetworkName[] = {null, null};
    String mNetworkNameDefault;
    String mNetworkNameSeparator;
    boolean mNetworkNameChanged[] = {false, false};
    int mPhoneSignalIconId[] = new int[2];
    int mQSPhoneSignalIconId[] = new int[2];
    int mDataDirectionIconId[] = new int[2]; // data + data direction on phones
    int mDataSignalIconId[] = new int[2];
    int mDataTypeIconId[] = new int[2];
    int mQSDataTypeIconId[] = new int[2];
    int mAirplaneIconId;
    boolean mDataActive[] = new boolean[2];
    int mLastSignalLevel[] = new int[2];
    boolean mShowPhoneRSSIForData = false;
    boolean mShowAtLeastThreeGees = false;
    boolean mAlwaysShowCdmaRssi = false;

    String mContentDescriptionPhoneSignal[] = {null, null};
    String mContentDescriptionWifi;
    String mContentDescriptionWimax;
    String mContentDescriptionCombinedSignal;
    String mContentDescriptionDataType[] = {null, null};

    // wifi
    final WifiManager mWifiManager;
    AsyncChannel mWifiChannel;
    boolean mWifiEnabled, mWifiConnected;
    int mWifiRssi, mWifiLevel;
    String mWifiSsid;
    int mWifiIconId = 0;
    int mQSWifiIconId = 0;
    int mWifiActivity = WifiManager.DATA_ACTIVITY_NONE;

    // bluetooth
    private boolean mBluetoothTethered = false;
    private int mBluetoothTetherIconId =
        com.android.internal.R.drawable.stat_sys_tether_bluetooth;

    //wimax
    private boolean mWimaxSupported = false;
    private boolean mIsWimaxEnabled = false;
    private boolean mWimaxConnected = false;
    private boolean mWimaxIdle = false;
    private int mWimaxIconId = 0;
    private int mWimaxSignal = 0;
    private int mWimaxState = 0;
    private int mWimaxExtraState = 0;

    // data connectivity (regardless of state, can we access the internet?)
    // state of inet connection - 0 not connected, 100 connected
    private boolean mConnected = false;
    private int mConnectedNetworkType = ConnectivityManager.TYPE_NONE;
    private String mConnectedNetworkTypeName;
    private int mInetCondition = 0;
    private static final int INET_CONDITION_THRESHOLD = 50;

    private boolean mAirplaneMode = false;
    private boolean mLastAirplaneMode = true;

    private Locale mLocale = null;
    private Locale mLastLocale = null;

    // our ui
    Context mContext;
    ArrayList<TextView> mCombinedLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mMobileLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mWifiLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mEmergencyLabelViews = new ArrayList<TextView>();
    ArrayList<SignalCluster> mSignalClusters = new ArrayList<SignalCluster>();
    ArrayList<NetworkSignalChangedCallback> mSignalsChangedCallbacks =
            new ArrayList<NetworkSignalChangedCallback>();
    ArrayList<NetworkSignalChangedCallbackExt> mSignalsChangedCallbacks2 =
            new ArrayList<NetworkSignalChangedCallbackExt>();
    int mLastPhoneSignalIconId[] = {-1, -1};
    int mLastDataDirectionIconId[] = {-1, -1};
    int mLastWifiIconId = -1;
    int mLastWimaxIconId = -1;
    int mLastCombinedSignalIconId = -1;
    int mLastDataTypeIconId[] = {-1, -1};
    String mLastCombinedLabel = "";

    private boolean mHasMobileDataFeature;

    boolean mDataAndWifiStacked = false;

    public interface SignalCluster {
        void setWifiIndicators(boolean visible, int strengthIcon,
                String contentDescription);
        void setMobileDataIndicators(boolean visible, int strengthIcon,
                int typeIcon, String contentDescription, String typeContentDescription);
        void setMobileDataIndicators2(boolean visible, int strengthIcon,
                int typeIcon, String contentDescription, String typeContentDescription,
                int strengthIcon2, int typeIcon2, String contentDescription2,
                String typeContentDescription2);
        void setIsAirplaneMode(boolean is, int airplaneIcon);
    }

    public interface NetworkSignalChangedCallbackExt {
        void onMobileDataSignalChanged2(boolean enabled, int mobileSignalIconId,
                String mobileSignalContentDescriptionId, int dataTypeIconId,
                boolean activityIn, boolean activityOut,
                String dataTypeContentDescriptionId, String description);
    }

    /**
     * Construct this controller object and register for updates.
     */
    public NetworkControllerDualSIM(Context context) {
        mContext = context;
        final Resources res = context.getResources();

        ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mHasMobileDataFeature = cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);

        mShowPhoneRSSIForData = res.getBoolean(R.bool.config_showPhoneRSSIForData);
        mShowAtLeastThreeGees = res.getBoolean(R.bool.config_showMin3G);
        mAlwaysShowCdmaRssi = res.getBoolean(
                com.android.internal.R.bool.config_alwaysUseCdmaRssi);

        // set up the default wifi icon, used when no radios have ever appeared
        updateWifiIcons();
        updateWimaxIcons();

        // telephony
        mPhone = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        mPhone2 = TelephonyManager.get2ndTm();
        mPhone.listen(mPhoneStateListener,
                          PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_CALL_STATE
                        | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                        | PhoneStateListener.LISTEN_DATA_ACTIVITY);
        mPhone2.listen(mPhoneStateListener2,
                          PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_CALL_STATE
                        | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                        | PhoneStateListener.LISTEN_DATA_ACTIVITY);
        mHspaDataDistinguishable = mContext.getResources().getBoolean(
                R.bool.config_hspa_data_distinguishable);
        mNetworkNameSeparator = mContext.getString(R.string.status_bar_network_name_separator);
        mNetworkNameDefault = mContext.getString(
                com.android.internal.R.string.lockscreen_carrier_default);
        mNetworkName[0] = mNetworkName[1] = mNetworkNameDefault;

        // wifi
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Handler handler = new WifiHandler();
        mWifiChannel = new AsyncChannel();
        Messenger wifiMessenger = mWifiManager.getWifiServiceMessenger();
        if (wifiMessenger != null) {
            mWifiChannel.connect(mContext, handler, wifiMessenger);
        }

        // broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(TelephonyIntents2.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents2.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(ConnectivityManager.INET_CONDITION_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(TelephonyConstants.ACTION_DATA_SIM_SWITCH);
        filter.addAction(TelephonyConstants.ACTION_MODEM_FAST_OOS_IND);
        mWimaxSupported = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_wimaxEnabled);
        if(mWimaxSupported) {
            filter.addAction(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION);
        }
        context.registerReceiver(this, filter);

        // AIRPLANE_MODE_CHANGED is sent at boot; we've probably already missed it
        updateAirplaneMode();

        mLastLocale = mContext.getResources().getConfiguration().locale;
    }

    public boolean hasMobileDataFeature() {
        return mHasMobileDataFeature;
    }

    public boolean isEmergencyOnly(int slot) {
        return (mServiceState[slot] != null && mServiceState[slot].isEmergencyOnly());
    }

    public boolean isEmergencyOnly() {
        return isEmergencyOnly(0) && isEmergencyOnly(1);
    }

    public void addCombinedLabelView(TextView v) {
        mCombinedLabelViews.add(v);
    }

    public void addMobileLabelView(TextView v) {
        mMobileLabelViews.add(v);
    }

    public void addWifiLabelView(TextView v) {
        mWifiLabelViews.add(v);
    }

    public void addEmergencyLabelView(TextView v) {
        mEmergencyLabelViews.add(v);
    }

    public void addSignalCluster(SignalCluster cluster) {
        mSignalClusters.add(cluster);
        refreshSignalCluster(cluster);
    }

    public void addNetworkSignalChangedCallback(NetworkSignalChangedCallback cb) {
        mSignalsChangedCallbacks.add(cb);
        notifySignalsChangedCallbacks(cb);
    }

    public void addNetworkSignalChangedCallbackExt(NetworkSignalChangedCallbackExt cb) {
        mSignalsChangedCallbacks2.add(cb);
        notifySignalsChangedCallbacks2(cb);
    }

    public void refreshSignalCluster(SignalCluster cluster) {
        if (mDemoMode) return;
        cluster.setWifiIndicators(
                // only show wifi in the cluster if connected or if wifi-only
                mWifiEnabled && (mWifiConnected || !mHasMobileDataFeature),
                mWifiIconId,
                mContentDescriptionWifi);

        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is special
            cluster.setMobileDataIndicators(
                    true,
                    mAlwaysShowCdmaRssi ? mPhoneSignalIconId[0] : mWimaxIconId,
                    mDataTypeIconId[0],
                    mContentDescriptionWimax,
                    mContentDescriptionDataType[0]);
        } else {
            // normal mobile data
            cluster.setMobileDataIndicators2(
                    mHasMobileDataFeature,
                    mShowPhoneRSSIForData ? mPhoneSignalIconId[0] : mDataSignalIconId[0],
                    mDataTypeIconId[0],
                    mContentDescriptionPhoneSignal[0],
                    mContentDescriptionDataType[0],
                    mShowPhoneRSSIForData ? mPhoneSignalIconId[1] : mDataSignalIconId[1],
                    mDataTypeIconId[1],
                    mContentDescriptionPhoneSignal[1],
                    mContentDescriptionDataType[1]);
        }
        cluster.setIsAirplaneMode(mAirplaneMode, mAirplaneIconId);
    }

    void notifySignalsChangedCallbacks(NetworkSignalChangedCallback cb) {
        // only show wifi in the cluster if connected or if wifi-only
        boolean wifiEnabled = mWifiEnabled && (mWifiConnected || !mHasMobileDataFeature);
        String wifiDesc = wifiEnabled ?
                mWifiSsid : null;
        boolean wifiIn = wifiEnabled && mWifiSsid != null
                && (mWifiActivity == WifiManager.DATA_ACTIVITY_INOUT
                || mWifiActivity == WifiManager.DATA_ACTIVITY_IN);
        boolean wifiOut = wifiEnabled && mWifiSsid != null
                && (mWifiActivity == WifiManager.DATA_ACTIVITY_INOUT
                || mWifiActivity == WifiManager.DATA_ACTIVITY_OUT);
        cb.onWifiSignalChanged(wifiEnabled, mQSWifiIconId, wifiIn, wifiOut,
                mContentDescriptionWifi, wifiDesc);

        boolean mobileIn = mDataConnected[0] && (mDataActivity[0] == TelephonyManager.DATA_ACTIVITY_INOUT
                || mDataActivity[0] == TelephonyManager.DATA_ACTIVITY_IN);
        boolean mobileOut = mDataConnected[0] && (mDataActivity[0] == TelephonyManager.DATA_ACTIVITY_INOUT
                || mDataActivity[0] == TelephonyManager.DATA_ACTIVITY_OUT);
        if (isEmergencyOnly(0)) {
            cb.onMobileDataSignalChanged(false, mQSPhoneSignalIconId[0],
                    mContentDescriptionPhoneSignal[0], mQSDataTypeIconId[0], mobileIn, mobileOut,
                    mContentDescriptionDataType[0], null);
        } else {
            if (mIsWimaxEnabled && mWimaxConnected) {
                // Wimax is special
                cb.onMobileDataSignalChanged(true, mQSPhoneSignalIconId[0],
                        mContentDescriptionPhoneSignal[0], mQSDataTypeIconId[0], mobileIn, mobileOut,
                        mContentDescriptionDataType[0], mNetworkName[0]);
            } else {
                // Normal mobile data
                cb.onMobileDataSignalChanged(mHasMobileDataFeature, mQSPhoneSignalIconId[0],
                        mContentDescriptionPhoneSignal[0], mQSDataTypeIconId[0], mobileIn, mobileOut,
                        mContentDescriptionDataType[0], mNetworkName[0]);
            }
        }
        cb.onAirplaneModeChanged(mAirplaneMode);
    }

    void notifySignalsChangedCallbacks2(NetworkSignalChangedCallbackExt cb) {
        // only notify mobile data signal change on SIM 2
        boolean mobileIn = mDataConnected[1] && (mDataActivity[1] == TelephonyManager.DATA_ACTIVITY_INOUT
                || mDataActivity[1] == TelephonyManager.DATA_ACTIVITY_IN);
        boolean mobileOut = mDataConnected[1] && (mDataActivity[1] == TelephonyManager.DATA_ACTIVITY_INOUT
                || mDataActivity[1] == TelephonyManager.DATA_ACTIVITY_OUT);
        if (isEmergencyOnly(1)) {
            cb.onMobileDataSignalChanged2(false, mQSPhoneSignalIconId[1],
                    mContentDescriptionPhoneSignal[1], mQSDataTypeIconId[1], mobileIn, mobileOut, mContentDescriptionDataType[1],
                    null);
        } else {
            if (mIsWimaxEnabled && mWimaxConnected) {
                // Wimax is special
                cb.onMobileDataSignalChanged2(true, mQSPhoneSignalIconId[1],
                        mContentDescriptionPhoneSignal[1], mQSDataTypeIconId[1], mobileIn, mobileOut,
                        mContentDescriptionDataType[1], mNetworkName[1]);
            } else {
                // Normal mobile data
                cb.onMobileDataSignalChanged2(mHasMobileDataFeature, mQSPhoneSignalIconId[1],
                        mContentDescriptionPhoneSignal[1], mQSDataTypeIconId[1], mobileIn, mobileOut,
                        mContentDescriptionDataType[1], mNetworkName[1]);
            }
        }
    }

    public void setStackedMode(boolean stacked) {
        mDataAndWifiStacked = true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.RSSI_CHANGED_ACTION)
                || action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)
                || action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            updateWifiState(intent);
            refreshViews();
        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)
                || action.equals(TelephonyIntents2.ACTION_SIM_STATE_CHANGED)) {
            final int slot = intent.getIntExtra("slot", 0);
            updateSimState(intent);
            updateTelephonySignalStrength(slot);
            updateDataNetType(slot);
            updateDataIcon(slot);
            refreshViews();
        } else if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)
                || action.equals(TelephonyIntents2.SPN_STRINGS_UPDATED_ACTION)) {
            final int slot = intent.getIntExtra("slot", 0);
            updateNetworkName(slot, intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false),
                        intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                        intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                        intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
            refreshViews();
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) ||
                 action.equals(ConnectivityManager.INET_CONDITION_ACTION)) {
            updateConnectivity(intent);
            refreshViews();
        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            refreshViews();
        } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            updateAirplaneMode();
            refreshViews();
        } else if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            updateWimaxState(intent);
            refreshViews();
        } else if (action.equals(TelephonyConstants.ACTION_DATA_SIM_SWITCH)) {
            String stage = intent.getStringExtra(TelephonyConstants.EXTRA_SWITCH_STAGE);
            if (DEBUG) Log.d(TAG, "stage: " + stage);
            if (stage != null) {
                if (stage.equals(TelephonyConstants.SIM_SWITCH_END)) {
                    handlePrimarySimSwap();
                } else if (stage.equals(TelephonyConstants.PHONE_CREATION_DONE)) {
                    handlePrimarySimSwap();
                }
            }
        } else if (action.equals(TelephonyConstants.ACTION_MODEM_FAST_OOS_IND)) {
            String phoneName = intent.getStringExtra(TelephonyConstants.MODEM_PHONE_NAME_KEY);
            int slot = convertToSlotId(phoneName);
            boolean toos = intent.getBooleanExtra(TelephonyConstants.EXTRA_TOOS_STATE, true);
            if (toos == mToos[slot]) return;
            mToos[slot] = toos;
            if (DEBUG) Log.d(TAG, "TOOS in " + phoneName + ",state:" + mToos[slot]);
            updateTelephonySignalStrength(slot);
        }
    }


    // ===== Telephony ==============================================================

    abstract class PhoneStateListenerDualSIM extends PhoneStateListener {
        protected abstract int getSlot();

        /**
         * Return Phone identifier ID
         */
        protected String getPhoneName() {
            return "Phone[" + getSlot() + "]";
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (DEBUG) {
                Log.d(TAG, getPhoneName() + "onSignalStrengthsChanged signalStrength=" + signalStrength +
                    ((signalStrength == null) ? "" : (" level=" + signalStrength.getLevel())));
            }
            final int slot = getSlot();
            mSignalStrength[slot] = signalStrength;
            mDataNetType[slot] = getNetworkType(slot);
            updateTelephonySignalStrength(slot);
            updateDataNetType(slot);
            refreshViews();
        }

        @Override
        public void onServiceStateChanged(ServiceState state) {
            if (DEBUG) {
                Log.d(TAG, getPhoneName() + "onServiceStateChanged voiceState=" + state.getVoiceRegState()
                        + " dataState=" + state.getDataRegState());
            }
            final int slot = getSlot();
            mServiceState[slot] = state;
            if (hasService(slot) && mToos[slot]) {
                mToos[slot] = false;
            }
            mDataNetType[slot] = getNetworkType(slot);
            updateTelephonySignalStrength(slot);
            updateDataNetType(slot);
            updateDataIcon(slot);
            refreshViews();
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (DEBUG) {
                Log.d(TAG, getPhoneName() + "onCallStateChanged state=" + state);
            }
            final int slot = getSlot();
            // In cdma, if a voice call is made, RSSI should switch to 1x.
            if (isCdma(slot)) {
                updateTelephonySignalStrength(slot);
                refreshViews();
            }
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            if (DEBUG) {
                Log.d(TAG, getPhoneName() + ": onDataConnectionStateChanged: state=" + state
                        + " type=" + networkType);
            }
            final int slot = getSlot();
            mDataState[slot] = state;
            mDataNetType[slot] = networkType;
            updateDataNetType(slot);
            updateDataIcon(slot);
            refreshViews();
        }

        @Override
        public void onDataActivity(int direction) {
            if (DEBUG) {
                Log.d(TAG, getPhoneName() + "onDataActivity: direction=" + direction);
            }
            final int slot = getSlot();
            mDataActivity[slot] = direction;
            mDataNetType[slot] = getNetworkType(slot);
            updateDataIcon(slot);
            updateDataNetType(slot);
            refreshViews();
        }
    }

    PhoneStateListenerDualSIM mPhoneStateListener = new PhoneStateListenerDualSIM() {
        protected int getSlot() {
            return getDataSlot();
        }
    };

    PhoneStateListenerDualSIM mPhoneStateListener2 = new PhoneStateListenerDualSIM() {
        protected int getSlot() {
            return 1 - getDataSlot();
        }
    };

    int getTelephonyIntentsSlot(boolean isPrimary) {
        return ( isPrimary && getDataSimId() == TelephonyConstants.DSDS_SLOT_1_ID
                || !isPrimary && getDataSimId() == TelephonyConstants.DSDS_SLOT_2_ID ) ? 0 : 1;
    }

    private final void updateSimState(Intent intent) {
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        int slot = intent.getIntExtra("slot", 0);
        if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            mSimState[slot] = IccCardConstants.State.ABSENT;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
            mSimState[slot] = IccCardConstants.State.READY;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason =
                    intent.getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
            if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                mSimState[slot] = IccCardConstants.State.PIN_REQUIRED;
            }
            else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                mSimState[slot] = IccCardConstants.State.PUK_REQUIRED;
            }
            else {
                mSimState[slot] = IccCardConstants.State.NETWORK_LOCKED;
            }
        } else {
            mSimState[slot] = IccCardConstants.State.UNKNOWN;
        }
    }

    private boolean isCdma(int slot) {
        return (mSignalStrength[slot] != null) && !mSignalStrength[slot].isGsm();
    }

    private boolean hasService(int slot) {
        if (mServiceState[slot] != null) {
            // Consider the device to be in service if either voice or data service is available.
            // Some SIM cards are marketed as data-only and do not support voice service, and on
            // these SIM cards, we want to show signal bars for data service as well as the "no
            // service" or "emergency calls only" text that indicates that voice is not available.
            switch(mServiceState[slot].getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                    return false;
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    return mServiceState[slot].getDataRegState() == ServiceState.STATE_IN_SERVICE;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    private void updateAirplaneMode() {
        mAirplaneMode = (Settings.Global.getInt(mContext.getContentResolver(),
            Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
    }

    boolean isNetworkRoaming(int slot) {
        boolean ret =
            ((getDataSimId() == TelephonyConstants.DSDS_SLOT_1_ID) && (slot == 0)
            || (getDataSimId() == TelephonyConstants.DSDS_SLOT_2_ID) && (slot == 1)) ?
            mPhone.isNetworkRoaming() : mPhone2.isNetworkRoaming();
        if (ret) {
            if (DEBUG) Log.d(TAG, "roaming:" + ret + ",getDataSimId:" + getDataSimId() + ",slot:" + slot);
            if (DEBUG) Log.d(TAG, "2ndPhone isNetworkRoaming:" + mPhone2.isNetworkRoaming());
        }

        return ret;
    }

    private int getSignalIconNull(int slot) {
        if (mToos[slot]) {
            return slot == 0 ?
                R.drawable.stat_sys_signal_slot1_toos :
                R.drawable.stat_sys_signal_slot2_toos;
        }
        return slot == 0 ?
            R.drawable.stat_sys_signal_slot1_null :
            R.drawable.stat_sys_signal_slot2_null;
    }

    private final void updateTelephonySignalStrength(int slot) {
        if (mPhone.isSimOff(slot)) {
            if (DEBUG) Log.d(TAG, "updateTelephonySignalStrength, SIM_OFF on slot:" + slot);
            mPhoneSignalIconId[slot] = slot == 0 ?
                    R.drawable.stat_sys_signal_slot1_off : R.drawable.stat_sys_signal_slot2_off;
            mDataSignalIconId[slot] = mPhoneSignalIconId[slot];
            mQSPhoneSignalIconId[slot] = slot == 0 ?
                    R.drawable.ic_qs_signal_slot1_no_signal : R.drawable.ic_qs_signal_slot2_no_signal;
        } else if (mSimState[slot] == IccCardConstants.State.ABSENT) {
            if (DEBUG) Log.d(TAG, "updateTelephonySignalStrength, SIM_ABSENT on slot:" + slot);
            mPhoneSignalIconId[slot] = slot == 0 ?
                    R.drawable.stat_sys_slot1_no_sim : R.drawable.stat_sys_slot2_no_sim;
            mDataSignalIconId[slot] = mPhoneSignalIconId[slot];
            mQSPhoneSignalIconId[slot] = slot == 0 ?
                    R.drawable.ic_qs_signal_slot1_no_signal : R.drawable.ic_qs_signal_slot2_no_signal;
        } else if (!hasService(slot)) {
            if (CHATTY) Log.d(TAG, "updateTelephonySignalStrength: !hasService() on slot:" + slot);
            mPhoneSignalIconId[slot] = getSignalIconNull(slot);
            mDataSignalIconId[slot] = mPhoneSignalIconId[slot];
            mQSPhoneSignalIconId[slot] = slot == 0 ?
                    R.drawable.ic_qs_signal_slot1_no_signal : R.drawable.ic_qs_signal_slot2_no_signal;
        } else {
            if (mSignalStrength[slot] == null) {
                if (CHATTY) Log.d(TAG, "updateTelephonySignalStrength: mSignalStrength[" + slot + "] == null");
                mPhoneSignalIconId[slot] = slot == 0 ?
                        R.drawable.stat_sys_signal_slot1_0 : R.drawable.stat_sys_signal_slot2_0;
                mDataSignalIconId[slot] = mPhoneSignalIconId[slot];
                mQSPhoneSignalIconId[slot] = slot == 0 ?
                    R.drawable.ic_qs_signal_slot1_no_signal : R.drawable.ic_qs_signal_slot2_no_signal;
                mContentDescriptionPhoneSignal[slot] = mContext.getString(
                        AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0]);
            } else {
                int iconLevel;
                int[] iconList;
                if (isCdma(slot) && mAlwaysShowCdmaRssi) {
                    mLastSignalLevel[slot] = iconLevel = mSignalStrength[slot].getCdmaLevel();
                    if (DEBUG) Log.d(TAG, "mAlwaysShowCdmaRssi=" + mAlwaysShowCdmaRssi
                            + " set to cdmaLevel=" + mSignalStrength[slot].getCdmaLevel()
                            + " instead of level=" + mSignalStrength[slot].getLevel());
                } else {
                    mLastSignalLevel[slot] = iconLevel = mSignalStrength[slot].getLevel();
                }

                if (isCdma(slot)) {
                    if (isCdmaEri(slot)) {
                        iconList = slot == 0 ?
                                TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_ROAMING_SLOT1[mInetCondition]:
                                TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_ROAMING_SLOT2[mInetCondition];
                    } else {
                        iconList = slot == 0 ?
                                TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_SLOT1[mInetCondition]:
                                TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_SLOT2[mInetCondition];
                    }
                } else {
                    // Though mPhone is a Manager, this call is not an IPC
                    if (isNetworkRoaming(slot)) {
                        iconList = slot == 0 ?
                                TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_ROAMING_SLOT1[mInetCondition]:
                                TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_ROAMING_SLOT2[mInetCondition];
                    } else {
                        iconList = slot == 0 ?
                                TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_SLOT1[mInetCondition]:
                                TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_SLOT2[mInetCondition];
                    }
                }
                mPhoneSignalIconId[slot] = iconList[iconLevel];
                mQSPhoneSignalIconId[slot] = slot == 0?
                        TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH_SLOT1[mInetCondition][iconLevel]:
                        TelephonyIcons.QS_TELEPHONY_SIGNAL_STRENGTH_SLOT2[mInetCondition][iconLevel];
                mContentDescriptionPhoneSignal[slot] = mContext.getString(
                        AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[iconLevel]);
                mDataSignalIconId[slot] = mPhoneSignalIconId[slot];
            }
        }
    }

    private final void updateDataNetType() {
        updateDataNetType(0);
        updateDataNetType(1);
    }

    private final void updateDataNetType(int slot) {
        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is a special 4g network not handled by telephony
            mDataIconList[slot] = TelephonyIcons.DATA_4G[mInetCondition];
            mDataTypeIconId[slot] = R.drawable.stat_sys_data_fully_connected_4g;
            mQSDataTypeIconId[slot] = TelephonyIcons.QS_DATA_4G[mInetCondition];
            mContentDescriptionDataType[slot] = mContext.getString(
                    R.string.accessibility_data_connection_4g);
        } else {
            switch (mDataNetType[slot]) {
                case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                    if (!mShowAtLeastThreeGees) {
                        mDataIconList[slot] = slot == 0 ?
                                TelephonyIcons.DATA_G_SLOT1[mInetCondition]:
                                TelephonyIcons.DATA_G_SLOT2[mInetCondition];
                        mDataTypeIconId[slot] = 0;
                        mQSDataTypeIconId[slot] = 0;
                        mContentDescriptionDataType[slot] = mContext.getString(
                                R.string.accessibility_data_connection_gprs);
                        break;
                    } else {
                        // fall through
                    }
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    if (!mShowAtLeastThreeGees) {
                        mDataIconList[slot] = slot == 0 ?
                                TelephonyIcons.DATA_E_SLOT1[mInetCondition]:
                                TelephonyIcons.DATA_E_SLOT2[mInetCondition];
                        mDataTypeIconId[slot] = slot == 0 ?
                                R.drawable.stat_sys_data_connected_slot1_e:
                                R.drawable.stat_sys_data_connected_slot2_e;
                        mQSDataTypeIconId[slot] = slot == 0 ?
                                R.drawable.ic_qs_signal_slot1_full_e:
                                R.drawable.ic_qs_signal_slot2_full_e;
                        mContentDescriptionDataType[slot] = mContext.getString(
                                R.string.accessibility_data_connection_edge);
                        break;
                    } else {
                        // fall through
                    }
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    mDataIconList[slot] = slot == 0 ?
                            TelephonyIcons.DATA_3G_SLOT1[mInetCondition]:
                            TelephonyIcons.DATA_3G_SLOT2[mInetCondition];
                    mDataTypeIconId[slot] = slot == 0 ?
                            R.drawable.stat_sys_data_connected_slot1_3g:
                            R.drawable.stat_sys_data_connected_slot2_3g;
                    mQSDataTypeIconId[slot] = slot == 0 ?
                            R.drawable.ic_qs_signal_slot1_full_3g:
                            R.drawable.ic_qs_signal_slot2_full_3g;
                    mContentDescriptionDataType[slot] = mContext.getString(
                            R.string.accessibility_data_connection_3g);
                    break;
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    if (mHspaDataDistinguishable) {
                        mDataIconList[slot] = slot == 0 ?
                                TelephonyIcons.DATA_H_SLOT1[mInetCondition]:
                                TelephonyIcons.DATA_H_SLOT2[mInetCondition];
                        mDataTypeIconId[slot] = slot == 0 ?
                                R.drawable.stat_sys_data_connected_slot1_h:
                                R.drawable.stat_sys_data_connected_slot2_h;
                        mQSDataTypeIconId[slot] = slot == 0 ?
                                R.drawable.ic_qs_signal_slot1_full_h:
                                R.drawable.ic_qs_signal_slot2_full_h;
                        mContentDescriptionDataType[slot] = mContext.getString(
                                R.string.accessibility_data_connection_3_5g);
                    } else {
                        mDataIconList[slot] = slot == 0 ?
                                TelephonyIcons.DATA_3G_SLOT1[mInetCondition]:
                                TelephonyIcons.DATA_3G_SLOT1[mInetCondition];
                        mDataTypeIconId[slot] = slot == 0 ?
                                R.drawable.stat_sys_data_connected_slot1_3g:
                                R.drawable.stat_sys_data_connected_slot2_3g;
                        mQSDataTypeIconId[slot] = slot == 0 ?
                                R.drawable.ic_qs_signal_slot1_full_3g:
                                R.drawable.ic_qs_signal_slot2_full_3g;
                        mContentDescriptionDataType[slot] = mContext.getString(
                                R.string.accessibility_data_connection_3g);
                    }
                    break;
                case TelephonyManager.NETWORK_TYPE_CDMA:
                    if (!mShowAtLeastThreeGees) {
                        // display 1xRTT for IS95A/B
                        mDataIconList[slot] = TelephonyIcons.DATA_1X[mInetCondition];
                        mDataTypeIconId[slot] = R.drawable.stat_sys_data_fully_connected_1x;
                        mQSDataTypeIconId[slot] = R.drawable.ic_qs_signal_1x;
                        mContentDescriptionDataType[slot] = mContext.getString(
                                R.string.accessibility_data_connection_cdma);
                        break;
                    } else {
                        // fall through
                    }
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    if (!mShowAtLeastThreeGees) {
                        mDataIconList[slot] = TelephonyIcons.DATA_1X[mInetCondition];
                        mDataTypeIconId[slot] = R.drawable.stat_sys_data_fully_connected_1x;
                        mQSDataTypeIconId[slot] = R.drawable.ic_qs_signal_1x;
                        mContentDescriptionDataType[slot] = mContext.getString(
                                R.string.accessibility_data_connection_cdma);
                        break;
                    } else {
                        // fall through
                    }
                case TelephonyManager.NETWORK_TYPE_EVDO_0: //fall through
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                    mDataIconList[slot] = TelephonyIcons.DATA_3G[mInetCondition];
                    mDataTypeIconId[slot] = R.drawable.stat_sys_data_fully_connected_3g;
                    mQSDataTypeIconId[slot] = R.drawable.ic_qs_signal_3g;
                    mContentDescriptionDataType[slot] = mContext.getString(
                            R.string.accessibility_data_connection_3g);
                    break;
                case TelephonyManager.NETWORK_TYPE_LTE:
                    mDataIconList[slot] = TelephonyIcons.DATA_4G[mInetCondition];
                    mDataTypeIconId[slot] = R.drawable.stat_sys_data_fully_connected_4g;
                    mQSDataTypeIconId[slot] = R.drawable.ic_qs_signal_4g;
                    mContentDescriptionDataType[slot] = mContext.getString(
                            R.string.accessibility_data_connection_4g);
                    break;
                default:
                    if (!mShowAtLeastThreeGees) {
                        mDataIconList[slot] = slot == 0 ?
                                TelephonyIcons.DATA_G_SLOT1[mInetCondition]:
                                TelephonyIcons.DATA_G_SLOT2[mInetCondition];
                        mDataTypeIconId[slot] = slot == 0 ?
                                R.drawable.stat_sys_data_connected_slot1_g:
                                R.drawable.stat_sys_data_connected_slot2_g;
                        mQSDataTypeIconId[slot] = slot == 0 ?
                                R.drawable.ic_qs_signal_slot1_full_g:
                                R.drawable.ic_qs_signal_slot2_full_g;
                        mContentDescriptionDataType[slot] = mContext.getString(
                                R.string.accessibility_data_connection_gprs);
                    } else {
                        mDataIconList[slot] = slot == 0 ?
                                TelephonyIcons.DATA_3G_SLOT1[mInetCondition]:
                                TelephonyIcons.DATA_3G_SLOT2[mInetCondition];
                        mDataTypeIconId[slot] = slot == 0 ?
                                R.drawable.stat_sys_data_connected_slot1_3g:
                                R.drawable.stat_sys_data_connected_slot1_3g;
                        mQSDataTypeIconId[slot] = slot == 0 ?
                                R.drawable.ic_qs_signal_slot1_full_3g:
                                R.drawable.ic_qs_signal_slot2_full_3g;
                        mContentDescriptionDataType[slot] = mContext.getString(
                                R.string.accessibility_data_connection_3g);
                    }
                    break;
            }
        }

        if (isCdma(slot)) {
            if (isCdmaEri(slot)) {
                mDataTypeIconId[slot] = R.drawable.stat_sys_data_fully_connected_roam;
                mQSDataTypeIconId[slot] = R.drawable.ic_qs_signal_r;
            }
        } else if (isNetworkRoaming(slot)) {
            mDataTypeIconId[slot] = slot == 0 ?
                    R.drawable.stat_sys_data_connected_slot1_roam:
                    R.drawable.stat_sys_data_connected_slot2_roam;
            mQSDataTypeIconId[slot] = slot == 0?
                    R.drawable.ic_qs_signal_slot1_full_r:
                    R.drawable.ic_qs_signal_slot2_full_r;
        }
    }

    boolean isCdmaEri(int slot) {
        if (mServiceState[slot] != null) {
            final int iconIndex = mServiceState[slot].getCdmaEriIconIndex();
            if (iconIndex != EriInfo.ROAMING_INDICATOR_OFF) {
                final int iconMode = mServiceState[slot].getCdmaEriIconMode();
                if (iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL
                        || iconMode == EriInfo.ROAMING_ICON_MODE_FLASH) {
                    return true;
                }
            }
        }
        return false;
    }

    private final void updateDataIcon(int slot) {
        int iconId;
        boolean visible = true;

        if (!isCdma(slot)) {
            // GSM case, we have to check also the sim state
            if (mSimState[slot] == IccCardConstants.State.READY ||
                    mSimState[slot] == IccCardConstants.State.UNKNOWN) {
                if (hasService(slot) && mDataState[slot] == TelephonyManager.DATA_CONNECTED) {
                    switch (mDataActivity[slot]) {
                        case TelephonyManager.DATA_ACTIVITY_IN:
                            iconId = mDataIconList[slot][1];
                            break;
                        case TelephonyManager.DATA_ACTIVITY_OUT:
                            iconId = mDataIconList[slot][2];
                            break;
                        case TelephonyManager.DATA_ACTIVITY_INOUT:
                            iconId = mDataIconList[slot][3];
                            break;
                        default:
                            iconId = mDataIconList[slot][0];
                            break;
                    }
                    mDataDirectionIconId[slot] = iconId;
                } else {
                    iconId = 0;
                    visible = false;
                }
            } else {
                iconId = 0;
                visible = false; // no SIM? no data
            }
        } else {
            // CDMA case, mDataActivity can be also DATA_ACTIVITY_DORMANT
            if (hasService(slot) && mDataState[slot] == TelephonyManager.DATA_CONNECTED) {
                switch (mDataActivity[slot]) {
                    case TelephonyManager.DATA_ACTIVITY_IN:
                        iconId = mDataIconList[slot][1];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_OUT:
                        iconId = mDataIconList[slot][2];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_INOUT:
                        iconId = mDataIconList[slot][3];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_DORMANT:
                    default:
                        iconId = mDataIconList[slot][0];
                        break;
                }
            } else {
                iconId = 0;
                visible = false;
            }
        }

        mDataDirectionIconId[slot] = iconId;
        mDataConnected[slot] = visible;
    }

    void updateNetworkName(int slot, boolean showSpn, String spn, boolean showPlmn, String plmn) {
        if (false) {
            Log.d("CarrierLabel", "updateNetworkName slot: " + slot + " showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        StringBuilder str = new StringBuilder();
        boolean something = false;
        if (showPlmn && plmn != null) {
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (something) {
                str.append(mNetworkNameSeparator);
            }
            str.append(spn);
            something = true;
        }

        String oldNetworkName = mNetworkName[slot];
        if (something) {
            mNetworkName[slot] = str.toString();
        } else {
            mNetworkName[slot] = mNetworkNameDefault;
        }

        // handle case that operator name is updated late
        if (!TextUtils.equals(oldNetworkName, mNetworkName[slot])) {
            mNetworkNameChanged[slot] = true;
        }
    }

    // ===== Wifi ===================================================================

    class WifiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        mWifiChannel.sendMessage(Message.obtain(this,
                                AsyncChannel.CMD_CHANNEL_FULL_CONNECTION));
                    } else {
                        Log.e(TAG, "Failed to connect to wifi");
                    }
                    break;
                case WifiManager.DATA_ACTIVITY_NOTIFICATION:
                    if (msg.arg1 != mWifiActivity) {
                        mWifiActivity = msg.arg1;
                        refreshViews();
                    }
                    break;
                default:
                    //Ignore
                    break;
            }
        }
    }

    private void updateWifiState(Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            mWifiEnabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;

        } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            final NetworkInfo networkInfo = (NetworkInfo)
                    intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            boolean wasConnected = mWifiConnected;
            mWifiConnected = networkInfo != null && networkInfo.isConnected();
            // If we just connected, grab the inintial signal strength and ssid
            if (mWifiConnected && !wasConnected) {
                // try getting it out of the intent first
                WifiInfo info = (WifiInfo) intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                if (info == null) {
                    info = mWifiManager.getConnectionInfo();
                }
                if (info != null) {
                    mWifiSsid = huntForSsid(info);
                } else {
                    mWifiSsid = null;
                }
            } else if (!mWifiConnected) {
                mWifiSsid = null;
            }
        } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
            mWifiRssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -200);
            mWifiLevel = WifiManager.calculateSignalLevel(
                    mWifiRssi, WifiIcons.WIFI_LEVEL_COUNT);
        }

        updateWifiIcons();
    }

    private void updateWifiIcons() {
        if (mWifiConnected) {
            mWifiIconId = WifiIcons.WIFI_SIGNAL_STRENGTH[mInetCondition][mWifiLevel];
            mQSWifiIconId = WifiIcons.QS_WIFI_SIGNAL_STRENGTH[mInetCondition][mWifiLevel];
            mContentDescriptionWifi = mContext.getString(
                    AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH[mWifiLevel]);
        } else {
            if (mDataAndWifiStacked) {
                mWifiIconId = 0;
                mQSWifiIconId = 0;
            } else {
                mWifiIconId = mWifiEnabled ? R.drawable.stat_sys_wifi_signal_null : 0;
                mQSWifiIconId = mWifiEnabled ? R.drawable.ic_qs_wifi_no_network : 0;
            }
            mContentDescriptionWifi = mContext.getString(R.string.accessibility_no_wifi);
        }
    }

    private String huntForSsid(WifiInfo info) {
        String ssid = info.getSSID();
        if (ssid != null) {
            return ssid;
        }
        // OK, it's not in the connectionInfo; we have to go hunting for it
        List<WifiConfiguration> networks = mWifiManager.getConfiguredNetworks();
        for (WifiConfiguration net : networks) {
            if (net.networkId == info.getNetworkId()) {
                return net.SSID;
            }
        }
        return null;
    }


    // ===== Wimax ===================================================================
    private final void updateWimaxState(Intent intent) {
        final String action = intent.getAction();
        boolean wasConnected = mWimaxConnected;
        if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION)) {
            int wimaxStatus = intent.getIntExtra(WimaxManagerConstants.EXTRA_4G_STATE,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mIsWimaxEnabled = (wimaxStatus ==
                    WimaxManagerConstants.NET_4G_STATE_ENABLED);
        } else if (action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION)) {
            mWimaxSignal = intent.getIntExtra(WimaxManagerConstants.EXTRA_NEW_SIGNAL_LEVEL, 0);
        } else if (action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            mWimaxState = intent.getIntExtra(WimaxManagerConstants.EXTRA_WIMAX_STATE,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mWimaxExtraState = intent.getIntExtra(
                    WimaxManagerConstants.EXTRA_WIMAX_STATE_DETAIL,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mWimaxConnected = (mWimaxState ==
                    WimaxManagerConstants.WIMAX_STATE_CONNECTED);
            mWimaxIdle = (mWimaxExtraState == WimaxManagerConstants.WIMAX_IDLE);
        }
        updateDataNetType();
        updateWimaxIcons();
    }

    private void updateWimaxIcons() {
        if (mIsWimaxEnabled) {
            if (mWimaxConnected) {
                if (mWimaxIdle)
                    mWimaxIconId = WimaxIcons.WIMAX_IDLE;
                else
                    mWimaxIconId = WimaxIcons.WIMAX_SIGNAL_STRENGTH[mInetCondition][mWimaxSignal];
                mContentDescriptionWimax = mContext.getString(
                        AccessibilityContentDescriptions.WIMAX_CONNECTION_STRENGTH[mWimaxSignal]);
            } else {
                mWimaxIconId = WimaxIcons.WIMAX_DISCONNECTED;
                mContentDescriptionWimax = mContext.getString(R.string.accessibility_no_wimax);
            }
        } else {
            mWimaxIconId = 0;
        }
    }

    // ===== Full or limited Internet connectivity ==================================
    int getDataSlot() {
        return getDataSimId() == TelephonyConstants.DSDS_SLOT_1_ID ? 0 : 1;
    }

    private void updateConnectivity(Intent intent) {
        if (CHATTY) {
            Log.d(TAG, "updateConnectivity: intent=" + intent);
        }

        final ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo info = connManager.getActiveNetworkInfo();

        // Are we connected at all, by any interface?
        mConnected = info != null && info.isConnected();
        if (mConnected) {
            mConnectedNetworkType = info.getType();
            mConnectedNetworkTypeName = info.getTypeName();
        } else {
            mConnectedNetworkType = ConnectivityManager.TYPE_NONE;
            mConnectedNetworkTypeName = null;
        }

        int connectionStatus = intent.getIntExtra(ConnectivityManager.EXTRA_INET_CONDITION, 0);

        if (CHATTY) {
            Log.d(TAG, "updateConnectivity: networkInfo=" + info);
            Log.d(TAG, "updateConnectivity: connectionStatus=" + connectionStatus);
        }

        //DSDS shows colored synal status
        //mInetCondition = (connectionStatus > INET_CONDITION_THRESHOLD ? 1 : 0);

        if (info != null && info.getType() == ConnectivityManager.TYPE_BLUETOOTH) {
            mBluetoothTethered = info.isConnected();
        } else {
            mBluetoothTethered = false;
        }

        // We want to update all the icons, all at once, for any condition change
        updateDataNetType(0);
        updateDataNetType(1);
        updateWimaxIcons();
        updateDataIcon(0);
        updateDataIcon(1);
        updateTelephonySignalStrength(0);
        updateTelephonySignalStrength(1);
        updateWifiIcons();
    }


    // ===== Update the views =======================================================

    void refreshViews() {
        Context context = mContext;

        int combinedSignalIconId = 0;
        String combinedLabel = "";
        String wifiLabel = "";
        String mobileLabel = "";
        int N;
        final boolean emergencyOnly = isEmergencyOnly(0);
        final boolean emergencyOnly2 = isEmergencyOnly(1);

        final int slot = getDataSlot();
        if (!mHasMobileDataFeature) {
            mDataSignalIconId[0] = mPhoneSignalIconId[0] = mQSPhoneSignalIconId[0] = 0;
            mDataSignalIconId[1] = mPhoneSignalIconId[1] = mQSPhoneSignalIconId[1] = 0;
            mobileLabel = "";
        } else {
            // We want to show the carrier name if in service and either:
            //   - We are connected or not to mobile data, or
            //   - We are not connected to mobile data, as long as the *reason* packets are not
            //     being routed over that link is that we have better connectivity via wifi.
            // If data is disconnected for some other reason but wifi (or ethernet/bluetooth)
            // is connected, we show nothing.
            // If carrier name is empty and we are not conected we show "No internet connection".

            if (mDataConnected[0] || mDataConnected[1]) {
                if (mDataConnected[0]) {
                    mobileLabel = mNetworkName[0];
                }

                if (mDataConnected[0] && mDataConnected[1]) {
                    mobileLabel += mNetworkNameSeparator;
                }

                if (mDataConnected[1]) {
                    mobileLabel += mNetworkName[1];
                }
            } else if (mConnected || emergencyOnly || emergencyOnly2) {
                mobileLabel = "";
                if (hasService(0) || emergencyOnly) {
                    // The isEmergencyOnly test covers the case of a phone with no SIM
                    mobileLabel = mNetworkName[0];
                }

                if (mobileLabel.length() != 0 && (hasService(1) || emergencyOnly2)) {
                    mobileLabel += mNetworkNameSeparator;
                }

                if (hasService(1) || emergencyOnly2) {
                    mobileLabel = mNetworkName[1];
                }
            } else {
                mobileLabel
                    = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }

            // Now for things that should only be shown when actually using mobile data.
            int connectedSlot = -1;
            for (int i = 0; i < 2; i++) {
                if (mDataConnected[i]) {
                    connectedSlot = i;
                } else {
                    mDataTypeIconId[i] = 0;
                    mQSDataTypeIconId[i] = 0;
                }
            }

            if (connectedSlot != -1) {
                combinedSignalIconId = mDataSignalIconId[connectedSlot];
                combinedLabel = mobileLabel;
                mContentDescriptionCombinedSignal = mContentDescriptionDataType[connectedSlot];
            }
        }

        if (mWifiConnected) {
            if (mWifiSsid == null) {
                wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_wifi_nossid);
            } else {
                wifiLabel = mWifiSsid;
                if (DEBUG) {
                    wifiLabel += "xxxxXXXXxxxxXXXX";
                }
            }

            combinedLabel = wifiLabel;
            combinedSignalIconId = mWifiIconId; // set by updateWifiIcons()
            mContentDescriptionCombinedSignal = mContentDescriptionWifi;
        } else {
            if (mHasMobileDataFeature) {
                wifiLabel = "";
            } else {
                wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }
        }

        if (mBluetoothTethered) {
            combinedLabel = mContext.getString(R.string.bluetooth_tethered);
            combinedSignalIconId = mBluetoothTetherIconId;
            mContentDescriptionCombinedSignal = mContext.getString(
                    R.string.accessibility_bluetooth_tether);
        }

        final boolean ethernetConnected = (mConnectedNetworkType == ConnectivityManager.TYPE_ETHERNET);
        if (ethernetConnected) {
            combinedLabel = context.getString(R.string.ethernet_label);
        }

        if (mAirplaneMode &&
                (mServiceState[0] == null || (!hasService(0) && !mServiceState[0].isEmergencyOnly())) &&
                (mServiceState[1] == null || (!hasService(1) && !mServiceState[1].isEmergencyOnly()))) {
            // Only display the flight-mode icon if not in "emergency calls only" mode.

            // look again; your radios are now airplanes
            mContentDescriptionPhoneSignal[0] = mContext.getString(
                    R.string.accessibility_airplane_mode);
            mContentDescriptionPhoneSignal[1] = mContext.getString(
                    R.string.accessibility_airplane_mode);
            mAirplaneIconId = FLIGHT_MODE_ICON;
            mPhoneSignalIconId[0] = mDataSignalIconId[0] = mDataTypeIconId[0] = mQSDataTypeIconId[0] = 0;
            mPhoneSignalIconId[1] = mDataSignalIconId[1] = mDataTypeIconId[1] = mQSDataTypeIconId[1] = 0;
            mQSPhoneSignalIconId[0] = mQSPhoneSignalIconId[1] = 0;

            // combined values from connected wifi take precedence over airplane mode
            if (mWifiConnected) {
                // Suppress "No internet connection." from mobile if wifi connected.
                mobileLabel = "";
            } else {
                if (mHasMobileDataFeature) {
                    // let the mobile icon show "No internet connection."
                    wifiLabel = "";
                } else {
                    wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
                    combinedLabel = wifiLabel;
                }
                // both SIM are same.
                mContentDescriptionCombinedSignal = mContentDescriptionPhoneSignal[0];
                combinedSignalIconId = mDataSignalIconId[0];
            }
        } else if (!mDataConnected[0]  && !mDataConnected[1] && !mWifiConnected && !mBluetoothTethered && !mWimaxConnected && !ethernetConnected) {
            // pretty much totally disconnected

            combinedLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            // On devices without mobile radios, we want to show the wifi icon
            combinedSignalIconId = mHasMobileDataFeature ?
                    mDataSignalIconId[slot] : mWifiIconId;
            mContentDescriptionCombinedSignal = mHasMobileDataFeature ?
                    mContentDescriptionDataType[slot] : mContentDescriptionWifi;

            for (int i = 0; i < 2; i++) {
                if (isCdma(i)) {
                    if (isCdmaEri(i)) {
                        mDataTypeIconId[i] = R.drawable.stat_sys_data_fully_connected_roam;
                        mQSDataTypeIconId[i] = R.drawable.ic_qs_signal_r;
                    }
                } else if (isNetworkRoaming(i)) {
                    mDataTypeIconId[i] = i == 0 ?
                            R.drawable.stat_sys_data_connected_slot1_roam:
                            R.drawable.stat_sys_data_connected_slot2_roam;
                    mQSDataTypeIconId[i] = i == 0 ?
                            R.drawable.ic_qs_signal_slot1_full_r:
                            R.drawable.ic_qs_signal_slot2_full_r;
                }
            }
        }

        if (DEBUG) {
            Log.d(TAG, "refreshViews connected={"
                    + (mWifiConnected?" wifi":"")
                    + (mDataConnected[0]?" data[0]":"")
                    + (mDataConnected[1]?" data[1]":"")
                    + " } level="
                    + ((mSignalStrength[0] == null)?"??":Integer.toString(mSignalStrength[0].getLevel()))
                    + ((mSignalStrength[1] == null)?"??":Integer.toString(mSignalStrength[1].getLevel()))
                    + " combinedSignalIconId=0x"
                    + Integer.toHexString(combinedSignalIconId)
                    + "/" + getResourceName(combinedSignalIconId)
                    + " mobileLabel=" + mobileLabel
                    + " wifiLabel=" + wifiLabel
                    + " emergencyOnly=" + emergencyOnly
                    + " emergencyOnly2=" + emergencyOnly2
                    + " combinedLabel=" + combinedLabel
                    + " mAirplaneMode=" + mAirplaneMode
                    + " mDataActivity[0]=" + mDataActivity[0]
                    + " mPhoneSignalIconId[0]=0x" + Integer.toHexString(mPhoneSignalIconId[0])
                    + " mQSPhoneSignalIconId[0]=0x" + Integer.toHexString(mQSPhoneSignalIconId[0])
                    + " mDataDirectionIconId[0]=0x" + Integer.toHexString(mDataDirectionIconId[0])
                    + " mDataSignalIconId[0]=0x" + Integer.toHexString(mDataSignalIconId[0])
                    + " mDataTypeIconId[0]=0x" + Integer.toHexString(mDataTypeIconId[0])
                    + " mQSDataTypeIconId[0]=0x" + Integer.toHexString(mQSDataTypeIconId[0])
                    + " mDataActivity[1]=" + mDataActivity[1]
                    + " mPhoneSignalIconId[1]=0x" + Integer.toHexString(mPhoneSignalIconId[1])
                    + " mQSPhoneSignalIconId[1]=0x" + Integer.toHexString(mQSPhoneSignalIconId[1])
                    + " mDataDirectionIconId[1]=0x" + Integer.toHexString(mDataDirectionIconId[1])
                    + " mDataSignalIconId[1]=0x" + Integer.toHexString(mDataSignalIconId[1])
                    + " mDataTypeIconId[1]=0x" + Integer.toHexString(mDataTypeIconId[1])
                    + " mQSDataTypeIconId[1]=0x" + Integer.toHexString(mQSDataTypeIconId[1])
                    + " mWifiIconId=0x" + Integer.toHexString(mWifiIconId)
                    + " mQSWifiIconId=0x" + Integer.toHexString(mQSWifiIconId)
                    + " mBluetoothTetherIconId=0x" + Integer.toHexString(mBluetoothTetherIconId));
        }

        // update QS
        for (NetworkSignalChangedCallback cb : mSignalsChangedCallbacks) {
            notifySignalsChangedCallbacks(cb);
        }

        if (mLastPhoneSignalIconId[0]          != mPhoneSignalIconId[0]
         || mLastPhoneSignalIconId[1]          != mPhoneSignalIconId[1]
         || mLastDataDirectionIconId[0]        != mDataDirectionIconId[0]
         || mLastDataDirectionIconId[1]        != mDataDirectionIconId[1]
         || mLastWifiIconId                    != mWifiIconId
         || mLastWimaxIconId                   != mWimaxIconId
         || mLastDataTypeIconId[0]             != mDataTypeIconId[0]
         || mLastDataTypeIconId[1]             != mDataTypeIconId[1]
         || mLastAirplaneMode                  != mAirplaneMode
         || mNetworkNameChanged[0]
         || mNetworkNameChanged[1])
        {
            // NB: the mLast*s will be updated later
            for (SignalCluster cluster : mSignalClusters) {
                refreshSignalCluster(cluster);
            }
            for (NetworkSignalChangedCallbackExt cb : mSignalsChangedCallbacks2) {
                notifySignalsChangedCallbacks2(cb);
            }
        }

        if (mLastAirplaneMode != mAirplaneMode) {
            mLastAirplaneMode = mAirplaneMode;
        }

        if (mNetworkNameChanged[0]) {
            mNetworkNameChanged[0] = false;
        }

        if (mNetworkNameChanged[1]) {
            mNetworkNameChanged[1] = false;
        }

        // the phone icon on phones
        if (mLastPhoneSignalIconId[0] != mPhoneSignalIconId[0]) {
            mLastPhoneSignalIconId[0] = mPhoneSignalIconId[0];
        }

        // the phone2 icon on phones
        if (mLastPhoneSignalIconId[1] != mPhoneSignalIconId[1]) {
            mLastPhoneSignalIconId[1] = mPhoneSignalIconId[1];
        }
        // the data icon on phones
        if (mLastDataDirectionIconId[0] != mDataDirectionIconId[0]) {
            mLastDataDirectionIconId[0] = mDataDirectionIconId[0];
        }

        if (mLastDataDirectionIconId[1] != mDataDirectionIconId[1]) {
            mLastDataDirectionIconId[1] = mDataDirectionIconId[1];
        }

        // the wifi icon on phones
        if (mLastWifiIconId != mWifiIconId) {
            mLastWifiIconId = mWifiIconId;
        }

        // the wimax icon on phones
        if (mLastWimaxIconId != mWimaxIconId) {
            mLastWimaxIconId = mWimaxIconId;
        }
        // the combined data signal icon
        if (mLastCombinedSignalIconId != combinedSignalIconId) {
            mLastCombinedSignalIconId = combinedSignalIconId;
        }

        // the data network type overlay
        if (mLastDataTypeIconId[0] != mDataTypeIconId[0]) {
            mLastDataTypeIconId[0] = mDataTypeIconId[0];
        }

        if (mLastDataTypeIconId[1] != mDataTypeIconId[1]) {
            mLastDataTypeIconId[1] = mDataTypeIconId[1];
        }

        // the combinedLabel in the notification panel
        if (!mLastCombinedLabel.equals(combinedLabel)) {
            mLastCombinedLabel = combinedLabel;
            N = mCombinedLabelViews.size();
            for (int i=0; i<N; i++) {
                TextView v = mCombinedLabelViews.get(i);
                v.setText(combinedLabel);
            }
        }

        // wifi label
        N = mWifiLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mWifiLabelViews.get(i);
            v.setText(wifiLabel);
            if ("".equals(wifiLabel)) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }

        // mobile label
        N = mMobileLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mMobileLabelViews.get(i);
            v.setText(mobileLabel);
            if ("".equals(mobileLabel)) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }

        // e-call label
        N = mEmergencyLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mEmergencyLabelViews.get(i);
            if (!emergencyOnly) {
                v.setVisibility(View.GONE);
            } else {
                v.setText(mobileLabel); // comes from the telephony stack
                v.setVisibility(View.VISIBLE);
            }
        }
    }

    int getDataSimId() {
        int dataSimId = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.MOBILE_DATA_SIM,
                ConnectivityManager.MOBILE_DATA_NETWORK_SLOT_A);
        return dataSimId;
    }

    private void handlePrimarySimSwap() {
        int dataSimId = getDataSimId();
        Log.d(TAG, "dataSimId: " + dataSimId);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NetworkController state:");
        pw.println(String.format("  %s network type %d (%s)",
                mConnected?"CONNECTED":"DISCONNECTED",
                mConnectedNetworkType, mConnectedNetworkTypeName));
        pw.println("  - telephony ------");
        //SIM 0
        pw.print("  hasService(0)=");
        pw.println(hasService(0));
        pw.print("  mHspaDataDistinguishable=");
        pw.println(mHspaDataDistinguishable);
        pw.print("  mDataConnected[0]=");
        pw.println(mDataConnected[0]);
        pw.print("  mSimState[0]=");
        pw.println(mSimState[0]);
        pw.print("  mPhoneState[0]=");
        pw.println(mPhoneState[0]);
        pw.print("  mDataState[0]=");
        pw.println(mDataState[0]);
        pw.print("  mDataActivity[0]=");
        pw.println(mDataActivity[0]);
        pw.print("  mDataNetType[0]=");
        pw.print(mDataNetType[0]);
        pw.print("/");
        pw.println(TelephonyManager.getNetworkTypeName(mDataNetType[0]));
        pw.print("  mServiceState[0]=");
        pw.println(mServiceState[0]);
        pw.print("  mSignalStrength[0]=");
        pw.println(mSignalStrength[0]);
        pw.print("  mLastSignalLevel[0]=");
        pw.println(mLastSignalLevel[0]);
        pw.print("  mNetworkName[0]=");
        pw.println(mNetworkName[0]);
        pw.print("  mNetworkNameDefault=");
        pw.println(mNetworkNameDefault);
        pw.print("  mNetworkNameSeparator=");
        pw.println(mNetworkNameSeparator.replace("\n","\\n"));
        pw.print("  mPhoneSignalIconId[0]=0x");
        pw.print(Integer.toHexString(mPhoneSignalIconId[0]));
        pw.print("/");
        pw.println(getResourceName(mPhoneSignalIconId[0]));
        pw.print("  mQSPhoneSignalIconId[0]=0x");
        pw.print(Integer.toHexString(mQSPhoneSignalIconId[0]));
        pw.print("/");
        pw.println(getResourceName(mQSPhoneSignalIconId[0]));
        pw.print("  mDataDirectionIconId[0]=");
        pw.print(Integer.toHexString(mDataDirectionIconId[0]));
        pw.print("/");
        pw.println(getResourceName(mDataDirectionIconId[0]));
        pw.print("  mDataSignalIconId[0]=");
        pw.print(Integer.toHexString(mDataSignalIconId[0]));
        pw.print("/");
        pw.println(getResourceName(mDataSignalIconId[0]));
        pw.print("  mDataTypeIconId[0]=");
        pw.print(Integer.toHexString(mDataTypeIconId[0]));
        pw.print("/");
        pw.println(getResourceName(mDataTypeIconId[0]));
        pw.print("  mQSDataTypeIconId[0]=");
        pw.print(Integer.toHexString(mQSDataTypeIconId[0]));
        pw.print("/");
        pw.println(getResourceName(mQSDataTypeIconId[0]));

        //SIM 1
        pw.print("  hasService(1)=");
        pw.println(hasService(1));
        pw.print("  mHspaDataDistinguishable=");
        pw.println(mHspaDataDistinguishable);
        pw.print("  mDataConnected[1]=");
        pw.println(mDataConnected[1]);
        pw.print("  mSimState[1]=");
        pw.println(mSimState[1]);
        pw.print("  mPhoneState[1]=");
        pw.println(mPhoneState[1]);
        pw.print("  mDataState[1]=");
        pw.println(mDataState[1]);
        pw.print("  mDataActivity[1]=");
        pw.println(mDataActivity[1]);
        pw.print("  mDataNetType[1]=");
        pw.print(mDataNetType[1]);
        pw.print("/");
        pw.println(TelephonyManager.getNetworkTypeName(mDataNetType[1]));
        pw.print("  mServiceState[1]=");
        pw.println(mServiceState[1]);
        pw.print("  mSignalStrength[1]=");
        pw.println(mSignalStrength[1]);
        pw.print("  mLastSignalLevel[1]=");
        pw.println(mLastSignalLevel[1]);
        pw.print("  mNetworkName[1]=");
        pw.println(mNetworkName[1]);
        pw.print("  mNetworkNameDefault=");
        pw.println(mNetworkNameDefault);
        pw.print("  mNetworkNameSeparator=");
        pw.println(mNetworkNameSeparator.replace("\n","\\n"));
        pw.print("  mPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mPhoneSignalIconId[1]));
        pw.print("/");
        pw.print(Integer.toHexString(mQSPhoneSignalIconId[1]));
        pw.print("  mQSPhoneSignalIconId[1]=0x");
        pw.print("/");
        pw.println(getResourceName(mQSPhoneSignalIconId[1]));
        pw.print("  mDataDirectionIconId[1]=");
        pw.print(Integer.toHexString(mDataDirectionIconId[1]));
        pw.print("/");
        pw.println(getResourceName(mDataDirectionIconId[1]));
        pw.print("  mDataSignalIconId[1]=");
        pw.print(Integer.toHexString(mDataSignalIconId[1]));
        pw.print("/");
        pw.println(getResourceName(mDataSignalIconId[1]));
        pw.print("  mDataTypeIconId[1]=");
        pw.print(Integer.toHexString(mDataTypeIconId[1]));
        pw.print("/");
        pw.println(getResourceName(mDataTypeIconId[1]));
        pw.print("  mQSDataTypeIconId[1]=");
        pw.print(Integer.toHexString(mQSDataTypeIconId[1]));
        pw.print("/");
        pw.println(getResourceName(mQSDataTypeIconId[1]));

        pw.println("  - wifi ------");
        pw.print("  mWifiEnabled=");
        pw.println(mWifiEnabled);
        pw.print("  mWifiConnected=");
        pw.println(mWifiConnected);
        pw.print("  mWifiRssi=");
        pw.println(mWifiRssi);
        pw.print("  mWifiLevel=");
        pw.println(mWifiLevel);
        pw.print("  mWifiSsid=");
        pw.println(mWifiSsid);
        pw.println(String.format("  mWifiIconId=0x%08x/%s",
                    mWifiIconId, getResourceName(mWifiIconId)));
        pw.println(String.format("  mQSWifiIconId=0x%08x/%s",
                    mQSWifiIconId, getResourceName(mQSWifiIconId)));
        pw.print("  mWifiActivity=");
        pw.println(mWifiActivity);

        if (mWimaxSupported) {
            pw.println("  - wimax ------");
            pw.print("  mIsWimaxEnabled="); pw.println(mIsWimaxEnabled);
            pw.print("  mWimaxConnected="); pw.println(mWimaxConnected);
            pw.print("  mWimaxIdle="); pw.println(mWimaxIdle);
            pw.println(String.format("  mWimaxIconId=0x%08x/%s",
                        mWimaxIconId, getResourceName(mWimaxIconId)));
            pw.println(String.format("  mWimaxSignal=%d", mWimaxSignal));
            pw.println(String.format("  mWimaxState=%d", mWimaxState));
            pw.println(String.format("  mWimaxExtraState=%d", mWimaxExtraState));
        }

        pw.println("  - Bluetooth ----");
        pw.print("  mBtReverseTethered=");
        pw.println(mBluetoothTethered);

        pw.println("  - connectivity ------");
        pw.print("  mInetCondition=");
        pw.println(mInetCondition);

        pw.println("  - icons ------");
        pw.print("  mLastPhoneSignalIconId[0]=0x");
        pw.print(Integer.toHexString(mLastPhoneSignalIconId[0]));
        pw.print("/");
        pw.println(getResourceName(mLastPhoneSignalIconId[0]));
        pw.print("  mLastDataDirectionIconId[0]=0x");
        pw.print(Integer.toHexString(mLastDataDirectionIconId[0]));
        pw.print("/");
        pw.println(getResourceName(mLastDataDirectionIconId[0]));
        pw.print("  mLastPhoneSignalIconId[1]=0x");
        pw.print(Integer.toHexString(mLastPhoneSignalIconId[1]));
        pw.print("/");
        pw.println(getResourceName(mLastPhoneSignalIconId[1]));
        pw.print("  mLastDataDirectionIconId[1]=0x");
        pw.print(Integer.toHexString(mLastDataDirectionIconId[1]));
        pw.print("/");
        pw.println(getResourceName(mLastDataDirectionIconId[1]));
        pw.print("/");
        pw.print("  mLastWifiIconId=0x");
        pw.print(Integer.toHexString(mLastWifiIconId));
        pw.print("/");
        pw.println(getResourceName(mLastWifiIconId));
        pw.print("  mLastCombinedSignalIconId=0x");
        pw.print(Integer.toHexString(mLastCombinedSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mLastCombinedSignalIconId));
        pw.print("  mLastDataTypeIconId=0x");
        pw.print(Integer.toHexString(mLastDataTypeIconId[0]));
        pw.print("/");
        pw.println(getResourceName(mLastDataTypeIconId[0]));
        pw.print("  mLastCombinedLabel=");
        pw.print(mLastCombinedLabel);
        pw.println("");
    }

    private String getResourceName(int resId) {
        if (resId != 0) {
            final Resources res = mContext.getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private boolean mDemoMode;
    private int mDemoInetCondition;
    private int mDemoWifiLevel;
    private int mDemoDataTypeIconId;
    private int mDemoMobileLevel;

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoMode && command.equals(COMMAND_ENTER)) {
            mDemoMode = true;
            mDemoWifiLevel = mWifiLevel;
            mDemoInetCondition = mInetCondition;
            mDemoDataTypeIconId = mDataTypeIconId[0];
            mDemoMobileLevel = mLastSignalLevel[0];
        } else if (mDemoMode && command.equals(COMMAND_EXIT)) {
            mDemoMode = false;
            for (SignalCluster cluster : mSignalClusters) {
                refreshSignalCluster(cluster);
            }
        } else if (mDemoMode && command.equals(COMMAND_NETWORK)) {
            String airplane = args.getString("airplane");
            if (airplane != null) {
                boolean show = airplane.equals("show");
                for (SignalCluster cluster : mSignalClusters) {
                    cluster.setIsAirplaneMode(show, FLIGHT_MODE_ICON);
                }
            }
            String fully = args.getString("fully");
            if (fully != null) {
                mDemoInetCondition = Boolean.parseBoolean(fully) ? 1 : 0;
            }
            String wifi = args.getString("wifi");
            if (wifi != null) {
                boolean show = wifi.equals("show");
                String level = args.getString("level");
                if (level != null) {
                    mDemoWifiLevel = level.equals("null") ? -1
                            : Math.min(Integer.parseInt(level), WifiIcons.WIFI_LEVEL_COUNT - 1);
                }
                int iconId = mDemoWifiLevel < 0 ? R.drawable.stat_sys_wifi_signal_null
                        : WifiIcons.WIFI_SIGNAL_STRENGTH[mDemoInetCondition][mDemoWifiLevel];
                for (SignalCluster cluster : mSignalClusters) {
                    cluster.setWifiIndicators(
                            show,
                            iconId,
                            "Demo");
                }
            }
            String mobile = args.getString("mobile");
            if (mobile != null) {
                boolean show = mobile.equals("show");
                String datatype = args.getString("datatype");
                if (datatype != null) {
                    mDemoDataTypeIconId =
                            datatype.equals("1x") ? R.drawable.stat_sys_data_fully_connected_1x :
                            datatype.equals("3g") ? R.drawable.stat_sys_data_fully_connected_3g :
                            datatype.equals("4g") ? R.drawable.stat_sys_data_fully_connected_4g :
                            datatype.equals("e") ? R.drawable.stat_sys_data_fully_connected_e :
                            datatype.equals("g") ? R.drawable.stat_sys_data_fully_connected_g :
                            datatype.equals("h") ? R.drawable.stat_sys_data_fully_connected_h :
                            datatype.equals("lte") ? R.drawable.stat_sys_data_fully_connected_lte :
                            datatype.equals("roam")
                                    ? R.drawable.stat_sys_data_fully_connected_roam :
                            0;
                }
                int[][] icons = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH;
                String level = args.getString("level");
                if (level != null) {
                    mDemoMobileLevel = level.equals("null") ? -1
                            : Math.min(Integer.parseInt(level), icons[0].length - 1);
                }
                int iconId = mDemoMobileLevel < 0 ? R.drawable.stat_sys_signal_null :
                        icons[mDemoInetCondition][mDemoMobileLevel];
                for (SignalCluster cluster : mSignalClusters) {
                    cluster.setMobileDataIndicators(
                            show,
                            iconId,
                            mDemoDataTypeIconId,
                            "Demo",
                            "Demo");
                }
            }
        }
    }

    private int convertToSlotId(String phoneName) {
        if ("GSM".equals(phoneName)) {
            return getDataSlot();
        } else {
            return 1 - getDataSlot();
        }
    }

    private int getNetworkType(int slot) {
        TelephonyManager tm = TelephonyManager.getTmBySlot(slot);
        return ((tm != null) ? tm.getNetworkType() : TelephonyManager.NETWORK_TYPE_UNKNOWN);
    }

}
