//
// Copyright (c) 2013 All Right Reserved, Pascal Collberg
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

import java.util.logging.Logger;

import com.github.osxmidi4j.midiservices.CoreMidiLibrary;
import com.github.osxmidi4j.midiservices.MIDIPacketList;
import com.sun.jna.NativeLong;


/**
 * CoreMidi::MidiOutputPort.
 */
public class MidiOutputPort {

    private static final Logger logger = Logger.getLogger(MidiOutputPort.class.getName());

    private final NativeLong midiPortRef;

    private final String name;

    public MidiOutputPort(NativeLong midiPortRef, String name) {
        this.midiPortRef = midiPortRef;
        this.name = name;
    }

    public void send(MidiEndpoint dest, MIDIPacketList packets) throws CoreMidiException {
        int midiSend = CoreMidiLibrary.INSTANCE.MIDISend(midiPortRef, dest.getEndpointRef(), packets.getPointer());
logger.fine("send: " + midiSend);
        if (midiSend != 0) {
            throw new CoreMidiException(midiSend);
        }
    }

    @Override
    public String toString() {
        return "MidiOutoutPort(" + name + ")@" + midiPortRef.longValue();
    }
}
