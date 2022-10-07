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

import java.util.logging.Level;
import java.util.logging.Logger;


import com.github.osxmidi4j.midiservices.CoreMidiLibrary;
import com.github.osxmidi4j.midiservices.CoreMidiLibrary.MIDINotifyProc;
import com.github.osxmidi4j.midiservices.MIDINotification;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

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

    private static final Logger logger = Logger.getLogger(CoreMidiDeviceProvider.class.getName());

    public CoreMidiDeviceProvider() throws CoreMidiException {
        if (!isMac()) {
            logger.fine("platform is not mac");
            return;
        }
        synchronized (logger) {
            if (props.client == null) {
                try {
                    props.notifyProc = new NotificationReceiver();
                    props.client = new MidiClient("CAProvider", props.notifyProc);
logger.fine("midi client: " + props.client);
                    props.output = props.client.outputPortCreate("CAMidiDeviceProvider Output");
                    buildDeviceMap();
                } catch (final CoreMidiException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                    throw e;
                } catch (final Exception e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }
            }
        }
    }

    final boolean isMac() {
        final String os = System.getProperty("os.name").toLowerCase();
        return (os.contains("mac"));
    }

    public MidiDevice getDevice(final MidiDevice.Info info) {
        if (!isDeviceSupported(info)) {
            throw new IllegalArgumentException();
        }

        final CoreMidiDeviceInfo cainfo = (CoreMidiDeviceInfo) info;
        return props.deviceMap.get(cainfo.getUniqueID());
    }

    public MidiDevice.Info[] getDeviceInfo() {
        return props.deviceMap.values().stream().map(MidiDevice::getDeviceInfo).toArray(MidiDevice.Info[]::new);
    }

    public boolean isDeviceSupported(final MidiDevice.Info info) {
        boolean foundDevice = false;
        if (info instanceof CoreMidiDeviceInfo) {
            final CoreMidiDeviceInfo cainfo = (CoreMidiDeviceInfo) info;
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
logger.fine("add CoreMidiSources: " + ep.getStringProperty(CoreMidiLibrary.kMIDIPropertyName));
                props.deviceMap.put(uid, new CoreMidiSource(ep, uid));
            }
        }
logger.fine("devices: " + props.deviceMap.size());
        count = INSTANCE.MIDIGetNumberOfDestinations().intValue();
        for (int dest = 0; dest < count; dest++) {
            NativeLong endpointRef = INSTANCE.MIDIGetDestination(new NativeLong(dest));
            MidiEndpoint ep = new MidiEndpoint(endpointRef);
            Integer uid = ep.getProperty(CoreMidiLibrary.kMIDIPropertyUniqueID);

            if (!props.deviceMap.containsKey(uid)) {
logger.fine("add CoreMidiDestination: " + ep.getStringProperty(CoreMidiLibrary.kMIDIPropertyName));
                props.deviceMap.put(uid, new CoreMidiDestination(ep, uid));
            }
        }
logger.fine("devices: " + props.deviceMap.size());
    }

    private class NotificationReceiver implements MIDINotifyProc {
        @Override
        public void apply(final MIDINotification message, final Pointer refCon) {
            switch (message.getMessageID()) {
            case CoreMidiLibrary.kMIDIMsgObjectAdded:
            case CoreMidiLibrary.kMIDIMsgObjectRemoved:
                props.deviceMap.clear();
                try {
                    buildDeviceMap();
                } catch (final CoreMidiException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }
                break;
            default:
                logger.fine("Got " + message.getMessageID());
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
