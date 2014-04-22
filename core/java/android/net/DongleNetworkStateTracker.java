/*
 * Copyright (C) 2007-2011 Borqs Ltd. All rights reserved.
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
package android.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.os.Messenger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@hide}
 */
public class DongleNetworkStateTracker implements NetworkStateTracker {
    private static final String TAG = "Dongle";
    private static final boolean DBG = !android.os.Build.TYPE.equalsIgnoreCase("user");

    private static final String NETWORKTYPE = "DONGLE";
    private static final String ACTION_NETWORK_STATE = "dongle.borqs.com.DONGLE_STATE_CHANGED";

    private Handler mCsHandler;
    private Context mContext;

    private AtomicBoolean mTeardownRequested = new AtomicBoolean(false);
    private AtomicBoolean mPrivateDnsRouteSet = new AtomicBoolean(false);
    private AtomicBoolean mDefaultRouteSet = new AtomicBoolean(false);

    private NetworkInfo mNetworkInfo;
    private LinkProperties mLinkProperties;
    private LinkCapabilities mLinkCapabilities;
    private static String mIfName = "";

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction(); 
            if (action.equals(ACTION_NETWORK_STATE)) {
                NetworkInfo ni = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                if (ni != null) {
                    mNetworkInfo.setIsAvailable(ni.isAvailable());
                    mNetworkInfo.setDetailedState(ni.getDetailedState(), ni.getReason(), ni.getExtraInfo());

                    if (ni.isConnected()) {
                        mIfName = ni.getExtraInfo();
                        makeLinkProperties();
                    } else {
                        mLinkProperties.clear();
                        mIfName = "";
                    }
                    Message msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
                    msg.sendToTarget();
                }
            }
        }
    };

    private void makeLinkProperties() {
        LinkProperties p = mLinkProperties;
        p.setInterfaceName(mIfName);
        if (makeLinkAddress(p)) {
            makeDnsAddresses(p);
            makeGateways(p);
        }
    }

    private void makeGateways(LinkProperties p) {
        String sysGateways = SystemProperties.get("net." + mIfName + ".gw");

        String[] gateways;
        if (sysGateways != null) {
            gateways = sysGateways.split(" ");
        } else {
            gateways = new String[0];
        }

        if (DBG) {
            Slog.d(TAG, "Gateways are " + sysGateways);
        }

        for (String addr : gateways) {
            addr = addr.trim();
            if (addr.isEmpty()) continue;
            InetAddress ia;
            try {
                ia = NetworkUtils.numericToInetAddress(addr);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (! ia.isAnyLocalAddress()) {
                p.addRoute(new RouteInfo(ia));
            }
        }
    }

    private void makeDnsAddresses(LinkProperties p) {
        String propertyPrefix = "net." + mIfName + ".";
        String dnsServers[] = new String[2];
        dnsServers[0] = SystemProperties.get(propertyPrefix + "dns1");
        dnsServers[1] = SystemProperties.get(propertyPrefix + "dns2");

        if (DBG) {
            Slog.d(TAG, "DNSs are " + dnsServers);
        }

        for (String addr : dnsServers) {
            addr = addr.trim();
            if (addr.isEmpty()) continue;
            InetAddress ia;
            try {
                ia = NetworkUtils.numericToInetAddress(addr);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!ia.isAnyLocalAddress()) {
                p.addDns(ia);
            }
        }
    }

    private boolean makeLinkAddress(LinkProperties p) {
        final String ipAddress = SystemProperties.get("net." + mIfName + ".local-ip");
        if (TextUtils.isEmpty(ipAddress)) {
            Log.e(TAG, "makeLinkAddress with empty ipAddress");
            return false;
        }

        InetAddress ia;
        try {
            ia = NetworkUtils.numericToInetAddress(ipAddress);
        } catch (IllegalArgumentException e) {
            return false;
        }

        int addrPrefixLen = (ia instanceof Inet4Address) ? 32 : 128;
        p.addLinkAddress(new LinkAddress(ia, addrPrefixLen));
        return true;
    }

    public DongleNetworkStateTracker(int netType, String tag) {
        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_DONGLE, 0, NETWORKTYPE, "");
        mLinkProperties = new LinkProperties();
        mLinkCapabilities = new LinkCapabilities();

        mNetworkInfo.setIsAvailable(false);
        setTeardownRequested(false);
    }

    public Object Clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    @Override
    public void startMonitoring(Context context, Handler target) {
        mContext = context;
        mCsHandler = target;
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_NETWORK_STATE);
        mContext.registerReceiver(mReceiver, filter);
    }

    @Override
    public NetworkInfo getNetworkInfo() {
        return mNetworkInfo;
    }

    @Override
    public LinkQualityInfo getLinkQualityInfo() {
        return null;
    }
	
    @Override
    public void captivePortalCheckComplete() {
    }
	
    @Override
    public void captivePortalCheckCompleted(boolean isCaptivePortal) {
        // not implemented
    }


    @Override
    public LinkProperties getLinkProperties() {
        return new LinkProperties(mLinkProperties);
    }

    @Override
    public LinkCapabilities getLinkCapabilities() {
        return new LinkCapabilities(mLinkCapabilities);
    }

    @Override
    public String getTcpBufferSizesPropName() {
        return "net.tcp.buffersize.umts";
    }

    @Override
    public boolean teardown() {
        mTeardownRequested.set(true);
        return true;
    }

    @Override
    public boolean reconnect() {
        mTeardownRequested.set(false);
        return true;
    }

    @Override
    public boolean setRadio(boolean turnOn) {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return mNetworkInfo.isAvailable();
    }

    @Override
    public void setUserDataEnable(boolean enabled) {
        Log.w(TAG, "ignoring setUserDataEnable(" + enabled + ")");        
    }

    @Override
    public void setPolicyDataEnable(boolean enabled) {
        Log.w(TAG, "ignoring setUserDataEnable(" + enabled + ")");
    }

    @Override
    public boolean isPrivateDnsRouteSet() {
        return mPrivateDnsRouteSet.get();
    }

    @Override
    public void privateDnsRouteSet(boolean enabled) {
        mPrivateDnsRouteSet.set(enabled);
    }

    @Override
    public boolean isDefaultRouteSet() {
        return mDefaultRouteSet.get();
    }

    @Override
    public void defaultRouteSet(boolean enabled) {
        mDefaultRouteSet.set(enabled);
    }

    @Override
    public boolean isTeardownRequested() {
        return mTeardownRequested.get();
    }

    @Override
    public void setTeardownRequested(boolean isRequested) {
        mTeardownRequested.set(isRequested);
    }

    @Override
    public void setDependencyMet(boolean met) {
        // TODO Auto-generated method stub
    }

    @Override
    public void addStackedLink(LinkProperties link) {
        mLinkProperties.addStackedLink(link);
    }

    @Override
    public void removeStackedLink(LinkProperties link) {
        mLinkProperties.removeStackedLink(link);
    }

    @Override
    public void supplyMessenger(Messenger messenger) {
        // not supported on this network
    }

    @Override
    public String getNetworkInterfaceName() {
        if (mLinkProperties != null) {
            return mLinkProperties.getInterfaceName();
        } else {
            return null;
        }
    }

    @Override
    public void startSampling(SamplingDataTracker.SamplingSnapshot s) {
        // nothing to do
    }

    @Override
    public void stopSampling(SamplingDataTracker.SamplingSnapshot s) {
        // nothing to do
    }
}
