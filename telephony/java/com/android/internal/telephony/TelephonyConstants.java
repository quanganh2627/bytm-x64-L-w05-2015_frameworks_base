/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.telephony;

import android.os.SystemProperties;
import android.text.TextUtils;

/**
 * Telephony Constants defined for DSDS
 * @hide
 */
public class TelephonyConstants {

    /**
     * Used when sim index is invalid or unknown
     */
    public static final int DSDS_INVALID_SLOT_ID = -1;

    /**
     * SIM A's index.
     */
    public static final int DSDS_SLOT_1_ID = 0;

    /**
     * SIM B's index.
     */
    public static final int DSDS_SLOT_2_ID = 1;

    /**
     * Primary Phone's index.
     */
    public static final int DSDS_PRIMARY_PHONE_ID = 0;

    /**
     * Secondary Phone's index.
     */
    public static final int DSDS_SECONDARY_PHONE_ID = 1;

    /**
     * The common name to distinguish SIM 1 and SIM 2.
     */
    public static final String EXTRA_SLOT = "slot";

    /**
     * The common name to distinguish whether is primary.
     */
    public static final String IS_PRIMARY = "is_primary";

    /**
     * The common name to distinguish intent extra comes from which phone.
     */
    public static final String FROM_PHONE = "from";

    /**
     * The common name to distinguish primary phone and secondary phone.
     */
    public static final String STRING_PHONE_ID = "phone_id";

    /**
     *   A generic disabled definition.
     */
    public static final int DISABLED = 0;

    /**
     *   A generic enabled definition.
     */
    public static final int ENABLED = 1;

    /**
     *  General ntent to update SimWidget states.
     */
    public static final String ACTION_SIM_WIDGET_GENERIC_UPDATE =
            "com.intel.simwidget.action.GENERIC_UPDATE";

    /**
     * Actin for DSDS call intent.
     * It is added to make call for dual sim device.
     */
    public static final String ACTION_DUAL_SIM_CALL =
            "com.intel.dualsim.ACTION_CALL";

    /**
     * Extra for DSDS call policy.
     * Currently, four plicies are defined.
     *          EXTRA_DCALL_SLOT_1
     *          EXTRA_DCALL_SLOT_2
     *          EXTRA_DCALL_PRIMARY_PHONE
     *          EXTRA_DCALL_SECONDARY_PHONE
     */
    public static final String EXTRA_DSDS_CALL_POLICY =
            "com.imc.phone.extra.DSDS_CALL_POLICY";

    public static final int EXTRA_DCALL_SLOT_1 = 1;
    public static final int EXTRA_DCALL_SLOT_2 = 2;
    public static final int EXTRA_DCALL_PRIMARY_PHONE = 3;
    public static final int EXTRA_DCALL_SECONDARY_PHONE = 4;

    /**
     * Extra for DSDS call intent. It is used to distinguish SIM card.
     */
    public static final String EXTRA_DSDS_CALL_FROM_SLOT_2 =
            "com.imc.phone.extra.DSDS_CALL_FROM_SLOT_2";

    /**
     * Property to indicate dual SIM mode
     */
    private static final String PROPERTY_DUAL_SIM_MODE = "persist.dual_sim";

    /**
     * Intent for setting data sim.
     */
    public static final String ACTION_DATA_SIM_SWITCH = "com.imc.intent.DATA_SIM_SWITCH";

    /**
     * Intent for rat setting.
     */
    public static final String ACTION_RAT_SETTING = "com.imc.intent.RAT_SETTING";

    /**
     * Stage string for setting data sim.
     */
    public static final String EXTRA_SWITCH_STAGE = "data_sim_switching_stage";

    /**
     * Beginning string for setting data sim.
     */
    public static final String SIM_SWITCH_BEGIN = "switch_begin";

    /**
     * fake IMSI for SIP call
     */
    public static final String IMSI_FOR_SIP_CALL = "010101010101010";

    /**
     * Switch done string for setting data sim.
     */
    public static final String SIM_SWITCH_DONE = "switch_done";

    /**
     * Endning string for setting data sim.
     */
    public static final String SIM_SWITCH_END = "switch_end";

    /**
     *  Phone creation done.
     *  It is used when phone is created.
     */
    public static final String PHONE_CREATION_DONE = "phone_creation_done";

    /**
     * Default ICS build (single SIM)
     */
    public static final int DUAL_SIM_MODE_NONE = 0;

    /**
     *  DSDS mode
     */
    public static final int DUAL_SIM_MODE_DSDS = 1;

    /**
     * Dual SIM Mode
     */
    public static final int DUAL_SIM_MODE =
        convertToMode(SystemProperties.get(PROPERTY_DUAL_SIM_MODE));

    /**
     * Mode which needs two ril
     */
    public static final boolean NEED_TWO_RILS =
        (DUAL_SIM_MODE != DUAL_SIM_MODE_NONE);

