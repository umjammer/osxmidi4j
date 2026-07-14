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

import com.github.osxmidi4j.midiservices.CoreMidiLibrary.MIDIReadProc;
import com.sun.jna.NativeLong;

import static com.github.osxmidi4j.midiservices.CoreMidiLibrary.INSTANCE;


/**
 * CoreMidi::MidiInputPort.
 */
public class MidiInputPort {

    private final NativeLong midiPortRef;

    private final String name;

    /**
     * CoreMidi calls this proc for the whole life of the native port, but JNA only holds callbacks
     * weakly: once no java code references it, its native trampoline is freed and CoreMidi ends up
     * calling a dangling pointer. Holding it here binds its life to the native port's life.
     *
     * @see MidiClient#inputPortCreate
     */
    private final MIDIReadProc readProc;

    MidiInputPort(NativeLong midiPortRef, String name, MIDIReadProc readProc) {
        this.midiPortRef = midiPortRef;
        this.name = name;
        this.readProc = readProc;
    }

    public void connectSource(MidiEndpoint source) throws CoreMidiException {
        int midiPortConnectSource = INSTANCE.MIDIPortConnectSource(midiPortRef, source.endpointRef(), null);
        if (midiPortConnectSource != 0) {
            throw new CoreMidiException(midiPortConnectSource);
        }
    }

    public void disconnectSource(MidiEndpoint source) throws CoreMidiException {
        int midiPortDisconnectSource = INSTANCE.MIDIPortDisconnectSource(midiPortRef, source.endpointRef());
        if (midiPortDisconnectSource != 0) {
            throw new CoreMidiException(midiPortDisconnectSource);
        }
    }

    /** Disposes the native port. Use {@link MidiClient#inputPortDispose(MidiInputPort)}, which also unregisters it. */
    void dispose() throws CoreMidiException {
        int midiPortDispose = INSTANCE.MIDIPortDispose(midiPortRef);
        if (midiPortDispose != 0) {
            throw new CoreMidiException(midiPortDispose);
        }
    }

    @Override
    public String toString() {
        return "MidiInputPort(" + name + ")@" + midiPortRef.longValue();
    }
}
