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

import com.sun.jna.NativeLong;

import static com.github.osxmidi4j.midiservices.CoreMidiLibrary.INSTANCE;


/**
 * CoreMidi::MidiInputPort.
 */
public class MidiInputPort {

    private final NativeLong midiPortRef;

    private final String name;

    public MidiInputPort(NativeLong midiPortRef, String name) {
        this.midiPortRef = midiPortRef;
        this.name = name;
    }

    public void connectSource(final MidiEndpoint source) throws CoreMidiException {
        int midiPortConnectSource = INSTANCE.MIDIPortConnectSource(midiPortRef, source.getEndpointRef(), null);
        if (midiPortConnectSource != 0) {
            throw new CoreMidiException(midiPortConnectSource);
        }
    }

    public void disconnectSource(final MidiEndpoint source) throws CoreMidiException {
        int midiPortDisconnectSource = INSTANCE.MIDIPortDisconnectSource(midiPortRef, source.getEndpointRef());
        if (midiPortDisconnectSource != 0) {
            throw new CoreMidiException(midiPortDisconnectSource);
        }
    }

    @Override
    public String toString() {
        return "MidiInputPort(" + name + ")@" + midiPortRef.longValue();
    }
}
