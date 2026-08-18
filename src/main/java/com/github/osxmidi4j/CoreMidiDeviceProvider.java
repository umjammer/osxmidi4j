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

import static com.github.osxmidi4j.midiservices.CoreMidiLibrary.INSTANCE;


/**
 * system property
 * <li>com.github.osxmidi4j.loopback ... create default loop back device or not, default {@code ture}</li>
 */
public class CoreMidiDeviceProvider extends MidiDeviceProvider {

    public static final String DEVICE_NAME_PREFIX = "CoreMidi - ";

    public static final String DEFAULT_DESTINATION = "CoreMIDI Loopback Destination";

    private static final int DEVICE_MAP_SIZE = 20;

    private static final class MidiProperties {
        private MidiClient client;
        private MidiOutputPort output;
        private final Map<Integer, MidiDevice> deviceMap = new LinkedHashMap<>(DEVICE_MAP_SIZE);
        /** the virtual destination we create ourselves, {@code null} when the loop back device is not used */
        private MidiEndpoint loopback;
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
                    props.client = new MidiClient("CAProvider", new NotificationReceiver());
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

    static boolean isMac() {
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

    /**
     * Rebuilds the device map from what CoreMidi currently exposes.
     * <p>
     * Devices that are still there keep their instance: callers may hold and have opened them, and an open
     * {@link CoreMidiSource} owns the native input port CoreMidi calls back on. Devices that are gone are
     * closed, which disposes that port.
     */
    private void buildDeviceMap() throws CoreMidiException {
        Map<Integer, MidiDevice> devices = new LinkedHashMap<>(DEVICE_MAP_SIZE);

        // 1. source
        int count = INSTANCE.MIDIGetNumberOfSources().intValue();
        for (int source = 0; source < count; source++) {
            NativeLong endpointRef = INSTANCE.MIDIGetSource(new NativeLong(source));
            MidiEndpoint ep = new MidiEndpoint(endpointRef);
            Integer uid = ep.getProperty(CoreMidiLibrary.kMIDIPropertyUniqueID);

            MidiDevice device = props.deviceMap.get(uid);
            if (device == null) {
logger.log(Level.DEBUG, "add CoreMidiSources: " + ep.getStringProperty(CoreMidiLibrary.kMIDIPropertyName));
                device = new CoreMidiSource(ep, uid);
            }
            devices.put(uid, device);
        }
        // 2. destination
        count = INSTANCE.MIDIGetNumberOfDestinations().intValue();
        for (int dest = 0; dest < count; dest++) {
            NativeLong endpointRef = INSTANCE.MIDIGetDestination(new NativeLong(dest));
            MidiEndpoint ep = new MidiEndpoint(endpointRef);
            try {
                Integer uid = ep.getProperty(CoreMidiLibrary.kMIDIPropertyUniqueID);

                MidiDevice device = props.deviceMap.get(uid);
                if (device == null) {
logger.log(Level.DEBUG, "add CoreMidiDestination: " + ep.getStringProperty(CoreMidiLibrary.kMIDIPropertyName));
                    device = new CoreMidiDestination(ep, uid);
                }
                devices.put(uid, device);
            } catch (CoreMidiException e) {
logger.log(Level.WARNING, e.toString());
            }
        }

        // 3. loop-back
        // TODO i wanna add call back to default destination like
        //  MIDISetCallbackToDestination(ep, readProc);
        // https://stackoverflow.com/a/68162041
        // created once, it lives as long as the client and is enumerated as a destination on the next builds
        if (props.loopback == null
                && Boolean.parseBoolean(System.getProperty("com.github.osxmidi4j.loopback", "false"))) {
            props.loopback = props.client.destinationCreate(DEFAULT_DESTINATION, CoreMidiDeviceProvider::readProc);
            Integer uid = props.loopback.getProperty(CoreMidiLibrary.kMIDIPropertyUniqueID);
logger.log(Level.DEBUG, "add CoreMidiDestination: " + DEFAULT_DESTINATION);
            devices.put(uid, new CoreMidiDestination(props.loopback, uid));
        }

        props.deviceMap.forEach((uid, device) -> {
            if (!devices.containsKey(uid)) {
logger.log(Level.DEBUG, "remove: " + device.getDeviceInfo().getName());
                device.close();
            }
        });
        props.deviceMap.clear();
        props.deviceMap.putAll(devices);
logger.log(Level.DEBUG, "devices: " + props.deviceMap.size());
    }

    /** for loop-back */
    private static void readProc(MIDIPacketList pktlist, Pointer readProcRefCon, Pointer srcConnRefCon) {
logger.log(Level.DEBUG, "readProc for " + DEFAULT_DESTINATION + " called");
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
        return props.client.getNotifyProc();
    }
}
