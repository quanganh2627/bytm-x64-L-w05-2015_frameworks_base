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

package com.android.internal.telephony;

public interface OemTelephonyConstants {

    /* SMS Transport Mode - See 3GPP 27.007 */
     int SMS_TRANSPORT_MODE_PACKET_DOMAIN = 0;
     int SMS_TRANSPORT_MODE_CIRCUIT_SWITCHED = 1;
     int SMS_TRANSPORT_MODE_PACKET_DOMAIN_PREFERRED = 2;
     int SMS_TRANSPORT_MODE_CIRCUIT_SWITCHED_PREFERRED = 3;

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

    /* OEM hook to request modem to trigger Fast Dormancy. */
    int RIL_OEM_HOOK_RAW_TRIGGER_FAST_DORMANCY = 0x000000A0;
    /* OEM hook to set value of Fast Dormancy SCRI inhibition timer */
    int RIL_OEM_HOOK_RAW_SET_FAST_DORMANCY_TIMER = 0x000000A1;
    /* OEM hook to get the value of sensor id */
    int RIL_OEM_HOOK_STRING_THERMAL_GET_SENSOR = 0x000000A2;
    /* OEM hook to set the min and max threhold of the sensor id */
    int RIL_OEM_HOOK_STRING_ACTIVATE_THERMAL_SENSOR_NOTIFICATION = 0x000000A3;
    /*  OEM HOOK to set the Autonomous Fast Dormancy Mode */
    int RIL_OEM_HOOK_RAW_SET_MODEM_AUTO_FAST_DORMANCY = 0x000000A4;
    /* OEM hook to Get the Answer to reset of the current SIM card */
    int RIL_OEM_HOOK_STRING_GET_ATR = 0x000000A5;
    /* OEM hook to get GPRS cell environment */
    int RIL_OEM_HOOK_STRING_GET_GPRS_CELL_ENV = 0x000000A6;
    /* OEM hook to dump the cell environment */
    int RIL_OEM_HOOK_STRING_DEBUG_SCREEN_COMMAND = 0x000000A7;
    /* OEM hook to release all the calls */
    int RIL_OEM_HOOK_STRING_RELEASE_ALL_CALLS = 0x000000A8;
    /* OEM hook to get the SMS Transport Mode */
    int RIL_OEM_HOOK_STRING_GET_SMS_TRANSPORT_MODE = 0x000000A9;
    /* OEM hook to set the SMS Transport Mode */
    int RIL_OEM_HOOK_STRING_SET_SMS_TRANSPORT_MODE = 0x000000AA;
    /* OEM hook specific to DSDS for swapping protocol stacks configs */
    int RIL_OEM_HOOK_STRING_SWAP_PS = 0x000000B2;
    /* OEM hook to get the thermal alarm indication */
    int RIL_OEM_HOOK_RAW_UNSOL_THERMAL_ALARM_IND = 0x000000D0;
    /* OEM hook specific to DSDS for catching out of service URC */
    int RIL_OEM_HOOK_RAW_UNSOL_FAST_OOS_IND = 0x000000D1;
    /* OEM hook specific to DSDS for catching in service URC */
    int RIL_OEM_HOOK_RAW_UNSOL_IN_SERVICE_IND = 0x000000D2;
    /* OEM hook specific to indicate data suspended/resume status */
    int RIL_OEM_HOOK_RAW_UNSOL_DATA_STATUS_IND = 0x000000D3;
    /* OEM hook specific to indicate MT class */
    int RIL_OEM_HOOK_RAW_UNSOL_MT_CLASS_IND = 0x000000D4;
}
