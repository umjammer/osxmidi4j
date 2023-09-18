//
// Copyright (c) 2013 All Right Reserved, Pascal Collberg and the author of CAProvider
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
//

package com.github.osxmidi4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;


import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.osxmidi4j.midiservices.CoreMidiLibrary;
import com.github.osxmidi4j.midiservices.MIDIPacket;
import com.github.osxmidi4j.midiservices.MIDIPacketList;
import com.github.osxmidi4j.midiservices.MIDISysexSendRequest;
import com.sun.jna.Pointer;


public class CoreMidiReceiver implements Receiver {

    private static final Logger logger = Logger.getLogger(CoreMidiReceiver.class.getName());

    private final MidiEndpoint dest;
    private final Set<Pointer> sendRequests = new HashSet<>();

    CoreMidiReceiver(MidiEndpoint ep) {
        dest = ep;
    }

    public void close() {
        // Not needed.
    }

    public void send(MidiMessage message, long timeStamp) {
        try {
            if (dest.getProperty(CoreMidiLibrary.kMIDIPropertyOffline) == 1) {
                logger.info("midi device is offline");
                return;
            }
        } catch (CoreMidiException e) {
            // -10835 kMIDIUnknownProperty Attempt to query a property not set on the object.
            // https://de.osdn.net/projects/miditrail/ticket/32542
            if (e.getErrorCode() != -10835) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
        try {
            // Don't deal with message directly because of bugs
            if (message instanceof ShortMessage) {
                ShortMessage m = (ShortMessage) message;
                MIDIPacketList midiPacketList = MIDIPacketList.Factory.newInstance();
                midiPacketList.add(new MIDIPacket(m));
                CoreMidiDeviceProvider.getOutputPort().send(dest, midiPacketList);
logger.fine("send short message: " + m + ", to MidiDestination: " + dest);
            } else if (message instanceof SysexMessage) {
                SysexMessage m = (SysexMessage) message;
                ByteArrayInputStream is;
                if (m.getStatus() == SysexMessage.SPECIAL_SYSTEM_EXCLUSIVE) {
                    is = new ByteArrayInputStream(m.getData());
                } else {
                    is = new ByteArrayInputStream(m.getMessage());
                }

                byte[] buf = new byte[MIDIPacket.DATA_SIZE];
                int read = 0;
                while ((read = is.read(buf)) != -1) {
                    MIDIPacket midiPacket = new MIDIPacket(timeStamp, (short) read, buf); // NOPMD 2013-10-04 21:51
                    MIDISysexSendRequest req = MIDISysexSendRequest.newInstance(dest, midiPacket, this::completed);
                    int midiSendSysex = CoreMidiLibrary.INSTANCE.MIDISendSysex(req .getPointer());
                    if (midiSendSysex != 0) {
                        throw new CoreMidiException(midiSendSysex);
                    }
                    sendRequests.add(req.getPointer());
logger.fine("add sendRequests sysex message: " + m + ", queue: " + sendRequests.size() + " to MidiDestination: " + dest);
                }
            }
        } catch (CoreMidiException | IOException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    private void completed(Pointer request) {
        sendRequests.remove(request);
logger.fine("Completed sendRequests: " + request + ", queue: " + sendRequests.size());
    }
}
