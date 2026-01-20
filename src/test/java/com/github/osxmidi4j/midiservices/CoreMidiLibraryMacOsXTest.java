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
package com.github.osxmidi4j.midiservices;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.rococoa.Foundation;
import org.rococoa.ID;
import org.rococoa.IDByReference;

import com.github.osxmidi4j.midiservices.CoreMidiLibrary.MIDINotifyProc;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.NativeLongByReference;

import static com.github.osxmidi4j.midiservices.CoreMidiLibrary.INSTANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class CoreMidiLibraryMacOsXTest {

    private static final Logger logger = System.getLogger(CoreMidiLibraryMacOsXTest.class.getName());

    @BeforeEach
    public void setUp() throws Exception {

        // Create client
        ID clientName = Foundation.cfString("Client");
        MIDINotifyProc notifyProc = (message, refCon) -> {};
        NativeLongByReference nativeLongByReference = new NativeLongByReference();
        logger.log(Level.INFO, String.valueOf(nativeLongByReference.getValue().longValue()));
        int osStatus = INSTANCE.MIDIClientCreate(clientName, notifyProc, null, nativeLongByReference);
        logger.log(Level.INFO, String.valueOf(nativeLongByReference.getValue().longValue()));
        logger.log(Level.INFO, String.valueOf(osStatus));
    }

    @AfterEach
    public void tearDown() throws Exception {
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*") // TODO why ga env midi doesn't have in/out???
    public void testNumPorts() {
        NativeLong numberOfDestinations = INSTANCE.MIDIGetNumberOfDestinations();
        assertTrue(numberOfDestinations.intValue() >= 1, "numberOfDestinations: " + numberOfDestinations.intValue());
        NativeLong numberOfSources = INSTANCE.MIDIGetNumberOfSources();
        assertTrue(numberOfSources.intValue() >= 1);
    }

    @Test
    public void testCFStringReturn() {
        String prop = CoreMidiLibrary.kMIDIPropertyName;
        Pointer kMIDIPropertyName = CoreMidiLibrary.JNA_NATIVE_LIB.getGlobalVariableAddress(prop);
        ID fromLong = ID.fromLong(kMIDIPropertyName.getNativeLong(0).longValue());
        String result = Foundation.toString(fromLong);
        logger.log(Level.INFO, result);
        assertEquals("name", result);
    }

    @Test
    public void testGetProperty() {

        // Get ports
        int numberOfDevices = INSTANCE.MIDIGetNumberOfDevices().intValue();
        for (int i = 0; i < numberOfDevices; i++) {
            NativeLong deviceRef = INSTANCE.MIDIGetDevice(new NativeLong(i));

            int numEntities = INSTANCE.MIDIDeviceGetNumberOfEntities(deviceRef).intValue();
            for (int j = 0; j < numEntities; j++) {
                NativeLong entDestination = INSTANCE.MIDIDeviceGetEntity(deviceRef, new NativeLong(j));

                int numSources = INSTANCE.MIDIEntityGetNumberOfSources(entDestination).intValue();
                for (int k = 0; k < numSources; k++) {
                    NativeLong endPointRef = INSTANCE.MIDIEntityGetSource(entDestination, new NativeLong(k));
                    printPropertyName(endPointRef);
                }
            }
        }
    }

    static void printPropertyName(NativeLong ref) {
        Pointer kMIDIPropertyName = CoreMidiLibrary.JNA_NATIVE_LIB.getGlobalVariableAddress("kMIDIPropertyName");
        long longValue = kMIDIPropertyName.getNativeLong(0).longValue();
        ID fromLong = ID.fromLong(longValue);

        // Get property
        IDByReference reference = new IDByReference();
        int osStatus = INSTANCE.MIDIObjectGetStringProperty(ref.longValue(), fromLong, reference);
        assertEquals(0, osStatus);
        String s = Foundation.toString(reference.getValue());
        logger.log(Level.INFO, "Length: " + s.length() + ", " + s);
    }
}
