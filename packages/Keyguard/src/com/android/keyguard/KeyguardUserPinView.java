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

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCardConstants.State;

import android.content.Context;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.net.ConnectivityManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView.OnEditorActionListener;

import com.android.internal.telephony.TelephonyConstants;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.TelephonyProperties2;

import android.util.Log;

/**
 * DSDS: Displays a PIN pad for unlocking.
 */
public class KeyguardUserPinView extends KeyguardAbsKeyInputView
        implements KeyguardSecurityView, OnEditorActionListener, TextWatcher {

    static private final String TAG = "UserPinUnlock";
    static private final boolean DEBUG = true;

    private ProgressDialog mSimUnlockProgressDialog = null;
    private volatile boolean mSimCheckInProgress;
    private int mSlot;
    private String mTitle;

    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;

    public KeyguardUserPinView(Context context) {
        this(context, null);
    }

    public KeyguardUserPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(context);
    }

    public void resetState() {
        //mSecurityMessageDisplay.setMessage(R.string.kg_sim_pin_instructions, true);
        updateUI();
        mPasswordEntry.setEnabled(true);
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.pinEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final View cancel = findViewById(R.id.key_cancel);
        if (cancel == null) {
            log("OOO, no cancel button");
        } else {
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doHapticKeyClick();
                    boolean unlocked = mUpdateMonitor.dismissSimPinActivity(mSlot);
                    if (unlocked) {
                        mCallback.dismiss(true);
                    } else {
                        updateUI();
                        log("still locked on another SIM, slot:" + mSlot);
                    }
                    mCallback.userActivity(0);
                    mSimCheckInProgress = false;
                }
            });
        }

        updateUI();

        final View ok = findViewById(R.id.key_enter);
        if (ok != null) {
            ok.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doHapticKeyClick();
                    verifyPasswordAndUnlock();
                }
            });
        }

        // The delete button is of the PIN keyboard itself in some (e.g. tablet) layouts,
        // not a separate view
        View pinDelete = findViewById(R.id.delete_button);
        if (pinDelete != null) {
            pinDelete.setVisibility(View.VISIBLE);
            pinDelete.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    CharSequence str = mPasswordEntry.getText();
                    if (str.length() > 0) {
                        mPasswordEntry.setText(str.subSequence(0, str.length()-1));
                    }
                    doHapticKeyClick();
                }
            });
            pinDelete.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    mPasswordEntry.setText("");
                    doHapticKeyClick();
                    return true;
                }
            });
        }

        mPasswordEntry.setKeyListener(DigitsKeyListener.getInstance());
        mPasswordEntry.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        mPasswordEntry.requestFocus();
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public void onPause() {
        // dismiss the dialog.
        if (mSimUnlockProgressDialog != null) {
            mSimUnlockProgressDialog.dismiss();
            mSimUnlockProgressDialog = null;
        }
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {
        private final String mPin;

        protected CheckSimPin(String pin) {
            mPin = pin;
        }

        abstract void onSimCheckResponse(boolean success);

        @Override
        public void run() {
            try {
                final String service = getSeviceBySlot(mSlot, mContext);
                final boolean result = ITelephony.Stub.asInterface(ServiceManager
                        .checkService(service)).supplyPin(mPin);
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(result);
                    }
                });
            } catch (RemoteException e) {
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(false);
                    }
                });
            }
        }
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.kg_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            if (!(mContext instanceof Activity)) {
                mSimUnlockProgressDialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            }
        }
        return mSimUnlockProgressDialog;
    }

    String getRetryTip() {
        String ret = "";
        String prop = isPrimaryPhone() ?
            TelephonyProperties.PROPERTY_SIM_PIN_RETRY_LEFT :
            TelephonyProperties2.PROPERTY_SIM_PIN_RETRY_LEFT;
        int numOfRetry = SystemProperties.getInt(prop, 0);
        if (numOfRetry > 0) {
            ret = mContext.getString(R.string.pin_puk_retry_left, numOfRetry);
        }
        return ret;
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText().toString();

        if (!isValidPin(entry, false)) {
            // otherwise, display a message to the user, and don't submit.
            mSecurityMessageDisplay.setMessage(R.string.kg_invalid_sim_pin_hint, true);
            mPasswordEntry.setText("");
            mCallback.userActivity(0);
            return;
        }

        getSimUnlockProgressDialog().show();

        if (!mSimCheckInProgress) {
            mSimCheckInProgress = true; // there should be only one
            new CheckSimPin(mPasswordEntry.getText().toString()) {
                void onSimCheckResponse(final boolean success) {
                    post(new Runnable() {
                        public void run() {
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                            }
                            if (success) {
                                // before closing the keyguard, report back that the sim is unlocked
                                // so it knows right away.
                                boolean unlocked = mUpdateMonitor.reportUserPinUnlocked(mSlot);
                                if (unlocked) {
                                    log("dismiss the keyguard only when both SIM is unsecured");
                                    mCallback.dismiss(true);
                                } else {
                                    log("still locked on the other SIM!");
                                }
                            } else {
                                mSecurityMessageDisplay.setMessage
                                    (R.string.kg_password_wrong_pin_code, true);
                                mPasswordEntry.setText("");
                            }
                            mCallback.userActivity(0);
                            mSimCheckInProgress = false;
                        }
                    });
                }
            }.start();
        }
    }
    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onSimStateChanged(State simState) {
            onIccStateChanged(0, simState);
        }

        @Override
        public void onSim2StateChanged(State simState) {
            onIccStateChanged(1, simState);
        }
    };
    void onIccStateChanged(int slot, State simState) {
        if (DEBUG) Log.d(TAG, "simState:" + simState + ",on slot " + slot);
        if (slot != mSlot || simState.isPinLocked()) {
            return;
        }

        boolean unlocked = mUpdateMonitor.reportUserPinUnlocked(mSlot);
        if (unlocked) {
            if (DEBUG) Log.d(TAG, "unlocked: " + unlocked);
            mCallback.dismiss(true);
        } else {
            updateUI();
        }

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mUpdateMonitor.registerCallback(mInfoCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mUpdateMonitor.removeCallback(mInfoCallback);
    }

    private KeyguardUpdateMonitor mUpdateMonitor;
    private void updateUI() {
        mSlot = mUpdateMonitor.getUserPinActivity();
        log("current slot:" + mSlot);
        updateTitle();
    }
    private void updateTitle() {

        if ("".equals(getRetryTip())) {
            mTitle = mSlot == 0 ?
                    getContext().getString(R.string.slot1) : getContext().getString(R.string.slot2);
            mTitle = mTitle + ": " + getContext().getString(R.string.keyguard_password_enter_pin_code);
        } else {
            mTitle = mContext.getText(R.string.keyguard_password_wrong_pin_code) + getRetryTip();
        }

        mSecurityMessageDisplay.setMessage(mTitle, true);
    }


    boolean isPrimaryPhone() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        return (mSlot== cm.getPrimaryDataSim());
    }

    /**
     *  To get 'phone' service or 'phone2' service by slot.
     */
    static private String getSeviceBySlot(int slotId, Context ctx) {
        boolean isPrimary = true;
        if (TelephonyConstants.IS_DSDS) {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            isPrimary = (slotId ==  cm.getPrimaryDataSim());
        }
        return isPrimary ? "phone" : "phone2";
    }

    static boolean isValidPin(String pin, boolean isPUK) {

        // for pin, we have 4-8 numbers, or puk, we use only 8.
        int pinMinimum = isPUK ? MAX_PIN_LENGTH : MIN_PIN_LENGTH;

        // check validity
        if (pin == null || pin.length() < pinMinimum || pin.length() > MAX_PIN_LENGTH) {
            return false;
        } else {
            return true;
        }
    }

    static void log(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
