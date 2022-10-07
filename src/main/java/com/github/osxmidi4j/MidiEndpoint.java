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

import java.nio.IntBuffer;

import java.util.logging.Logger;

import org.rococoa.Foundation;
import org.rococoa.ID;
import org.rococoa.IDByReference;

import com.github.osxmidi4j.midiservices.CoreMidiLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import static com.github.osxmidi4j.midiservices.CoreMidiLibrary.INSTANCE;


/**
 * Represents real pointer to MidiSource, MidiDestination.
 */
public class MidiEndpoint {

    private static final int BUFFER_SIZE = 256;
    private final NativeLong endpointRef;

    public MidiEndpoint(NativeLong endpointRef) {
        this.endpointRef = endpointRef;
    }

    public int getProperty(String kMidiPropertyOffline) throws CoreMidiException {
        ID propertyId = getPropertyId(kMidiPropertyOffline);
        IntBuffer intBuffer = IntBuffer.allocate(BUFFER_SIZE);
        int osStatus = INSTANCE.MIDIObjectGetIntegerProperty(endpointRef.longValue(), propertyId, intBuffer);
        if (osStatus != 0) {
            throw new CoreMidiException(osStatus);
        }
        return intBuffer.get();
    }

    public String getStringProperty(String kMidiPropertyDriverVersion) throws CoreMidiException {
        ID propertyId = getPropertyId(kMidiPropertyDriverVersion);
        IDByReference reference = new IDByReference();
        int osStatus = INSTANCE.MIDIObjectGetStringProperty(endpointRef.longValue(), propertyId, reference);
        if (osStatus == 0) {
            return Foundation.toString(reference.getValue());
        } else {
            throw new CoreMidiException(osStatus);
        }
    }

    private ID getPropertyId(String propertyName) {
        Pointer p = CoreMidiLibrary.JNA_NATIVE_LIB.getGlobalVariableAddress(propertyName);
        return ID.fromLong(p.getNativeLong(0).longValue());
    }

    public NativeLong getEndpointRef() {
        return endpointRef;
    }

    @Override
    public String toString() {
        return "MidiEndpoint@" + endpointRef.longValue();
    }
}
