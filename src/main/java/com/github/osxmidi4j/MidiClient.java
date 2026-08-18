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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.rococoa.Foundation;
import org.rococoa.ID;

import com.github.osxmidi4j.midiservices.CoreMidiLibrary.MIDINotifyProc;
import com.github.osxmidi4j.midiservices.CoreMidiLibrary.MIDIReadProc;
import com.sun.jna.NativeLong;
import com.sun.jna.ptr.NativeLongByReference;

import static com.github.osxmidi4j.midiservices.CoreMidiLibrary.INSTANCE;


/**
 * CoreMidi::MidiClient.
 * <p>
 * Every callback given to CoreMidi is kept referenced here until the native object that uses it is
 * disposed. JNA holds callbacks only weakly (its {@code CallbackReference} is a {@link java.lang.ref.WeakReference}
 * and frees the native trampoline once the java callback is collected), while CoreMidi keeps calling
 * the pointer it was given. Without those strong references the callbacks silently die at the first gc.
 */
public class MidiClient {

    private static final Logger logger = System.getLogger(MidiClient.class.getName());

    private final NativeLong midiClientRef;

    /** the proc CoreMidi calls for the life of the native client */
    private final MIDINotifyProc notifyProc;

    /** the ports created by this client, each holding the proc CoreMidi calls for the life of that native port */
    private final List<MidiInputPort> inputPorts = new CopyOnWriteArrayList<>();

    /** the procs CoreMidi calls for the life of the native destinations created by this client */
    private final Map<NativeLong, MIDIReadProc> destinationReadProcs = new ConcurrentHashMap<>();

    public MidiClient(String name, MIDINotifyProc notifyProc) throws CoreMidiException {
        ID nameId = Foundation.cfString(name);
        NativeLongByReference clientRef = new NativeLongByReference();

        int osStatus = INSTANCE.MIDIClientCreate(nameId, notifyProc, null, clientRef);
        if (osStatus != 0) {
            throw new CoreMidiException(osStatus);
        }
logger.log(Level.DEBUG, "MidiClientRef: " + name + ", " + clientRef.getValue().longValue());
        midiClientRef = clientRef.getValue();
        this.notifyProc = notifyProc;
    }

    public MidiOutputPort outputPortCreate(String name) throws CoreMidiException {
        NativeLongByReference portRef = new NativeLongByReference();
        ID nameId = Foundation.cfString(name);
        int osStatus = INSTANCE.MIDIOutputPortCreate(midiClientRef, nameId, portRef);
        if (osStatus != 0) {
            throw new CoreMidiException(osStatus);
        }
logger.log(Level.DEBUG, "MidiOutputPort: " + name + ", " + portRef.getValue());
        return new MidiOutputPort(portRef.getValue(), name);
    }

    public MidiInputPort inputPortCreate(String name, MIDIReadProc readProc) throws CoreMidiException {
        NativeLongByReference portRef = new NativeLongByReference();
        ID nameId = Foundation.cfString(name);
        int osStatus = INSTANCE.MIDIInputPortCreate(midiClientRef, nameId, readProc, null, portRef);
        if (osStatus != 0) {
            throw new CoreMidiException(osStatus);
        }
logger.log(Level.DEBUG, "MidiInputPort: " + name + ", " + portRef.getValue());
        MidiInputPort inputPort = new MidiInputPort(portRef.getValue(), name, readProc);
        inputPorts.add(inputPort);
        return inputPort;
    }

    /** Disposes the native port, after which its read proc is no longer referenced. */
    public void inputPortDispose(MidiInputPort inputPort) throws CoreMidiException {
        inputPort.dispose();
        inputPorts.remove(inputPort);
logger.log(Level.DEBUG, "MidiInputPort disposed: " + inputPort);
    }

    /** Creates a virtual destination owned by this client, CoreMidi calls {@code readProc} on what is sent to it. */
    public MidiEndpoint destinationCreate(String name, MIDIReadProc readProc) throws CoreMidiException {
        NativeLongByReference destRef = new NativeLongByReference();
        ID nameId = Foundation.cfString(name);
        int osStatus = INSTANCE.MIDIDestinationCreate(midiClientRef, nameId, readProc, null, destRef);
        if (osStatus != 0) {
            throw new CoreMidiException(osStatus);
        }
        NativeLong endpointRef = destRef.getValue();
logger.log(Level.DEBUG, "MidiDestination: " + name + ", " + endpointRef);
        destinationReadProcs.put(endpointRef, readProc);
        return new MidiEndpoint(endpointRef);
    }

    /** Disposes the native destination, after which its read proc is no longer referenced. */
    public void destinationDispose(MidiEndpoint destination) throws CoreMidiException {
        int osStatus = INSTANCE.MIDIEndpointDispose(destination.endpointRef());
        if (osStatus != 0) {
            throw new CoreMidiException(osStatus);
        }
        destinationReadProcs.remove(destination.endpointRef());
logger.log(Level.DEBUG, "MidiDestination disposed: " + destination);
    }

    public NativeLong getMidiClientRef() {
        return midiClientRef;
    }

    MIDINotifyProc getNotifyProc() {
        return notifyProc;
    }
}
