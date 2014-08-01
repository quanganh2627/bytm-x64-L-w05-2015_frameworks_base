/*
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

import android.app.admin.DevicePolicyManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;

import android.animation.LayoutTransition;
import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.telephony.TelephonyManager;
import android.net.ConnectivityManager;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.InputType;
import android.view.animation.DecelerateInterpolator;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.drawable.Drawable;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.util.Log;
import android.util.AttributeSet;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import android.content.res.Resources;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.TelephonyConstants;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.TelephonyProperties2;
import com.android.internal.widget.LockPatternUtils;

public class DualSimUnlockScreen extends LinearLayout implements KeyguardSecurityView,
        View.OnClickListener, View.OnLongClickListener {
    private static final String TAG = "DualSimUnlockScreen";
    private static final boolean DBG = true;

    private View mSlotView[] = { null, null};
    private View mSlot2;
    private TextView mHeadText;
    private TextView mSlotText[] = {null, null};
    private View mSecure[] = {null, null};
    private TextView mContinueButton;

    private static final char[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    private ValueAnimator mShowAnimation = null;
    private ValueAnimator mHideAnimation = null;

    private boolean mKeypadVisible = false;
    private int mSlotId = -1;
    private int mSimState = -1;

    private ProgressDialog mSimUnlockProgressDialog = null;

    private TextView mHeaderText;
    private TextView mPwdEntry;

    private boolean mCheckInProgress = false;

    private String mPinText;
    private String mPukText;

    private StateMachine mStateMachine = new StateMachine();

    private int mKeypadHeight;
    private int mSlotsHeight;
    private int mSlotWidth;
    private int mInfoHeight;

    private LinearLayout mSlots;
    private View mKeypad;
    private View mInfo;

    private KeyguardSecurityCallback mKeyguardCallback;
    private KeyguardUpdateMonitor mUpdateMonitor;

    private SecurityMessageDisplay mSecurityMessageDisplay;
    private View mEcaView;
    private Drawable mBouncerFrame;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    private static final int ENABLED = 1;
    private static final int AWAKE_POKE_MILLIS = 30000;

    private static final int EVENT_UNLOCK_DONE = 1;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_UNLOCK_DONE:
                    break;
            }
        }
    };

    private LockPatternUtils mLockPatternUtils;

    private int mCreationOrientation;

    private int mKeyboardHidden;

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

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {

        private final String mPin;

        protected CheckSimPin(String pin) {
            mPin = pin;
        }

        abstract void onSimLockChangedResponse(boolean success);

        @Override
        public void run() {
            try {
                final String service = isPrimaryPhone() ? "phone" : "phone2";
                final boolean result = ITelephony.Stub.asInterface(ServiceManager
                        .checkService(service)).supplyPin(mPin);
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(result);
                    }
                });
            } catch (RemoteException e) {
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(false);
                    }
                });
            }
        }
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPuk extends Thread {
        private final String mPin, mPuk;

        protected CheckSimPuk(String puk, String pin) {
            mPuk = puk;
            mPin = pin;
        }

        abstract void onSimLockChangedResponse(boolean success);

        @Override
        public void run() {
            try {
                final String service = isPrimaryPhone() ? "phone" : "phone2";
                final boolean result = ITelephony.Stub.asInterface(ServiceManager
                        .checkService(service)).supplyPuk(mPuk, mPin);
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(result);
                    }
                });
            } catch (RemoteException e) {
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(false);
                    }
                });
            }
        }
    }

    private class StateMachine {
        final int ENTER_PUK = 0;
        final int ENTER_PIN = 1;
        final int CONFIRM_PIN = 2;
        final int DONE = 3;
        private int state = ENTER_PUK;

        public void next() {
            int msg = 0;
            if (state == ENTER_PUK) {
                if (checkPuk()) {
                    state = ENTER_PIN;
                    msg = R.string.kg_puk_enter_pin_hint;
                } else {
                    msg = R.string.kg_invalid_sim_puk_hint;
                }
            } else if (state == ENTER_PIN) {
                if (checkPin()) {
                    state = CONFIRM_PIN;
                    msg = R.string.kg_enter_confirm_pin_hint;
                } else {
                    msg = R.string.kg_invalid_sim_pin_hint;
                }
            } else if (state == CONFIRM_PIN) {
                if (confirmPin()) {
                    state = DONE;
                    msg =
                        R.string.keyguard_sim_unlock_progress_dialog_message;
                    verifyPukAndUnlock();
                } else {
                    state = ENTER_PIN; // try again?
                    msg = R.string.kg_invalid_confirm_pin_hint;
                }
            }
            mPwdEntry.setText(null);
            if (msg != 0) {
                mHeaderText.setText(msg);
            }
        }

        void reset() {
            mPinText="";
            mPukText="";
            state = ENTER_PUK;
            mSecurityMessageDisplay.setMessage(R.string.kg_puk_enter_puk_hint, true);
            mPwdEntry.requestFocus();
        }
    }

    public DualSimUnlockScreen(Context context) {
        this(context, null);
    }

    public DualSimUnlockScreen(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d(TAG, "start construct");
        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(context);
    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        mKeyguardCallback = callback;
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        mLockPatternUtils = utils;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLockPatternUtils = mLockPatternUtils == null
                ? new LockPatternUtils(mContext) : mLockPatternUtils;

        mSlotView[0] = findViewById(R.id.slot1_layout);
        mSlotView[0].setOnClickListener(this);
        mSlotView[1] = findViewById(R.id.slot2_layout);
        mSlotView[1].setOnClickListener(this);
        mSlotText[0] =  (TextView)findViewById(R.id.slot1_text);
        mSlotText[1] = (TextView)findViewById(R.id.slot2_text);
        mSecure[0] = findViewById(R.id.secure1);
        mSecure[1] = findViewById(R.id.secure2);
        mContinueButton = (TextView) findViewById(R.id.cont);
        mContinueButton.setOnClickListener(this);

        mHeaderText = (TextView)findViewById(R.id.headerText);
        mPwdEntry = (TextView)findViewById(R.id.dsdsPinEntry);
        mPwdEntry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        View view = findViewById(R.id.delete_button);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);

        findViewById(R.id.key_enter).setOnClickListener(this);
        findViewById(R.id.key_cancel).setOnClickListener(this);

        mSlots = (LinearLayout)findViewById(R.id.slots_layout);

        mKeypad = findViewById(R.id.keypad_layout);
        mInfo = findViewById(R.id.info);

        setFocusableInTouchMode(true);
        mSecurityMessageDisplay = new KeyguardMessageArea.Helper(this);
        mEcaView = findViewById(R.id.keyguard_selector_fade_container);
        View bouncerFrameView = findViewById(R.id.keyguard_bouncer_frame);
        if (bouncerFrameView != null) {
            mBouncerFrame = bouncerFrameView.getBackground();
        }

        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG);

        initAnimation();
        updateTitle();
    }

    private void initAnimation() {
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(LayoutTransition.CHANGE_APPEARING, 80);
        transition.setDuration(LayoutTransition.CHANGE_DISAPPEARING, 80);
        transition.setInterpolator(LayoutTransition.CHANGE_APPEARING, new DecelerateInterpolator());
        transition.setInterpolator(LayoutTransition.CHANGE_DISAPPEARING, new DecelerateInterpolator());
        transition.setStartDelay(LayoutTransition.CHANGE_APPEARING, 0);
        transition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transition.setStartDelay(LayoutTransition.APPEARING, 60);
        transition.setDuration(LayoutTransition.APPEARING, 60);
        transition.setDuration(LayoutTransition.DISAPPEARING, 0);
        mSlots.setLayoutTransition(transition);

        LayoutParams keypadParams = (LayoutParams)mKeypad.getLayoutParams();
        mKeypadHeight = keypadParams.height;
        Log.d(TAG, "Keypad height: " + mKeypadHeight);

        LayoutParams slotsParams = (LayoutParams)mSlots.getLayoutParams();
        mSlotsHeight = slotsParams.height;
        Log.d(TAG, "Slots height: " + mSlotsHeight);

        LayoutParams slot0Params = (LayoutParams)mSlotView[0].getLayoutParams();
        mSlotWidth = slot0Params.width;
        Log.d(TAG, "Slot width: " + mSlotWidth);

        LayoutParams infoParams = (LayoutParams)mInfo.getLayoutParams();
        mInfoHeight = infoParams.height;
        Log.d(TAG, "Info height: " + mInfoHeight);

        Animator.AnimatorListener animatorListener;
        animatorListener = new Animator.AnimatorListener() {
            public void onAnimationStart(Animator animation) {
                if (mKeypadVisible) {
                    mKeypad.setVisibility(View.VISIBLE);
                } else {
                    mInfo.setVisibility(View.VISIBLE);
                }
            }

            public void onAnimationEnd(Animator animation) {
                if (mKeypadVisible) {
                    LayoutParams keypadParams = (LayoutParams)mKeypad.getLayoutParams();
                    keypadParams.height = mKeypadHeight;
                    mKeypad.setLayoutParams(keypadParams);

                    mPwdEntry.requestFocus();
                    mInfo.setVisibility(View.GONE);
                } else {
                    LayoutParams slot0Params = (LayoutParams)mSlotView[0].getLayoutParams();
                    slot0Params.width = mSlotWidth;
                    mSlotView[0].setLayoutParams(slot0Params);

                    LayoutParams slot1Params = (LayoutParams)mSlotView[1].getLayoutParams();
                    slot1Params.width = mSlotWidth;
                    mSlotView[1].setLayoutParams(slot1Params);

                    LayoutParams infoParams = (LayoutParams)mInfo.getLayoutParams();
                    infoParams.height = mInfoHeight;
                    mInfo.setLayoutParams(infoParams);

                    mContinueButton.requestFocus();
                    mKeypad.setVisibility(View.GONE);
                }
            }

            public void onAnimationRepeat(Animator animation) {
            }

            public void onAnimationCancel(Animator animation) {
            }
        };

        ValueAnimator.AnimatorUpdateListener animatorUpdateListener;
        animatorUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                Float f = (Float)animation.getAnimatedValue();

                if (mSlotId == 0) {
                    LayoutParams slot0Params = (LayoutParams)mSlotView[0].getLayoutParams();
                    slot0Params.width = mSlotWidth + (int)(f * mSlotWidth / 3);
                    mSlotView[0].setLayoutParams(slot0Params);
                } else {
                    LayoutParams slot1Params = (LayoutParams)mSlotView[1].getLayoutParams();
                    slot1Params.width = mSlotWidth + (int)(f * mSlotWidth / 3);
                    mSlotView[1].setLayoutParams(slot1Params);
                }

                LayoutParams slotsParams = (LayoutParams)mSlots.getLayoutParams();
                LayoutParams keypadParams = (LayoutParams)mKeypad.getLayoutParams();
                LayoutParams infoParams = (LayoutParams)mInfo.getLayoutParams();

                slotsParams.height = (int)(mSlotsHeight * 5 / 8 + (1 - f) * mSlotsHeight * 3 / 8);
                keypadParams.height = (int)(mKeypadHeight * f);
                infoParams.height = (int)(mInfoHeight * (1 - f));

                mSlots.setLayoutParams(slotsParams);
                mKeypad.setLayoutParams(keypadParams);
                mInfo.setLayoutParams(infoParams);
            }
        };

        mShowAnimation = ValueAnimator.ofFloat(0f, 1f);
        mShowAnimation.addListener(animatorListener);
        mShowAnimation.addUpdateListener(animatorUpdateListener);
        mShowAnimation.setInterpolator(new DecelerateInterpolator());
        mShowAnimation.setDuration(80);

        mHideAnimation = ValueAnimator.ofFloat(1f, 0f);
        mHideAnimation.addListener(animatorListener);
        mHideAnimation.addUpdateListener(animatorUpdateListener);
        mHideAnimation.setInterpolator(new DecelerateInterpolator());
        mHideAnimation.setDuration(80);
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
    private void updateTitle() {
        mSecurityMessageDisplay.setMessage(R.string.unlock_cards, true);
    }


    public void reset() {
        updateTitle();
    }

    private State getSimStateBySlotId(int slotId) {
        return mUpdateMonitor.getSimState(slotId);
    }

    public int getPrimaryDataSim() {
        enforceAccessPermission();
        int retVal = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MOBILE_DATA_SIM, TelephonyConstants.DSDS_SLOT_1_ID);
        return retVal;
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE,
                "ConnectivityService");
    }
    private boolean isSimUsable(State simState) {
        return (simState == State.READY || simState == State.UNKNOWN);
    }
    private void updateUI() {
        State state1 = getSimStateBySlotId(0);
        State state2 = getSimStateBySlotId(1);
        if (DBG) Log.d(TAG,"updateUI, state1: " + state1 + ", state2: " + state2);

        updateSlotUi(state1, 0);
        updateSlotUi(state2, 1);

    }

    void updateSlotUi(State state, int slotId) {

        mSlotText[slotId].setText(getInfoRes(state, slotId));

        if (state == State.PIN_REQUIRED || state == State.PUK_REQUIRED) {
            mSecure[slotId].setVisibility(View.VISIBLE);
        } else {
            mSecure[slotId].setVisibility(View.INVISIBLE);
        }

        boolean unlockable = (state != State.ABSENT);
        if (DBG) Log.d(TAG,"unlockable:" + unlockable + " for slot: " + slotId);
        mSlotView[slotId].setEnabled(unlockable);
        mSlotText[slotId].setEnabled(unlockable);

    }

    boolean isAirplaneModeOn() {
        return (Settings.Global.getInt(getContext().getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) > 0);
    }

    private int getInfoRes(State state, int slotId) {
        // card off has higher priority.
        if (slotId == 0) {
            int sim1Enabled = Settings.Global.getInt(
                               mContext.getContentResolver(),
                               Settings.Global.DUAL_SLOT_1_ENABLED, ENABLED);

            if (sim1Enabled != ENABLED) {
                 return R.string.card_off;
            }
        } else {
            int sim2Enabled = Settings.Global.getInt(
                               mContext.getContentResolver(),
                               Settings.Global.DUAL_SLOT_2_ENABLED, ENABLED);

            if (sim2Enabled != ENABLED) {
                 return R.string.card_off;
            }
        }

        switch (state) {
            case ABSENT:
                return R.string.card_absent;
            default:
                return R.string.card_unknown;

            case NETWORK_LOCKED:
                return R.string.card_net_locked;

            case READY:
                return R.string.card_available;

            case PIN_REQUIRED:
                return R.string.card_pin_locked;

            case PUK_REQUIRED:
                return R.string.card_puk_locked;
        }
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onResume(int reason) {
        updateUI();
        if (mKeypadVisible) {
            mPwdEntry.requestFocus();
        } else {
            mContinueButton.requestFocus();
        }
    }

    @Override
    public void showUsabilityHint() {
    }

    public void cleanUp() {
    }

    private boolean isPukLocked() {
        return mSimState == TelephonyManager.SIM_STATE_PUK_REQUIRED;
    }

    public void onClick(View v) {
        //mKeyguardCallback.pokeWakelock(AWAKE_POKE_MILLIS);
        mKeyguardCallback.userActivity(0);

        switch (v.getId()) {
        case R.id.cont:
            mUpdateMonitor.reportIccPinCheckDone();
            mKeyguardCallback.dismiss(true);
            break;
        case R.id.slot1_layout:
            if (DBG) Log.d(TAG, "Slot 1 is clicked, mKeypadVisible? " + mKeypadVisible);
            mSlotId = 0;
            mKeypadVisible = !mKeypadVisible;
            showKeypad(mSlotId, mKeypadVisible);
            mPwdEntry.setText("");
            break;
        case R.id.slot2_layout:
            if (DBG) Log.d(TAG, "Slot 2 is clicked, mKeypadVisible? " + mKeypadVisible);
            mSlotId = 1;
            mKeypadVisible = !mKeypadVisible;
            showKeypad(mSlotId, mKeypadVisible);
            mPwdEntry.setText("");
            break;
        case R.id.delete_button:
            reportDelete();
            break;
        case R.id.key_enter:
            reportEnter();
            break;
        case R.id.key_cancel:
            reportCancel();
            break;
        }
    }

    public boolean onLongClick(View v) {
        if (v.getId() == R.id.delete_button) {
            mPwdEntry.setText("");
        }
        return true;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final char digit = event.getMatch(DIGITS);
        if (digit != 0) {
            reportDigit(digit - '0');
            return true;
        }
        switch (keyCode) {
        case KeyEvent.KEYCODE_DEL:
            reportDelete();
            return true;
        case KeyEvent.KEYCODE_ENTER:
            reportEnter();
            return true;
        case KeyEvent.KEYCODE_ESCAPE:
            reportCancel();
            return true;
        }
        return false;
    }

    private void reportDelete() {
        if (mKeypadVisible) {
            CharSequence str = mPwdEntry.getText();
            if (str.length() > 0) {
                mPwdEntry.setText(str.subSequence(0, str.length() - 1));
            }
        }
    }

    private void reportEnter() {
        if (mKeypadVisible) {
            if (isPukLocked()) {
                mStateMachine.next();
            } else {
                if (checkPin()) {
                    verifyPinAndUnlock();
                } else {
                    mHeaderText.setText(com.android.internal.R.string.invalidPin);
                    mPwdEntry.setText("");
                }
            }
        } else {
            mSlotId = -1;
            State state = getSimStateBySlotId(0);
            if (state == State.PIN_REQUIRED || state == State.PUK_REQUIRED) {
                mSlotId = 0;
            } else {
                state = getSimStateBySlotId(1);
                if (state == State.PIN_REQUIRED || state == State.PUK_REQUIRED) {
                    mSlotId = 1;
                }
            }
            if (mSlotId != -1) {
                mKeypadVisible = !mKeypadVisible;
                showKeypad(mSlotId, mKeypadVisible);
                mPwdEntry.setText("");
            }
        }
    }

    private void reportCancel() {
        if (mKeypadVisible) {
            mKeypadVisible = !mKeypadVisible;
            showKeypad(mSlotId, mKeypadVisible);
            mSlotId = -1;
        }
        mPwdEntry.setText("");
    }

    private void reportDigit(int digit) {
        mPwdEntry.append(Integer.toString(digit));
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(mContext.getString(
                        R.string.keyguard_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            if (!(mContext instanceof Activity)) {
                mSimUnlockProgressDialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            }
        }
        return mSimUnlockProgressDialog;
    }

    private boolean checkPin() {
        // make sure the PIN is between 4 and 8 digits
        int length = mPwdEntry.getText().length();
        if (length >= 4 && length <= 8) {
            mPinText = mPwdEntry.getText().toString();
            return true;
        }
        return false;
    }

    private boolean checkPuk() {
        // make sure the puk is at least 8 digits long.
        if (mPwdEntry.getText().length() >= 8) {
            mPukText = mPwdEntry.getText().toString();
            return true;
        }
        return false;
    }

    public boolean confirmPin() {
        return mPinText.equals(mPwdEntry.getText().toString());
    }

    private void verifyPinAndUnlock() {
        getSimUnlockProgressDialog().show();

        new CheckSimPin(mPwdEntry.getText().toString()) {
            void onSimLockChangedResponse(final boolean success) {
                mPwdEntry.post(new Runnable() {
                    public void run() {
                        if (mSimUnlockProgressDialog != null) {
                            mSimUnlockProgressDialog.hide();
                        }
                        IccCardConstants.State state =  mUpdateMonitor.getSimState(mSlotId);
                        if (success) {
                            boolean unlock = mUpdateMonitor.reportDualSimUnlocked(mSlotId);
                            if (unlock) {
                                mKeyguardCallback.dismiss(true);
                            } else {
                                if (DBG) Log.d(TAG, "Other sim is locked, so just hide keypad");
                                if (mKeypadVisible) {
                                    mKeypadVisible = !mKeypadVisible;
                                    showKeypad(mSlotId, mKeypadVisible);
                                    mSlotId = -1;
                                }
                                mPwdEntry.setText("");
                                updateUI();
                            }
                        } else if (state == IccCardConstants.State.PUK_REQUIRED) {
                            mHeaderText.setText(R.string.kg_puk_enter_puk_hint);
                            mSimState = TelephonyManager.SIM_STATE_PUK_REQUIRED;
                        } else {
                            StringBuilder msg = new StringBuilder(mContext.getText(
                                    R.string.keyguard_password_wrong_pin_code));
                            msg.append(" ").append(getRetryTip());
                            mHeaderText.setText(msg.toString());
                            mPwdEntry.setText("");
                        }
                    }
                });
            }
        }.start();
    }

    private void verifyPukAndUnlock() {
        getSimUnlockProgressDialog().show();

        if (!mCheckInProgress) {
            mCheckInProgress = true;
            new CheckSimPuk(mPukText, mPinText) {
                void onSimLockChangedResponse(final boolean success) {
                    post(new Runnable() {
                        public void run() {
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                            }
                            if (success) {
                                boolean unlock = mUpdateMonitor.reportDualSimUnlocked(mSlotId);
                                if (unlock) {
                                    mKeyguardCallback.dismiss(true);
                                } else {
                                    mSlotId = 1 - mSlotId;
                                    if (DBG) Log.d(TAG, "Verify Puk, SIM " + (mSlotId + 1) + " is still locked.");
                                    if (mKeypadVisible) {
                                    mKeypadVisible = !mKeypadVisible;
                                    showKeypad(mSlotId, mKeypadVisible);
                                    }
                                    mPwdEntry.setText("");
                                    updateUI();
                                }
                            } else {
                                if ("".equals(getRetryTip())) {
                                    mHeaderText.setText(R.string.keyguard_password_enter_puk_code);
                                } else {
                                    StringBuilder msg = new StringBuilder(mContext.getText(
                                        R.string.kg_invalid_sim_puk_hint));
                                    msg.append(" ").append(getRetryTip());
                                    mHeaderText.setText(msg.toString());
                                    mPwdEntry.setText("");

                                }
                            }
                            mStateMachine.reset();
                            mCheckInProgress = false;
                        }
                    });
                }
            }.start();
        }
    }

    private String getRetryTip() {
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

    boolean isPrimaryPhone() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return (mSlotId == cm.getPrimaryDataSim());
    }

    private void showKeypad(final int slotId, final boolean visible) {
        if (slotId != 0 && slotId != 1) {
            return;
        }

        State state = getSimStateBySlotId(slotId);
        if (state == State.PIN_REQUIRED) {
            mHeaderText.setText(R.string.kg_sim_pin_instructions);
            mSimState = TelephonyManager.SIM_STATE_PIN_REQUIRED;
        } else if (state == State.PUK_REQUIRED) {
            mHeaderText.setText(R.string.keyguard_password_enter_puk_code);
            mSimState = TelephonyManager.SIM_STATE_PUK_REQUIRED;
        } else {
            if (DBG) Log.d(TAG, "reset mKeypadVisible and return when not locked");
            mKeypadVisible = !visible;
            return;
        }

        if (visible) {
            mWakeLock.acquire();
            if (slotId == 0) {
                mSlotView[1].setVisibility(View.GONE);
            } else {
                mSlotView[0].setVisibility(View.GONE);
            }
            mShowAnimation.start();
        } else {
            mWakeLock.release();
            mSlotView[0].setVisibility(View.VISIBLE);
            mSlotView[1].setVisibility(View.VISIBLE);
            mHideAnimation.start();
        }
    }

    private void showUnlockDialog(int type, int slot) {
        if (DBG) Log.d(TAG, "Enter showUnlockDialog type=" + type + ", slot="+slot);
        Intent intent = new Intent(TelephonyConstants.INTENT_SHOW_PIN_PUK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.putExtra("type", type);
        intent.putExtra(TelephonyConstants.EXTRA_SLOT, slot);
        mContext.startActivity(intent);
    }

    @Override
    public boolean needsInput() {
        return false;
    }

    @Override
    public KeyguardSecurityCallback getCallback() {
        return mKeyguardCallback;
    }

    @Override
    public void showBouncer(int duration) {
        KeyguardSecurityViewHelper.
                showBouncer(mSecurityMessageDisplay, mEcaView, mBouncerFrame, duration);
    }

    @Override
    public void hideBouncer(int duration) {
        KeyguardSecurityViewHelper.
                hideBouncer(mSecurityMessageDisplay, mEcaView, mBouncerFrame, duration);
    }

    void onIccStateChanged(int slot, State simState) {
        if (DBG) Log.d(TAG, "onIccStateChanged,simState:" + simState + ",on slot " + slot);
        boolean keyguardDone = simState.isPinLocked() ?
            false : mUpdateMonitor.reportDualSimUnlocked(slot);

        if (keyguardDone) {
            if (DBG) Log.d(TAG, "Sim is unlocked on slot: " + slot);
            mKeyguardCallback.dismiss(true);
        }

        updateUI();
    }

}
