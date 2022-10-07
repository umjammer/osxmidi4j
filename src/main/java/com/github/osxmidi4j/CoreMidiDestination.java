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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

import com.github.osxmidi4j.midiservices.CoreMidiLibrary;
import java.util.logging.Logger;



/**
 * Wrapper of CoreMidi::MidiDestination.
 */
public class CoreMidiDestination implements MidiDevice {

    private static final Logger logger = Logger.getLogger(CoreMidiDestination.class.getName());
    private boolean destOpen = false;

    private final CoreMidiDeviceInfo info;
    private final MidiEndpoint dest;
    private final Set<CoreMidiReceiver> receivers;

    public CoreMidiDestination(final MidiEndpoint ep, final Integer uid, final String namePrefix) {
        dest = ep;
        String name = "", vendor = "", description = "", version = "";
        try {
            name = namePrefix
                            + " "
                            + dest.getStringProperty(CoreMidiLibrary.kMIDIPropertyName);
        } catch (final CoreMidiException e) {
            logger.warning(CoreMidiLibrary.kMIDIPropertyName);
            logger.warning(e.getMessage());
        }
        try {
            version = Integer.toString(dest.getProperty(CoreMidiLibrary.kMIDIPropertyDriverVersion));
        } catch (final CoreMidiException e) {
            if (e.getErrorCode() == -10835) {
                // Some ports don't have driver versions
                logger.fine(name + " kMIDIPropertyDriverVersion not found");
            } else {
                logger.warning(name + " " + CoreMidiLibrary.kMIDIPropertyDriverVersion);
                logger.warning(e.getMessage());
            }
        }
        try {
            vendor = dest.getStringProperty(CoreMidiLibrary.kMIDIPropertyManufacturer);
        } catch (final CoreMidiException e) {
            if (e.getErrorCode() == -10835) {
                logger.fine(name + " kMIDIPropertyManufacturer not found");
            } else {
                logger.warning(name + " " + CoreMidiLibrary.kMIDIPropertyManufacturer);
                logger.warning(e.getMessage());
            }
        }
        try {
            // Should I use something else for the description?
            description = dest.getStringProperty(CoreMidiLibrary.kMIDIPropertyModel);
        } catch (final CoreMidiException e) {
            if (e.getErrorCode() == -10835) {
                logger.fine(name + " kMIDIPropertyModel not found");
            } else {
                logger.warning(name + " " + CoreMidiLibrary.kMIDIPropertyModel);
                logger.warning(e.getMessage());
            }
        }
        info = new CoreMidiDeviceInfo(name, vendor, description, version, uid);
        receivers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public CoreMidiDestination(final MidiEndpoint ep, final Integer uid) {
        this(ep, uid, CoreMidiDeviceProvider.DEVICE_NAME_PREFIX);
    }

    public void close() {
        destOpen = false;
        for (Receiver receiver : getReceivers()) {
            receiver.close();
        }
    }

    public MidiDevice.Info getDeviceInfo() {
        return info;
    }

    public int getMaxTransmitters() {
        return 0;
    }

    public int getMaxReceivers() {
        return -1;
    }

    // Is this right?
    public long getMicrosecondPosition() {
        // Maybe
        // 1000*HostTime.convertHostTimeToNanos(HostTime.getCurrentHostTime())
        return -1;
    }

    public Transmitter getTransmitter() throws MidiUnavailableException {
        throw new MidiUnavailableException("CAMIDIDestination currently has no Transmitters");
    }

    public Receiver getReceiver() {
        CoreMidiReceiver receiver =  new CoreMidiReceiver(dest);
        receivers.add(receiver);
        return receiver;
    }

    public boolean isOpen() {
        return destOpen;
    }

    public void open() {
        destOpen = true;
    }

    @Override
    public List<Receiver> getReceivers() {
        return Collections.unmodifiableList(new ArrayList<Receiver>(receivers));
    }

    @Override
    public List<Transmitter> getTransmitters() {
        return Collections.emptyList();
    }
}
