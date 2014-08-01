/*
 * Copyright (C) 2013 Capital Alliance Software LTD (Pekall)
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.statusbar.policy.NetworkControllerDualSIM;

import com.android.systemui.R;

// Intimately tied to the design of res/layout/signal_cluster_view_dual_sim.xml
public class SignalClusterViewDualSIM
        extends LinearLayout
        implements NetworkControllerDualSIM.SignalCluster {

    static final boolean DEBUG = false;
    static final String TAG = "SignalClusterViewDualSIM";

    NetworkControllerDualSIM mNC;

    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0;
    private boolean mMobileVisible = false;
    private int mMobileStrengthId = 0, mMobileTypeId = 0;
    private int mMobileStrengthId2 = 0, mMobileTypeId2 = 0;
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private String mWifiDescription, mMobileDescription, mMobileTypeDescription;
    private String mMobileDescription2, mMobileTypeDescription2;

    ViewGroup mWifiGroup, mMobileGroup, mMobileGroup2;
    ImageView mWifi, mMobile, mMobileType, mAirplane;
    ImageView mMobile2,  mMobileType2;
    View mSpacer;

    public SignalClusterViewDualSIM(Context context) {
        this(context, null);
    }

    public SignalClusterViewDualSIM(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterViewDualSIM(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setNetworkController(NetworkControllerDualSIM nc) {
        if (DEBUG) Slog.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mWifiGroup       = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi            = (ImageView) findViewById(R.id.wifi_signal);
        mMobileGroup     = (ViewGroup) findViewById(R.id.mobile_combo);
        mMobile          = (ImageView) findViewById(R.id.mobile_signal);
        mMobileType      = (ImageView) findViewById(R.id.mobile_type);
        mSpacer          =             findViewById(R.id.spacer);
        mMobileGroup2    = (ViewGroup) findViewById(R.id.mobile_combo2);
        mMobile2         = (ImageView) findViewById(R.id.mobile_signal2);
        mMobileType2     = (ImageView) findViewById(R.id.mobile_type2);
        mAirplane        = (ImageView) findViewById(R.id.airplane);

        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        mWifiGroup       = null;
        mWifi            = null;
        mMobileGroup     = null;
        mMobile          = null;
        mMobileType      = null;
        mSpacer          = null;
        mMobileGroup2    = null;
        mMobile2         = null;
        mMobileType2     = null;
        mAirplane        = null;
        super.onDetachedFromWindow();
    }

    @Override
    public void setWifiIndicators(boolean visible, int strengthIcon, String contentDescription) {
        mWifiVisible = visible;
        mWifiStrengthId = strengthIcon;
        mWifiDescription = contentDescription;

        apply();
    }

    @Override
    public void setMobileDataIndicators(boolean visible, int strengthIcon,
            int typeIcon, String contentDescription, String typeContentDescription) {
        mMobileVisible = visible;
        mMobileStrengthId = strengthIcon;
        mMobileTypeId = typeIcon;
        mMobileDescription = contentDescription;
        mMobileTypeDescription = typeContentDescription;

        apply();
    }

    @Override
    public void setMobileDataIndicators2(boolean visible, int strengthIcon,
                int typeIcon, String contentDescription, String typeContentDescription,
                int strengthIcon2, int typeIcon2, String contentDescription2,
                String typeContentDescription2) {
        mMobileVisible = visible;
        mMobileStrengthId = strengthIcon;
        mMobileTypeId = typeIcon;
        mMobileDescription = contentDescription;
        mMobileTypeDescription = typeContentDescription;

        mMobileStrengthId2 = strengthIcon2;
        mMobileTypeId2 = typeIcon2;
        mMobileDescription2 = contentDescription2;
        mMobileTypeDescription2 = typeContentDescription2;

        apply();
    }

    @Override
    public void setIsAirplaneMode(boolean is, int airplaneIconId) {
        mIsAirplaneMode = is;
        mAirplaneIconId = airplaneIconId;

        apply();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup != null && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        if (mMobileVisible && mMobileGroup != null && mMobileGroup.getContentDescription() != null)
            event.getText().add(mMobileGroup.getContentDescription());
        if (mMobileVisible && mMobileGroup2 != null && mMobileGroup2.getContentDescription() != null)
            event.getText().add(mMobileGroup2.getContentDescription());
        return super.dispatchPopulateAccessibilityEvent(event);
    }


    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        if (mWifiVisible) {
            mWifiGroup.setVisibility(View.VISIBLE);
            mWifi.setImageResource(mWifiStrengthId);

            mWifiGroup.setContentDescription(mWifiDescription);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("wifi: %s sig=%d act=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId));

        if (mMobileVisible && !mIsAirplaneMode) {
            mMobileGroup.setVisibility(View.VISIBLE);
            mMobile.setImageResource(mMobileStrengthId);
            mMobileType.setImageResource(mMobileTypeId);
            mMobileGroup.setContentDescription(mMobileTypeDescription + " " + mMobileDescription);
            mMobileGroup2.setVisibility(View.VISIBLE);
            mMobile2.setImageResource(mMobileStrengthId2);
            mMobileType2.setImageResource(mMobileTypeId2);
            mMobileGroup2.setContentDescription(mMobileTypeDescription2 + " " + mMobileDescription2);

        } else {
            mMobileGroup.setVisibility(View.GONE);
            mMobileGroup2.setVisibility(View.GONE);
        }

        if (mIsAirplaneMode) {
            mAirplane.setImageResource(mAirplaneIconId);
            mAirplane.setVisibility(View.VISIBLE);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        if (mMobileVisible && mWifiVisible && mIsAirplaneMode) {
            mSpacer.setVisibility(View.INVISIBLE);
        } else {
            mSpacer.setVisibility(View.GONE);
        }

        if (DEBUG) Slog.d(TAG,
                String.format("mobile: %s sig=%d typ=%d",
                    (mMobileVisible ? "VISIBLE" : "GONE"),
                    mMobileStrengthId, mMobileTypeId));

        mMobileType.setVisibility(
                !mWifiVisible ? View.VISIBLE : View.GONE);

        mMobileType2.setVisibility(
                !mWifiVisible ? View.VISIBLE : View.GONE);
    }
}

