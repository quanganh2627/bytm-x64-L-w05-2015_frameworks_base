/*
 * Copyright 2010 Giesecke & Devrient GmbH.
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

package org.simalliance.openmobileapi.service;

import org.simalliance.openmobileapi.service.ISmartcardServiceCallback;
import org.simalliance.openmobileapi.service.SmartcardError;

/**
 * Smartcard service interface.
 */
interface ISmartcardSystemService {

    /**
     * Closes the specified connection and frees internal resources.
     * A logical channel will be closed.
     */
    void closeChannel(long hChannel);

    /**
     * Returns the friendly names of available smart card readers.
     */
    String getReaders();

    /**
     * Returns true if a card is present in the specified reader.
     * Returns false if a card is not present in the specified reader.
     */
    boolean isCardPresent(String reader);

    /**
     * Opens a connection using the basic channel of the card in the
     * specified reader and returns a channel handle.
     * Logical channels cannot be opened with this connection.
     * Use interface method openLogicalChannel() to open a logical channel.
     */
    long openBasicChannel(String reader);

    /**
     * Opens a connection with the specific AID using the basic channel of the card in the
     * specified reader and returns a channel handle.
     * Logical channels cannot be opened with this connection.
     * Use interface method openLogicalChannel() to open a logical channel.
     */
    long openBasicChannelAid(String reader, String aid);

    /**
     * Opens a connection using the next free logical channel of the card in the
     * specified reader. Selects the specified applet.
     * Selection of other applets with this connection is not supported.
     */
    long openLogicalChannel(String reader, String aid);

    /**
     * Transmits the specified command APDU and returns the response APDU.
     * MANAGE channel commands are not supported.
     * Selection of applets is not supported in logical channels.
     */
    String transmit(long hChannel, in String command);

    /**
     * Returns a string containing a textual description of the exception
     * occured within the last call to the SmartcardService.
     * It returns an emptry string, if no error occured.
     */
    String getLastError();

}
