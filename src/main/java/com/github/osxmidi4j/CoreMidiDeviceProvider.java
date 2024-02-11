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

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.spi.MidiDeviceProvider;

import java.lang.System.Logger.Level;
import java.lang.System.Logger;


import com.github.osxmidi4j.midiservices.CoreMidiLibrary;
import com.github.osxmidi4j.midiservices.CoreMidiLibrary.MIDINotifyProc;
import com.github.osxmidi4j.midiservices.MIDINotification;
import com.github.osxmidi4j.midiservices.MIDIPacketList;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.NativeLongByReference;
import org.rococoa.Foundation;
import org.rococoa.ID;

import static com.github.osxmidi4j.midiservices.CoreMidiLibrary.INSTANCE;


public class CoreMidiDeviceProvider extends MidiDeviceProvider {

    public static final String DEVICE_NAME_PREFIX = "CoreMidi - ";

    private static final int DEVICE_MAP_SIZE = 20;

    private static final class MidiProperties {
        private MidiClient client;
        private MidiOutputPort output;
        private final Map<Integer, MidiDevice> deviceMap = new LinkedHashMap<>(DEVICE_MAP_SIZE);
        private MIDINotifyProc notifyProc;
    }

    private static final MidiProperties props = new MidiProperties();

    private static final Logger logger = System.getLogger(CoreMidiDeviceProvider.class.getName());

    public CoreMidiDeviceProvider() throws CoreMidiException {
        if (!isMac()) {
            logger.log(Level.DEBUG, "platform is not mac");
            return;
        }
        synchronized (logger) {
            if (props.client == null) {
                try {
                    props.notifyProc = new NotificationReceiver();
                    props.client = new MidiClient("CAProvider", props.notifyProc);
logger.log(Level.DEBUG, "midi client: " + props.client);
                    props.output = props.client.outputPortCreate("CAMidiDeviceProvider Output");
                    buildDeviceMap();
                } catch (CoreMidiException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                    throw e;
                } catch (Exception e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }
            }
        }
    }

    final boolean isMac() {
        String os = System.getProperty("os.name").toLowerCase();
        return (os.contains("mac"));
    }

    public MidiDevice getDevice(MidiDevice.Info info) {
        if (!isDeviceSupported(info)) {
            throw new IllegalArgumentException();
        }

        CoreMidiDeviceInfo cainfo = (CoreMidiDeviceInfo) info;
        return props.deviceMap.get(cainfo.getUniqueID());
    }

    public MidiDevice.Info[] getDeviceInfo() {
        return props.deviceMap.values().stream().map(MidiDevice::getDeviceInfo).toArray(MidiDevice.Info[]::new);
    }

    public boolean isDeviceSupported(MidiDevice.Info info) {
        boolean foundDevice = false;
        if (info instanceof CoreMidiDeviceInfo cainfo) {
            if (props.deviceMap.containsKey(cainfo.getUniqueID())) {
                foundDevice = true;
            }
        }

        return foundDevice;
    }

    static MidiClient getMIDIClient() throws CoreMidiException {
        if (props.client == null) {
            new CoreMidiDeviceProvider();
        }
        return props.client;
    }

    static MidiOutputPort getOutputPort() {
        return props.output;
    }

    private void buildDeviceMap() throws CoreMidiException {
        int count = INSTANCE.MIDIGetNumberOfSources().intValue();
        for (int source = 0; source < count; source++) {
            NativeLong endpointRef = INSTANCE.MIDIGetSource(new NativeLong(source));
            MidiEndpoint ep = new MidiEndpoint(endpointRef);
            Integer uid = ep.getProperty(CoreMidiLibrary.kMIDIPropertyUniqueID);

            if (!props.deviceMap.containsKey(uid)) {
logger.log(Level.DEBUG, "add CoreMidiSources: " + ep.getStringProperty(CoreMidiLibrary.kMIDIPropertyName));
                props.deviceMap.put(uid, new CoreMidiSource(ep, uid));
            }
        }
logger.log(Level.DEBUG, "devices: " + props.deviceMap.size());
        count = INSTANCE.MIDIGetNumberOfDestinations().intValue();
        for (int dest = 0; dest < count; dest++) {
            NativeLong endpointRef = INSTANCE.MIDIGetDestination(new NativeLong(dest));
            MidiEndpoint ep = new MidiEndpoint(endpointRef);
                Integer uid = ep.getProperty(CoreMidiLibrary.kMIDIPropertyUniqueID);

                if (!props.deviceMap.containsKey(uid)) {
logger.log(Level.DEBUG, "add CoreMidiDestination: " + ep.getStringProperty(CoreMidiLibrary.kMIDIPropertyName));
                    props.deviceMap.put(uid, new CoreMidiDestination(ep, uid));
                }
            }
logger.fine("devices: " + props.deviceMap.size());

        // TODO i wanna do add call back to default destination like
        //  MIDISetCallbackToDestination(ep, readProc);
        // https://stackoverflow.com/a/68162041
        NativeLongByReference outDest = new NativeLongByReference();
        ID nameId = Foundation.cfString("CoreMIDI Loopback Destination");
        int osStatus = INSTANCE.MIDIDestinationCreate(props.client.getMidiClientRef(),
                nameId, this::readProc, null, outDest);
        if (osStatus != 0) {
            logger.log(Level.WARNING, "MIDIDestinationCreate: " + osStatus);
        } else {
            NativeLong endpointRef = outDest.getValue();
            MidiEndpoint ep = new MidiEndpoint(endpointRef);
            Integer uid = ep.getProperty(CoreMidiLibrary.kMIDIPropertyUniqueID);
logger.log(Level.DEBUG, "add CoreMidiDestination: " + ep.getStringProperty(CoreMidiLibrary.kMIDIPropertyName));
            props.deviceMap.put(uid, new CoreMidiDestination(ep, uid));
        }
logger.log(Level.DEBUG, "devices: " + props.deviceMap.size());
    }

    private void readProc(MIDIPacketList pktlist, Pointer readProcRefCon, Pointer srcConnRefCon) {
logger.fine("readProc for CoreMIDI Loopback Destination called");
        props.deviceMap.values().forEach(device -> {
            if (device instanceof CoreMidiSource) {
                ((CoreMidiSource) device).readProc(pktlist, readProcRefCon, srcConnRefCon);
            }
        });
    }

    private class NotificationReceiver implements MIDINotifyProc {
        @Override
        public void apply(MIDINotification message, Pointer refCon) {
            switch (message.getMessageID()) {
            case CoreMidiLibrary.kMIDIMsgObjectAdded:
            case CoreMidiLibrary.kMIDIMsgObjectRemoved:
                props.deviceMap.clear();
                try {
                    buildDeviceMap();
                } catch (CoreMidiException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }
                break;
            default:
                logger.log(Level.DEBUG, "Got " + message.getMessageID());
                break;
            }
        }
    }

    MidiClient getClient() {
        return props.client;
    }

    MidiOutputPort getOutput() {
        return props.output;
    }

    Map<Integer, MidiDevice> getDeviceMap() {
        return props.deviceMap;
    }

    MIDINotifyProc getNproc() {
        return props.notifyProc;
    }
}
