/*
 * Copyright (C) 2012 Intel Corporation, All Rights Reserved
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

package com.intel.internal.telephony.OemTelephony;

public interface OemTelephonyConstants {

    /* SMS Transport Mode - See 3GPP 27.007 */
    public static final int SMS_TRANSPORT_MODE_INVALID = -1;
    public static final int SMS_TRANSPORT_MODE_PACKET_DOMAIN = 0;
    public static final int SMS_TRANSPORT_MODE_CIRCUIT_SWITCHED = 1;
    public static final int SMS_TRANSPORT_MODE_PACKET_DOMAIN_PREFERRED = 2;
    public static final int SMS_TRANSPORT_MODE_CIRCUIT_SWITCHED_PREFERRED = 3;

    /* TX Power Offset Table Index */
    public static final int OFFSET_TABLE_INDEX_0 = 0;
    public static final int OFFSET_TABLE_INDEX_1 = 1;
    public static final int OFFSET_TABLE_INDEX_2 = 2;

    public static final int MODEM_SENSOR_ID_RF              = 0;
    public static final int MODEM_SENSOR_ID_BASEBAND_CHIP   = 1;
    public static final int MODEM_SENSOR_ID_PCB             = 3;

    public static final String MODEM_SENSOR_ID_KEY = "sensorId";
    public static final String MODEM_SENSOR_TEMPERATURE_KEY = "temperature";

    /* IMS network status */
    public static final int IMS_NW_STATUS_NOT_SUPPORTED = 0;
    public static final int IMS_NW_STATUS_SUPPORTED = 1;
    /* IMS registration state request */
    public static final int IMS_STATE_REQUEST_UNREGISTER = 0;
    public static final int IMS_STATE_REQUEST_REGISTER = 1;
    /* IMS registration status */
    public static final int IMS_STATUS_UNREGISTERED = 0;
    public static final int IMS_STATUS_REGISTERED = 1;
    /* IMS call status */
    public static final int IMS_CALL_NOT_AVAILABLE = 0;
    public static final int IMS_CALL_AVAILABLE = 1;
    /* IMS sms status */
    public static final int IMS_SMS_NOT_AVAILABLE = 0;
    public static final int IMS_SMS_AVAILABLE = 1;

    /**
     * Broadcast Action: Modem sensor has reached the set threshold temperature.
     * The intent will have the following extra values:</p>
     * <ul>
     *   <li><em>sensor Id</em> -  - An int with one of the following values:
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#MODEM_SENSOR_ID_RF},
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#MODEM_SENSOR_ID_BASEBAND_CHIP},
     *          or {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#MODEM_SENSOR_ID_PCB}</li>
     *   <li><em>temperature</em> - Integer containing the temperature formatted as a
     *              4 digits number: 2300 = 23.00 celcius degrees (two digits after dot).</li>
     * </ul>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_MODEM_SENSOR_THRESHOLD_REACHED =
                                    "intel.intent.action.MODEM_SENSOR_THRESHOLD_REACHED";

    public static final String IMS_STATUS_KEY = "IMSState";
    public static final String ACTION_IMS_REGISTRATION_STATE_CHANGED =
                                    "intel.intent.action.IMS_REGISTRATION_STATE_CHANGED";

    public static final String ACTION_IMS_NW_SUPPORT_STATE_CHANGED =
                                    "intel.intent.action.IMS_NW_SUPPORT_STATE_CHANGED";

    public static final String COEX_INFO_KEY = "CoexInfo";
    public static final String ACTION_COEX_INFO =
                                    "intel.intent.action.ACTION_COEX_INFO";

    // Ciphering Indication Intents.
    public static final String ACTION_CIPHERING_STATE_CHANGED =
            "intel.intent.action.CIPHERING_STATE_CHANGED";
    // The key for ciphering status
    public static final String CIPHERING_STATUS_KEY = "CipheringStatus"; // ON/OFF

    // These enumerations should be in sync with what is used in
    // ril adaptation

    /**
     * This enum details the additional requests (OEM) to pass to the RIL
     * via the RIL_REQUEST_OEM_HOOK_RAW API
     * The command id allocation is the following:
     * 0x00000001 -> 0x0000009F : Product Specific
     * 0x000000A0 -> 0x000000CF : Platform Requests
     * 0x000000D0 -> 0x000000FF : Platform Unsolicited
     */

    /* OEM hook to get the value of sensor id */
    public static final int RIL_OEM_HOOK_STRING_THERMAL_GET_SENSOR = 0x000000A2;
    /* OEM hook to set the min and max threhold of the sensor id */
    public static final int RIL_OEM_HOOK_STRING_ACTIVATE_THERMAL_SENSOR_NOTIFICATION = 0x000000A3;
    /*  OEM HOOK to set the Autonomous Fast Dormancy Mode */
    public static final int RIL_OEM_HOOK_STRING_SET_MODEM_AUTO_FAST_DORMANCY = 0x000000A4;
    /* OEM hook to Get the Answer to reset of the current SIM card */
    public static final int RIL_OEM_HOOK_STRING_GET_ATR = 0x000000A5;
    /* OEM hook to get GPRS cell environment */
    public static final int RIL_OEM_HOOK_STRING_GET_GPRS_CELL_ENV = 0x000000A6;
    /* OEM hook to dump the cell environment */
    public static final int RIL_OEM_HOOK_STRING_DEBUG_SCREEN_COMMAND = 0x000000A7;
    /* OEM hook to release all the calls */
    public static final int RIL_OEM_HOOK_STRING_RELEASE_ALL_CALLS = 0x000000A8;
    /* OEM hook to get the SMS Transport Mode */
    public static final int RIL_OEM_HOOK_STRING_GET_SMS_TRANSPORT_MODE = 0x000000A9;
    /* OEM hook to set the SMS Transport Mode */
    public static final int RIL_OEM_HOOK_STRING_SET_SMS_TRANSPORT_MODE = 0x000000AA;
    /* OEM hook to set the RF Power table */
    public static final int RIL_OEM_HOOK_STRING_GET_RF_POWER_CUTBACK_TABLE = 0x000000AB;
    /* OEM hook to set the RF Power table */
    public static final int RIL_OEM_HOOK_STRING_SET_RF_POWER_CUTBACK_TABLE = 0x000000AC;
    /* OEM hook to register or unregister on IMS network */
    public static final int RIL_OEM_HOOK_STRING_IMS_REGISTRATION = 0x000000AD;
    /* OEM hook to set a new IMS apn */
    public static final int RIL_OEM_HOOK_STRING_IMS_CONFIG = 0x000000AE;
    /* OEM hook specific to set the default APN and type. Valid only in LTE modem. */
    public static final int RIL_OEM_HOOK_STRING_SET_DEFAULT_APN = 0x00000AF;
    /* OEM hook to power off modem */
    public static final int RIL_OEM_HOOK_STRING_POWEROFF_MODEM = 0x000000B0;
    /* OEM hook specific to DSDS for swapping protocol stacks configs */
    public static final int RIL_OEM_HOOK_STRING_SWAP_PS = 0x000000B2;
    /* OEM hook used to send direct AT commands to modem */
    public static final int RIL_OEM_HOOK_STRING_SEND_AT = 0x000000B3;
    /* OEM hook specific to indicate call available with IMS */
    public static final int RIL_OEM_HOOK_STRING_IMS_CALL_STATUS = 0x000000B4;
    /* OEM hook specific to indicate sms available with IMS */
    public static final int RIL_OEM_HOOK_STRING_IMS_SMS_STATUS = 0x000000B5;
    /* OEM hook specific to get PC-CSCF information with IMS */
    public static final int RIL_OEM_HOOK_STRING_IMS_GET_PCSCF = 0x000000B6;
    /* OEM hook specific to IMS to pass SRVCC call infos */
    public static final int RIL_OEM_HOOK_STRING_IMS_SRVCC_PARAM = 0x000000B7;
    /* OEM hook to get the thermal alarm indication */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_THERMAL_ALARM_IND = 0x000000D0;
    /* OEM hook specific to DSDS for catching out of service URC */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_FAST_OOS_IND = 0x000000D1;
    /* OEM hook specific to DSDS for catching in service URC */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_IN_SERVICE_IND = 0x000000D2;
    /* OEM hook specific to indicate data suspended/resume status */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_DATA_STATUS_IND = 0x000000D3;
    /* OEM hook specific to indicate MT class */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_MT_CLASS_IND = 0x000000D4;
    /* OEM hook specific to report events to crashtool */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_CRASHTOOL_EVENT_IND = 0x000000D5;
    /* OEM hook specific to indicate IMS registration status */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_IMS_REG_STATUS = 0x000000D6;
    /* OEM hook specific to indicate network IMS support status */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_IMS_SUPPORT_STATUS = 0x000000D7;
    /* OEM hook to indicate device diagnostic metrics and IDC CWS info, for RF Coexistence */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_COEX_INFO = 0x000000D8;
    /* OEM hook specific to indicate network APN information */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_NETWORK_APN_IND = 0x000000D9;
    /* OEM hook specific to indicate SIM appliation error */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_SIM_APP_ERR_IND = 0x000000DA;
    /* OEM hook specific to indicate call disconnect */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_CALL_DISCONNECTED = 0x000000DB;
    /* OEM hook specific to indicate new bearer TFT information */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_BEARER_TFT_PARAMS = 0x000000DC;
    /* OEM hook specific to indicate new bearer QOS information */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_BEARER_QOS_PARAMS = 0x000000DD;
    /* OEM hook specific to indicate bearer deactivation */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_BEARER_DEACT = 0x000000DE;
    /* OEM hook specific to ciphering indication */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_CIPHERING_IND = 0x000000DF;
    /* OEM hook specific to indicate SRVCC procedure started or completed*/
    public static final int RIL_OEM_HOOK_RAW_UNSOL_SRVCCH_STATUS = 0x000000E0;
    /* OEM hook specific to indicate SRVCC synchronization needed */
    public static final int RIL_OEM_HOOK_RAW_UNSOL_SRVCC_HO_STATUS = 0x000000E1;
}