    /**
     * DSDS cases
     */
    public static final boolean IS_DSDS = DUAL_SIM_MODE == DUAL_SIM_MODE_DSDS;

    /**
     * SIM_ON_OFF property for SIM 1
     */
    public static final String PROP_ON_OFF_SIM1 = "gsm.simmanager.set_off_sim1";

    /**
     * SIM_ON_OFF property for SIM 2
     */
    public static final String PROP_ON_OFF_SIM2 = "gsm.simmanager.set_off_sim2";

    /**
     * Property to indicate USER_PIN_ACTIVITY
     */
    public static final String PROPERTY_USER_PIN_ACTIVITY = "gsm.simpin.user_activity";

    /**
     * Property to indicate state of temporary out of service.
     */
    public static final String PROPERTY_TEMPORARY_OOS = "gsm.dsds.toos";

    /**
     * String to primary phone.
     */
    public static final String STRING_PRIMARY_PHONE = "GSM";

    /**
     * String to secondary phone.
     */
    public static final String STRING_SECONDARY_PHONE = "GSM2";

    /**
     * user PIN/PUK activity on SIM 1
     */
    public static final int USER_ACTIVITY_PIN_SLOT1 = 0;

    /**
     * user PIN/PUK activity on SIM 2
     */
    public static final int USER_ACTIVITY_PIN_SLOT2 = 1;

    /**
     * user PIN/PUK activity on SIM 1
     */
    public static final int USER_ACTIVITY_PIN_NONE = -1;

    /**
     *  intent to update SimWidget states.
     */
    public static final String INTENT_PHONE_DISMISS_USER_PIN =
            "com.imc.intent.dismiss_user_pin";

    /**
     * Intents to show PIN-PUK UI
     */
    public static final String INTENT_SHOW_PIN_PUK =
            "com.imc.intent.PIN_PUK";

    public static final String ACTION_MODEM_FAST_OOS_IND =
            "intel.intent.action.MODEM_FAST_OOS_IND";

    public static final String EXTRA_TOOS_STATE = "state";

    public static final String MODEM_PHONE_NAME_KEY = "phone";

    /**
     * Internal function to contert property to mode
     */
    private static int convertToMode(String property) {
        if (TextUtils.equals(property, "dsds")) {
            return DUAL_SIM_MODE_DSDS;
        } else {
            return DUAL_SIM_MODE_NONE;
        }
    }
    public static final String EXTRA_RESULT_CODE     = "result_code";
    public static final int SWITCH_SUCCESS              = 0;
    public static final int SWITCH_FAILED_PHONE_BUSY    = 1;
    public static final int SWITCH_FAILED_RADIO_OFF     = 2;
    public static final int SWITCH_FAILED_TIMEDOUT      = 3;
    public static final int SWITCH_FAILED_SWITCHING_ON_GOING    = 4;
    public static final int SWITCH_FAILED_GENERIC       = 9;

    /**
     * Intent for ICC Hot Swap.
     */
    public static final String ACTION_ICC_HOT_SWAP = "com.imc.intent.action_hot_swap";

    /**
     * Extra for the intent ACTION_ICC_HOT_SWAP
     */
    public static final String EXTRA_SWAP_IN = "isSimAdded";

    /**
     * Intent for notification of RIL switching, all the message hereafter
     * shall be from the new RIL.
     */
    public static final String ACTION_RIL_SWITCHING = "com.imc.intent.action_ril_switching";

    /**
     * Intent to launch switching the primary SIM
     */
    public static final String INTENT_SWITCHING_PRIMARY = "com.imc.intent.switchingprimary";

    /**
     * Intent to notify SIM activity change
     */
    public static final String INTENT_SIM_ACTIVITY = "com.imc.intent.simactivity";

    /**
     * is during SIM switching
     */
    public static final String PROP_SIM_BUSY = "gsm.dsds.simactivity";

    public static final int SIM_ACTIVITY_IDLE        = 0;
    public static final int SIM_ACTIVITY_SLOT        = 0x02;
    public static final int SIM_ACTIVITY_ONOFF       = 0x04;
    public static final int SIM_ACTIVITY_PRIMARY     = 0x08;
    public static final int SIM_ACTIVITY_IMPORT      = 0x10;
    public static final int SIM_ACTIVITY_EXPORT      = 0x20;

    /**
     * Text color specified for SIM slot 1
     */
    public static final int DSDS_TEXT_COLOR_SLOT_1 = 0xFFFF8800;

    /**
     * Text color specified for SIM slot 1
     */
    public static final int DSDS_TEXT_COLOR_SLOT_2 = 0xFF33B5E5;

    /**
     * Intent for switching ON/OFF a SIM
     */
    public static final String INTENT_SWITCHING_SIM_ONOFF = "com.imc.intent.simonoff";

    /**
     * Intent for notify the result of ON/OFF a SIM
     */
    public static final String INTENT_SIM_ONOFF_RESULT = "com.imc.intent.onoff_result";
}
