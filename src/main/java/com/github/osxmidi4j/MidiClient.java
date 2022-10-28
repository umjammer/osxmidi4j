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

import org.rococoa.Foundation;
import org.rococoa.ID;

import com.github.osxmidi4j.midiservices.CoreMidiLibrary.MIDINotifyProc;
import com.github.osxmidi4j.midiservices.CoreMidiLibrary.MIDIReadProc;
import com.sun.jna.NativeLong;
import com.sun.jna.ptr.NativeLongByReference;

import static com.github.osxmidi4j.midiservices.CoreMidiLibrary.INSTANCE;


public class MidiClient {

    private static final Logger logger = Logger.getLogger(MidiClient.class.getName());

    private final NativeLong midiClientRef;

    public MidiClient(String name, MIDINotifyProc notifyProc) throws CoreMidiException {
        ID nameId = Foundation.cfString(name);
        NativeLongByReference clientRef = new NativeLongByReference();

        int osStatus = INSTANCE.MIDIClientCreate(nameId, notifyProc, null, clientRef);
        if (osStatus != 0) {
            throw new CoreMidiException(osStatus);
        }
logger.fine("MidiClientRef: " + name + ", " + clientRef.getValue().longValue());
        midiClientRef = clientRef.getValue();
    }

    public MidiOutputPort outputPortCreate(String name) throws CoreMidiException {
        NativeLongByReference portRef = new NativeLongByReference();
        ID nameId = Foundation.cfString(name);
        int osStatus = INSTANCE.MIDIOutputPortCreate(midiClientRef, nameId, portRef);
        if (osStatus != 0) {
            throw new CoreMidiException(osStatus);
        }
logger.fine("MidiOutputPort: " + name + ", " + portRef.getValue());
        return new MidiOutputPort(portRef.getValue(), name);
    }

    public MidiInputPort inputPortCreate(String name, MIDIReadProc readProc) throws CoreMidiException {
        NativeLongByReference portRef = new NativeLongByReference();
        ID nameId = Foundation.cfString(name);
        int osStatus = INSTANCE.MIDIInputPortCreate(midiClientRef, nameId, readProc, null, portRef);
        if (osStatus != 0) {
            throw new CoreMidiException(osStatus);
        }
logger.fine("MidiInputPort: " + name + ", " + portRef.getValue());
        return new MidiInputPort(portRef.getValue(), name);
    }

    public NativeLong getMidiClientRef() {
        return midiClientRef;
    }
}
