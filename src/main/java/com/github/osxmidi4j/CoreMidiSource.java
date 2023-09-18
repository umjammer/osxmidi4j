//
// Copyright (c) 2013 All Right Reserved, Pascal Collberg, dqueffeulou and the author of CAProvider
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
import java.util.Iterator;
import java.util.List;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;

import com.github.osxmidi4j.midiservices.CoreMidiLibrary;
import com.github.osxmidi4j.midiservices.MIDIPacket;
import com.github.osxmidi4j.midiservices.MIDIPacketList;
import com.sun.jna.Pointer;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Wrapper of CoreMidi::MidiSource.
 */
public class CoreMidiSource implements MidiDevice {

    private static final int HALF_BYTE = 0x80;
    private static final int BYTE_MAX = 0xFF;

    private static final Logger logger = Logger.getLogger(CoreMidiSource.class.getName());

    private final List<Transmitter> transmitters;
    private boolean sourceOpen = false;

    private final CoreMidiDeviceInfo info;
    private final MidiEndpoint source;
    private MidiInputPort input = null;

    public CoreMidiSource(MidiEndpoint ep, Integer uid, String namePrefix) {
        transmitters = new ArrayList<>();
        source = ep;
        String name = "", vendor = "", description = "", version = "";
        try {
            name = namePrefix
                            + " "
                            + source.getStringProperty(CoreMidiLibrary.kMIDIPropertyName);
        } catch (CoreMidiException e) {
            logger.warning(CoreMidiLibrary.kMIDIPropertyName);
            logger.warning(e.getMessage());
        }
        try {
            version = Integer.toString(source.getProperty(CoreMidiLibrary.kMIDIPropertyDriverVersion));
        } catch (CoreMidiException e) {
            if (e.getErrorCode() == -10835) {
                // Some ports don't have driver versions
                logger.fine(name + " kMIDIPropertyDriverVersion not found");
            } else {
                logger.warning(name + " " + CoreMidiLibrary.kMIDIPropertyDriverVersion);
                logger.fine(e.getMessage());
            }
        }
        try {
            vendor = source.getStringProperty(CoreMidiLibrary.kMIDIPropertyManufacturer);
        } catch (CoreMidiException e) {
            if (e.getErrorCode() == -10835) {
                logger.fine(name + " kMIDIPropertyManufacturer not found");
            } else {
                logger.fine(name + " " + CoreMidiLibrary.kMIDIPropertyManufacturer);
                logger.fine(e.getMessage());
            }
        }
        try {
            // Should I use something else for the description?
            description = source.getStringProperty(CoreMidiLibrary.kMIDIPropertyModel);
        } catch (CoreMidiException e) {
            logger.warning(name + " " + CoreMidiLibrary.kMIDIPropertyModel);
            logger.warning(e.getMessage());
        }
        info = new CoreMidiDeviceInfo(name, vendor, description, version, uid);
    }

    public CoreMidiSource(MidiEndpoint ep, Integer uid) {
        this(ep, uid, CoreMidiDeviceProvider.DEVICE_NAME_PREFIX);
    }

    @Override
    public void close() {
        sourceOpen = false;
        synchronized (transmitters) {
            transmitters.clear();
        }
        if (input != null) {
            try {
                input.disconnectSource(source);
            } catch (CoreMidiException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
    }

    @Override
    public MidiDevice.Info getDeviceInfo() {
        return info;
    }

    @Override
    public int getMaxReceivers() {
        return 0;
    }

    @Override
    public int getMaxTransmitters() {
        return -1;
    }

    // Is this right?
    @Override
    public long getMicrosecondPosition() {
        return -1;
    }

    public boolean isOffline() {
        boolean retVal = false;
        try {
            retVal = source.getProperty(CoreMidiLibrary.kMIDIPropertyOffline) == 1;
        } catch (CoreMidiException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        return retVal;
    }

    @Override
    public Receiver getReceiver() throws MidiUnavailableException {
        throw new MidiUnavailableException("CAMIDISource currently has no Receivers");
    }

    @Override
    public Transmitter getTransmitter() {
        Transmitter t = new Transmitter() {
            private Receiver r = null;

            @Override public void close() {
                // Not needed.
            }

            @Override public Receiver getReceiver() {
                return r;
            }

            @Override public void setReceiver(Receiver r) {
                this.r = r;
            }
        };

        synchronized (transmitters) {
            transmitters.add(t);
        }
        return t;
    }

    @Override
    public boolean isOpen() {
        return sourceOpen;
    }

    @Override
    public void open() {
        try {
            if (!isOffline()) {
                if (input == null) {
                    input = CoreMidiDeviceProvider.getMIDIClient().inputPortCreate(info.getName(), this::readProc);
                }
                input.connectSource(source);
logger.log(Level.FINE, String.format("MidiInputPort %s connected to MidiSource: %s", input, source));
            }
            sourceOpen = true;
        } catch (CoreMidiException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    // From CoreMidiLibrary
    private void findMessages(byte[] data) throws InvalidMidiDataException {
        int status = data[0] & BYTE_MAX;
        int len = data.length;
        if (status == SysexMessage.SYSTEM_EXCLUSIVE || (status & HALF_BYTE) == 0) {
            byte[] d = new byte[len];
            System.arraycopy(data, 0, d, 0, len);

            SysexMessage msg = new SysexMessage();
            if (status == SysexMessage.SYSTEM_EXCLUSIVE) {
                msg.setMessage(d, len);
            } else {
                msg.setMessage(SysexMessage.SPECIAL_SYSTEM_EXCLUSIVE, d, len);
            }

            transmitMessage(msg);
        } else {
            int d1, d2;
            ShortMessage msg = new ShortMessage();
            for (int i = 0; i < len; i++) {
                status = data[i] & BYTE_MAX;
                if ((i + 1 < len) && (data[i + 1] & HALF_BYTE) == 0) {
                    d1 = data[++i] & BYTE_MAX;
                    if ((i + 1 < len) && (data[i + 1] & HALF_BYTE) == 0) {
                        d2 = data[++i] & BYTE_MAX;
                        msg.setMessage(status, d1, d2);
                    } else {
                        msg.setMessage(status, d1, 0);
                    }
                } else {
                    msg.setMessage(status);
                }
                transmitMessage(msg);
            }
        }
    }

    private void transmitMessage(MidiMessage msg) {
        synchronized (transmitters) {
            for (Transmitter t : transmitters) {
                if (t != null) {
                    Receiver r = t.getReceiver();
                    if (r != null) {
                        r.send(msg, -1);
                    }
                }
            }
        }
    }

    public void readProc(MIDIPacketList pktlist, Pointer readProcRefCon, Pointer srcConnRefCon) {
        logger.fine("MIDIPacketList numpackets: " + pktlist.getNumPackets());
        try {
            Iterator<MIDIPacket> iterator = pktlist.iterator();
            while (iterator.hasNext()) {
                MIDIPacket midiPacket = iterator.next();
                if (midiPacket.getData().length > 0) {
                    findMessages(midiPacket.getData());
                } else {
                    logger.warning("0 length message");
                }
            }
        } catch (InvalidMidiDataException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    @Override
    public List<Receiver> getReceivers() {
        return Collections.emptyList();
    }

    @Override
    public List<Transmitter> getTransmitters() {
        synchronized (transmitters) {
            return Collections.unmodifiableList(transmitters);
        }
    }
}
