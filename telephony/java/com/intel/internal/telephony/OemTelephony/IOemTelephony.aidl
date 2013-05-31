/*
 * Copyright (C) 2012 Intel Corporation, All rights Reserved
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

/**
 * Interface used to interact with the OEM Hook.
 *
 * {@hide}
 */
interface IOemTelephony {


    /**
     * On Success returns the Answer to Reset of the current active SIM card.
     * On Failure, returns an empty string.
     *
     * {@hide}
     */
    String getATR();

    /**
     * Retrieves the temperature of the selected modem sensor.
     * @param sensorId: Sensor id
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#MODEM_SENSOR_ID_RF},
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#MODEM_SENSOR_ID_BASEBAND_CHIP},
     *          or {@link com.intel.internal.telephony.OemTelephony.
     *                   OemTelephonyConstants#MODEM_SENSOR_ID_PCB}
     *
     * @return On Success, String containg the temperatures separated by space
     *         "Filtered temperature Raw Temperature". Both temperature
     *          are formated as a 4 digits number: "2300" = 23.00 celcius
     *          degrees  (two digits after dot).
     *         On Failure, returns an empty string.
     * {@hide}
     */
    String getThermalSensorValue(int sensorId);

    /**
     * Gets a dump of the GPRS cell environment.
     *
     * @return On Success, returns a string containing a dump of GPRS Cell Environment
     *         On Failure, returns an empty string.
     * {@hide}
     */
    String getGprsCell();

    /**
     * Retrieves Cell Environment
     * @param page: page_nr
     * @return On Success, string containing a dump of the cell environment
     *         On Failure, returns an empty string.
     * {@hide}
     */
    String getDumpScreen(int page);

    /**
     * Activates the modem sensor alarm notification when the selected
     * sensor temperature is below the minimal threshold or above the
     * maximal threshold value.
     *
     * @param activate: activate the threshold notification when true,
     *                  else deactivates the notification.
     * @param sensorId: Sensor id
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#MODEM_SENSOR_ID_RF},
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#MODEM_SENSOR_ID_BASEBAND_CHIP},
     *          or {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#MODEM_SENSOR_ID_PCB}
     *
     * @param minThresholdValue: temperature are formated as a 4 digits number:
     *                  "2300" = 23.00 celcius degrees  (two digits after dot)
     * @param maxThresholdValue: temperature are formated as a 4 digits number:
     *                  "2300" = 23.00 celcius degrees  (two digits after dot)
     * {@hide}
     */
    oneway void ActivateThermalSensorNotification(boolean activate, int sensorId,
                            int minThresholdValue, int maxThresholdValue);

    /**
     * Retrieves the SMS Transport Mode.
     * @return On Success, int representing the SMS Transport Mode,
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#SMS_TRANSPORT_MODE_PACKET_DOMAIN},
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#SMS_TRANSPORT_MODE_CIRCUIT_SWITCHED},
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#SMS_TRANSPORT_MODE_PACKET_DOMAIN_PREFERRED},
     *          or {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#SMS_TRANSPORT_MODE_CIRCUIT_SWITCHED_PREFERRED}
     *         On Failure, returns {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#SMS_TRANSPORT_MODE_INVALID}
     *
     * {@hide}
     */
    int getSMSTransportMode();

    /**
     * Sets the SMS Transport Mode.
     * @param transportMode: SMS Transport Mode,
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#SMS_TRANSPORT_MODE_PACKET_DOMAIN},
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#SMS_TRANSPORT_MODE_CIRCUIT_SWITCHED},
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#SMS_TRANSPORT_MODE_PACKET_DOMAIN_PREFERRED},
     *          or {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#SMS_TRANSPORT_MODE_CIRCUIT_SWITCHED_PREFERRED}
     * {@hide}
     */
    oneway void setSMSTransportMode(int transportMode);

    /*
     * Get the currently applied rf power cutback table.
     * @return On Success, int representing the applied table.
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#OFFSET_TABLE_INDEX_0},
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#OFFSET_TABLE_INDEX_1},
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#OFFSET_TABLE_INDEX_2},
     * {@hide}
     */
    int getRFPowerCutbackTable();

    /*
     * Set the currently applied rf power cutback table.
     * @param table the table to apply.
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#OFFSET_TABLE_INDEX_0},
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#OFFSET_TABLE_INDEX_1},
     *          {@link com.intel.internal.telephony.OemTelephony.
     *                  OemTelephonyConstants#OFFSET_TABLE_INDEX_2},
     * {@hide}
     */
    oneway void setRFPowerCutbackTable(int table);
}

