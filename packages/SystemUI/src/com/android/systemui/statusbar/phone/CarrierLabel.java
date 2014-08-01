/*
 * Copyright (C) 2013 Capital Alliance Software LTD (Pekall)
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.widget.TextView;

import com.android.internal.telephony.TelephonyConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyIntents2;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import com.android.internal.R;

/**
 * This widget display an analogic clock with two hands for hours and
 * minutes.
 */
public class CarrierLabel extends TextView {
    private boolean mAttached;
    private int mSlot = 0;

    public CarrierLabel(Context context) {
        this(context, null);
    }

    public CarrierLabel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CarrierLabel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        updateNetworkName(false, null, false, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
            filter.addAction(TelephonyIntents2.SPN_STRINGS_UPDATED_ACTION);
            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    public void setSimSlot(int slot) {
        if (TelephonyConstants.IS_DSDS) {
            mSlot = slot;
            Slog.d("CarrierLabel", "mSlot = " + mSlot);
        }
    }

    private boolean isSim1Primary() {
        int curId = Settings.Global.getInt(
                getContext().getContentResolver(),
                Settings.Global.MOBILE_DATA_SIM,
                TelephonyConstants.DSDS_SLOT_1_ID);
        return curId == TelephonyConstants.DSDS_SLOT_1_ID;
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.SPN_STRINGS_UPDATED_ACTION.equals(action) ||
                    TelephonyIntents2.SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                int slot = intent.getIntExtra(TelephonyConstants.EXTRA_SLOT, 0);
                if (slot == mSlot) {
                    updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false),
                            intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                            intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                            intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
                }
            }
        }
    };

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
        if (false) {
            Slog.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        final String str;
        // match logic in KeyguardStatusViewManager
        final boolean plmnValid = showPlmn && !TextUtils.isEmpty(plmn);
        final boolean spnValid = showSpn && !TextUtils.isEmpty(spn);
        if (plmnValid && spnValid) {
            str = plmn + "|" + spn;
        } else if (plmnValid) {
            str = plmn;
        } else if (spnValid) {
            str = spn;
        } else {
            str = TelephonyConstants.IS_DSDS ?
                    getContext().getString(com.android.internal.R.string.lockscreen_carrier_default) :
                    "";
        }

        if (TelephonyConstants.IS_DSDS) {
            final boolean isSim1DataSim = isSim1Primary();
            if ((isSim1DataSim && mSlot == TelephonyConstants.DSDS_SLOT_1_ID) ||
                    (!isSim1DataSim && mSlot == TelephonyConstants.DSDS_SLOT_2_ID)) {
                setTypeface(null, Typeface.BOLD);
            } else {
                setTypeface(null, Typeface.NORMAL);
            }

            if (mSlot == TelephonyConstants.DSDS_SLOT_1_ID) {
                setTextColor(TelephonyConstants.DSDS_TEXT_COLOR_SLOT_1);
            } else {
                setTextColor(TelephonyConstants.DSDS_TEXT_COLOR_SLOT_2);
            }
        }

        setText(str);
    }
}


