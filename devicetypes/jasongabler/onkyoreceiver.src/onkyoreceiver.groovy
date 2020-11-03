/**
 *  Onkyo Receiver
 *
 *  A SmartThings device handler to control the volume on Onkyo and later model Pioneer receivers
 *
 * =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-==-=-=-=
 *
 * Installation:
 * - In the SmartThings IDE
 *   1. Click "My Device Handlers" (at the very top)
 *   2. Click "Create New Device Handler"
 *   3. Click the "From Code" tab
 *   4. Paste this entire code into the large text area
 *   5. Click "Create"
 *   6. Click "Publish"
 *   7. Click "My Devices" (at the very top)
 *   8. Click "New Device"
 *   9. Set the Device Network Id to <IP address>:<port> in a hex string representation
 *      where   <octet A decimal>.<octet B decimal>.<octet C decimal>.<octet D decimal>:<port decimal>
 *      becomes <octet A hex><octet B hex><octet C hex><octet AD hex>:<port hex>
 *      For example, 192.168.0.134:60128 becomes C0A80086:EAE0
 *      Here's a handy converter: https://www.rapidtables.com/convert/number/decimal-to-hex.html
 *  10. Set all other device settings as desired and save. The device will then show up as a device
 *      within your SmartThings mobile app.
 *
 * =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-==-=-=-=
 *
 * Planned changes:
 * - Enabled setting receiver IP address within the SmartThing devices settings in the mobile app.
 * - Permit leaving out the port and default to 60128
 * Known bugs:
 * - Mobile app stalls after using slider and reverts to 0 (though volume remains set at desired change)
 * - Mobible app slider does to reflect current receiver volume, whether changed by mobile app or
 *   external manipulator
 * Other Notes:
 * - I have created this device handler using a Pioneer 2018 VSX-LX503. eISCP commands and the volume values
 *   may differ between different models of Onkyo and later Pioneer receivers. 
 * - Pioneer used to have an elegant, all ASCII command set which you could send to the receiver via
 *   simple telnet/netcat. When Onkyo bought Pioneer, they shoved Onkyo's archaicly complex command
 *   structure and processing into Pioneer receivers and bounced them back technologically by ten to
 *   twenty years. This is why getEiscpMessage() creates such ridiculous messages.
 *
 * =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-==-=-=-=
 *
 *  Copyright 2020 Jason Gabler
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  This code is based upon a forked from Allan Klein's (@allanak) GitHub repo
 */

metadata {
    definition (name: "OnkyoReceiver", namespace: "jasongabler", author: "Jason Gabler", cstHandler: true) {
        capability "Audio Volume"
    }

    simulator {
        // TODO: define status and reply messages here
    }

    tiles {
        controlTile("levelSliderControl", "device.volume", "slider", height: 1, width: 2) {
            state "${currentValue}", action:"switch level.setLevel"
        }        
        standardTile("actionFlat", "device.volume", width: 2, height: 2, decoration: "flat") {
            tileAttribute("device.volume", key: "VALUE_CONTROL") {
                attributeState "VALUE_UP", action: "volumeUp"
                attributeState "VALUE_DOWN", action: "volumeDown"
            }
        }

        main("levelSliderControl")
        details(["levelSliderControl","actionFlat"])
    }
}


def parse(description) {
    log.debug "Parsing '${description}'"
    //return createEvent(name: "volume", value: "${currentVolume}")
    def volume = device.currentValue("volume")
    log.debug "volume: ${volume}"
    sendEvent(name:"volume", value: "${volume}")
}

// handle commands
def setVolume(volume) {
    if (volume < 0)	volume = 0
    if( volume > 100) volume = 100

    String volhex = String.format("%02x", (int)(volume*2))
    def result = sendMsg("MVL${volhex}")
    sendEvent(name:"volume", value: (volume))
    return result
}       


def volumeUp() {
    return sendMsg("MVLUP")
}


def volumeDown() {
    return sendMsg("MVLDOWN")
}


def sendMsg(rawMsg) {
    log.debug "sendMsg: ["+rawMsg+"]"
    def msg = getEiscpMessage(rawMsg)
    def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN )
    log.debug "HubAction: ["+ha.toString()+"]"
	sendEvent(name:"volume", value: volume)
    log.debug "sendEvent: ["+ha.toString()+"]"
    return ha
}

/* 
 * Build raw eISCP message to send to receiver. Some parts are copied in regular ASCII and some parts
 * are converted to hex string representation.
 */
def getEiscpMessage(command){
	def sb = StringBuilder.newInstance()
	def eiscpDataSize = command.length() + 3  // this is the eISCP data size
	def eiscpMsgSize = eiscpDataSize + 1 + 16 // this is the size of the entire eISCP msg


    // Begin with the prefix
	sb.append("ISCP")

	// 4 char Big Endian Header
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("10", 16))

	// 4 char  Big Endian data size
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))
    
	// Official ISCP documentation defined the next block the data size (stored in eiscpDataSize).
    //sb.append((char)Integer.parseInt(Integer.toHexString(eiscpMsgSize), 16))
	// However, it seems to only work if sending the size of the entire message (stored in eiscpMsgSize).
	sb.append((char)Integer.parseInt(Integer.toHexString(eiscpDataSize), 16))

	// eiscp_version = "01";
	sb.append((char)Integer.parseInt("01", 16))

	// 3 chars reserved = "00"+"00"+"00";
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))

	// eISCP data: start character
	sb.append("!")

	// eISCP data: unittype char '1' is the receiver
	sb.append("1")

	// eISCP data: the actual command string and param
    // E.g. For "PWR01" the command is "PWR", immediately follow by "01"
	sb.append(command)

	// eISCP footer: can differ depending on the receiver model. 
    // Carriage return (CR) seems to widely accepted.
    // For reference, other possible values may be: CR is 0x0D, LF is 0x0A, EOF is 0x1A
	sb.append((char)Integer.parseInt("0D", 16))

	return sb.toString()
}
    

/*
 * Utility to convert a character string to a hex string of its bytes.
 * It can be useful in printing the output from getEiscpMessage() to the debug log, and then taking
 * that hex string and running it through netcat directly to the receiver. For example, the following
 * command sents the output from this method to set the volumn to 52% on my receiver.
 * $ echo "49534350000000100000000A0100000021314D564C5153544E0D" | xxd -r -p |  nc 192.168.0.134 60128  # MVL52
 */
def bytesToHex(str){
    def bytes = str.getBytes();
    def HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    def hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
        int v = bytes[j] & 0xFF;
        hexChars[j * 2] = HEX_ARRAY[v >>> 4];
        hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
}
