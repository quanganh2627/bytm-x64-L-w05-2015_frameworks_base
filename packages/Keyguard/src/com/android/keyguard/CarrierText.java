/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import android.content.Context;
import android.text.method.SingleLineTransformationMethod;
import android.text.Html;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.TelephonyConstants;
import com.android.internal.widget.LockPatternUtils;

import java.util.Locale;
import android.telephony.TelephonyManager;
import android.util.Log;

public class CarrierText extends TextView {
    private static CharSequence mSeparator;
    public static boolean DEBUG = KeyguardViewMediator.DEBUG;
    private LockPatternUtils mLockPatternUtils;

    private CharSequence mPlmn[] = {null, null};
    private CharSequence mSpn[] = {null, null};
    private State mSimState[] = { IccCardConstants.State.NOT_READY, IccCardConstants.State.NOT_READY};

    private static final String mColor1 =
            Integer.toHexString(TelephonyConstants.DSDS_TEXT_COLOR_SLOT_1 & 0x00FFFFFF);
    private static final String mColor2 =
            Integer.toHexString(TelephonyConstants.DSDS_TEXT_COLOR_SLOT_2 & 0x00FFFFFF);

    private KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int slot)  {
            mPlmn[slot] = plmn;
            mSpn[slot] = spn;
            updateCarrierText();
        }

        @Override
        public void onSimStateChanged(IccCardConstants.State simState) {
            mSimState[0] = simState;
            updateCarrierText();
        }

        @Override
        public void onSim2StateChanged(IccCardConstants.State simState) {
            mSimState[1] = simState;
            updateCarrierText();
        }
		
        public void onScreenTurnedOff(int why) {
            setSelected(false);
        };

        public void onScreenTurnedOn() {
            setSelected(true);
        };
    };
    /**
     * The status of this lock screen. Primarily used for widgets on LockScreen.
     */
    private static enum StatusMode {
        Normal, // Normal case (sim card present, it's not locked)
        NetworkLocked, // SIM card is 'network locked'.
        SimMissing, // SIM card is missing.
        SimMissingLocked, // SIM card is missing, and device isn't provisioned; don't allow access
        SimPukLocked, // SIM card is PUK locked because SIM entered wrong too many times
        SimLocked, // SIM card is currently locked
        SimPermDisabled, // SIM card is permanently disabled due to PUK unlock failure
        SimNotReady; // SIM is not ready yet. May never be on devices w/o a SIM.
    }

    public CarrierText(Context context) {
        this(context, null);
    }

    public CarrierText(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLockPatternUtils = new LockPatternUtils(mContext);
        boolean useAllCaps = mContext.getResources().getBoolean(R.bool.kg_use_all_caps);
        setTransformationMethod(new CarrierTextTransformationMethod(mContext, useAllCaps));
    }

    private CharSequence getCarrierTextInHtml(String text1, String text2) {
        String carrier1 = "<font color=\"#" + mColor1 + "\">" + text1 + "</font>";
        String carrier2 = "<font color=\"#" + mColor2 + "\">" + text2 + "</font>";
        return Html.fromHtml(carrier1 + " | " + carrier2);
    }

    protected void updateCarrierText() {
        if (!TelephonyConstants.IS_DSDS) {
            updateCarrierText(mSimState[0], mPlmn[0], mSpn[0]);
            return;
        }

        CharSequence text1 = getCarrierTextForSimState(mSimState[0], mPlmn[0], mSpn[0], 0);
        if (text1 == null) text1 = "";
        CharSequence text2 = getCarrierTextForSimState(mSimState[1], mPlmn[1], mSpn[1], 1);
        if (text2 == null) text2 = "";

        Log.d("Keyguard-CarrierText", "carrier:" + text1 + ",carrier2:" + text2);

        if (KeyguardViewManager.USE_UPPER_CASE) {
            CharSequence content = getCarrierTextInHtml(text1.toString().toUpperCase(), text2.toString().toUpperCase());
            setText(updateSplmn(content));
            //setText(getCarrierTextInHtml(text1.toString().toUpperCase(), text2.toString().toUpperCase()));
        } else {
            CharSequence content = getCarrierTextInHtml(text1.toString(), text2.toString());
            setText(updateSplmn(content));
            //setText(getCarrierTextInHtml(text1.toString(), text2.toString()));
        }
    }
    
    private CharSequence updateSplmn(CharSequence content){
       if(content == null){
         return null;
       }

       String  splmn = content.toString();
       if(splmn.contains("CHN-UNICOM")){
          splmn = getResources().getString(R.string.keyguard_carrier_unicom).toString();
          return appendPlmnType(splmn, content.toString());
       }else if(splmn.contains("CHN-MOBILE")){
          splmn = getResources().getString(R.string.keyguard_carrier_mobile).toString();
          return appendPlmnType(splmn, content.toString());
       }

       return splmn.toString();
    }

    private CharSequence appendPlmnType(String splmn, String content){
       String str = splmn;
       if(content.contains("2G")){
          str = str+" - "+"2G";
       }else if(content.contains("3G")){
          str = str+" - "+"3G";
       }else if(content.contains("4G")){
          str = str+" - "+"4G";
       }
       return str.toString();
    }

    protected void updateCarrierText(State simState, CharSequence plmn, CharSequence spn) {
        setText(getCarrierTextForSimState(simState, plmn, spn, 0));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSeparator = getResources().getString(R.string.kg_text_message_separator);
        final boolean screenOn = KeyguardUpdateMonitor.getInstance(mContext).isScreenOn();
        setSelected(screenOn); // Allow marquee to work.
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mCallback);
    }

    /**
     * Top-level function for creating carrier text. Makes text based on simState, PLMN
     * and SPN as well as device capabilities, such as being emergency call capable.
     *
     * @param simState
     * @param plmn
     * @param spn
     * @return
     */
    private CharSequence getCarrierTextForSimState(IccCardConstants.State simState,
            CharSequence plmn, CharSequence spn, int slot) {
        CharSequence carrierText = null;
        StatusMode status = getStatusForIccState(simState);
        switch (status) {
            case Normal:
                carrierText = concatenate(plmn, spn);
                break;

            case SimNotReady:
                carrierText = null; // nothing to display yet.
                break;

            case NetworkLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_network_locked_message), plmn);
                break;

            case SimMissing:
                // Shows "No SIM card | Emergency calls only" on devices that are voice-capable.
                // This depends on mPlmn containing the text "Emergency calls only" when the radio
                // has some connectivity. Otherwise, it should be null or empty and just show
                // "No SIM card"
                final boolean isSimOff = TelephonyManager.getDefault().isSimOff(slot);
                if (isSimOff) {
                    carrierText =  getContext().getText(R.string.card_off);
                } else {
                    carrierText =  makeCarrierStringOnEmergencyCapable(
                            getContext().getText(R.string.keyguard_missing_sim_message_short),
                            plmn);
                }
                break;

            case SimPermDisabled:
                carrierText = getContext().getText(
                        R.string.keyguard_permanent_disabled_sim_message_short);
                break;

            case SimMissingLocked:
                carrierText =  makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_missing_sim_message_short),
                        plmn);
                break;

            case SimLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_locked_message),
                        plmn);
                break;

            case SimPukLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_puk_locked_message),
                        plmn);
                break;
        }

        return carrierText;
    }

    /*
     * Add emergencyCallMessage to carrier string only if phone supports emergency calls.
     */
    private CharSequence makeCarrierStringOnEmergencyCapable(
            CharSequence simMessage, CharSequence emergencyCallMessage) {
        if (mLockPatternUtils.isEmergencyCallCapable()) {
            return concatenate(simMessage, emergencyCallMessage);
        }
        return simMessage;
    }

    /**
     * Determine the current status of the lock screen given the SIM state and other stuff.
     */
    private StatusMode getStatusForIccState(IccCardConstants.State simState) {
        // Since reading the SIM may take a while, we assume it is present until told otherwise.
        if (simState == null) {
            return StatusMode.Normal;
        }

        final boolean missingAndNotProvisioned =
                !KeyguardUpdateMonitor.getInstance(mContext).isDeviceProvisioned()
                && (simState == IccCardConstants.State.ABSENT ||
                        simState == IccCardConstants.State.PERM_DISABLED);

        // Assume we're NETWORK_LOCKED if not provisioned
        simState = missingAndNotProvisioned ? IccCardConstants.State.NETWORK_LOCKED : simState;
        switch (simState) {
            case ABSENT:
                return StatusMode.SimMissing;
            case NETWORK_LOCKED:
                return StatusMode.SimMissingLocked;
            case NOT_READY:
                return StatusMode.SimNotReady;
            case PIN_REQUIRED:
                return StatusMode.SimLocked;
            case PUK_REQUIRED:
                return StatusMode.SimPukLocked;
            case READY:
                return StatusMode.Normal;
            case PERM_DISABLED:
                return StatusMode.SimPermDisabled;
            case UNKNOWN:
                return StatusMode.SimMissing;
        }
        return StatusMode.SimMissing;
    }

    private static CharSequence concatenate(CharSequence plmn, CharSequence spn) {
        final boolean plmnValid = !TextUtils.isEmpty(plmn);
        final boolean spnValid = !TextUtils.isEmpty(spn);
        if (plmnValid && spnValid) {
            return new StringBuilder().append(plmn).append(mSeparator).append(spn).toString();
        } else if (plmnValid) {
            return plmn;
        } else if (spnValid) {
            return spn;
        } else {
            return "";
        }
    }

    private CharSequence getCarrierHelpTextForSimState(IccCardConstants.State simState,
            String plmn, String spn) {
        int carrierHelpTextId = 0;
        StatusMode status = getStatusForIccState(simState);
        switch (status) {
            case NetworkLocked:
                carrierHelpTextId = R.string.keyguard_instructions_when_pattern_disabled;
                break;

            case SimMissing:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions_long;
                break;

            case SimPermDisabled:
                carrierHelpTextId = R.string.keyguard_permanent_disabled_sim_instructions;
                break;

            case SimMissingLocked:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions;
                break;

            case Normal:
            case SimLocked:
            case SimPukLocked:
                break;
        }

        return mContext.getText(carrierHelpTextId);
    }

    private class CarrierTextTransformationMethod extends SingleLineTransformationMethod {
        private final Locale mLocale;
        private final boolean mAllCaps;

        public CarrierTextTransformationMethod(Context context, boolean allCaps) {
            mLocale = context.getResources().getConfiguration().locale;
            mAllCaps = allCaps;
        }

        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            source = super.getTransformation(source, view);

            if (mAllCaps && source != null) {
                source = source.toString().toUpperCase(mLocale);
            }

            return source;
        }
    }
}
