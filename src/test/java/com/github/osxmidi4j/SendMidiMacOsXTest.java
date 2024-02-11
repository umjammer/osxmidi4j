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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import vavi.util.Debug;
import vavi.util.StringUtil;

import static com.github.osxmidi4j.CoreMidiDeviceProvider.DEFAULT_DESTINATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SendMidiMacOsXTest {

    static final int MIN_NUM_PORTS = 3; // no name in, out + loop-back

    @Test
    @DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*") // TODO why ga env midi doesn't have in/out???
    void testSendMidi() throws MidiUnavailableException,
            InvalidMidiDataException {
        Info[] midiDeviceInfos = MidiSystem.getMidiDeviceInfo();
        int portCount = 0;
int i = 0;
        for (Info info : midiDeviceInfos) {
Debug.println("info[" + i++ + "]: " + info + ", " + (info instanceof CoreMidiDeviceInfo) + ", " + info.getClass().getName() + " --------");
            if (info instanceof CoreMidiDeviceInfo) {
                MidiDevice midiDevice = MidiSystem.getMidiDevice(info);
Debug.println("device: " + midiDevice + ", " + midiDevice.getMaxTransmitters());
                if (midiDevice.getMaxTransmitters() == 0) {
                    portCount++;
                    assertEquals(CoreMidiDestination.class, midiDevice.getClass());
                    midiDevice.open();
                    Receiver receiver = midiDevice.getReceiver();
                    ShortMessage shortMessage = new ShortMessage();
                    shortMessage.setMessage(ShortMessage.CONTROL_CHANGE, 21, 35);
                    receiver.send(shortMessage, 0);

                    SysexMessage sysexMessage = new SysexMessage();
                    byte[] buf = new byte[] {
                                    (byte) 0xF0, 0x41, 0x10, 0x42, 0x12, 0x40,
                                    0x01, 0x33, 0x02, 0x0D, (byte) 0xF7 };
                    sysexMessage.setMessage(buf, buf.length);
                    receiver.send(sysexMessage, 0);
                    midiDevice.close();
                }
            }
        }
Debug.println("portCount: " + portCount + ", expected " + MIN_NUM_PORTS);
        assertTrue(portCount >= MIN_NUM_PORTS, "actual: " + portCount + ", expected: " + MIN_NUM_PORTS);
    }

    @Test
    void testReceiveMidi() throws MidiUnavailableException,
            InvalidMidiDataException, InterruptedException {

        int portCount = 0;

        ArrayList<MidiMessage> list = new ArrayList<>();

        ShortMessage shortMessage = new ShortMessage();
        shortMessage.setMessage(ShortMessage.CONTROL_CHANGE, 0, 0);
        list.add(shortMessage);

        SysexMessage sysexMessage = new SysexMessage();
        byte[] buf = new byte[] {
                        (byte) 0xF0, 0x41, 0x10, 0x42, 0x12, 0x40, 0x01, 0x33,
                        0x02, 0x0D, (byte) 0xF7 };
        sysexMessage.setMessage(buf, buf.length);
        list.add(sysexMessage);

int i = 0;
        Info[] midiDeviceInfos = MidiSystem.getMidiDeviceInfo();
        for (Info info : midiDeviceInfos) {
Debug.println(" -------- info[" + i++ + "]: " + info + ", CoreMIDI?: " + (info instanceof CoreMidiDeviceInfo) + ", " + info.getClass().getName() + " --------");
            if (info instanceof CoreMidiDeviceInfo) {
                MidiDevice midiDevice = MidiSystem.getMidiDevice(info);
Debug.println("device: " + midiDevice + ", " + midiDevice.getMaxReceivers());
                if (midiDevice.getMaxReceivers() == 0) {
                    portCount++;
                    assertEquals(CoreMidiSource.class, midiDevice.getClass());

                    midiDevice.open();

Debug.println("device name: " + midiDevice.getDeviceInfo().getName());

                    AtomicReference<String> failureMessage = new AtomicReference<>();
                    AtomicInteger arrayIndex = new AtomicInteger();

                    Transmitter transmitter = midiDevice.getTransmitter();
                    transmitter.setReceiver(new Receiver() {
                        @Override
                        public void send(MidiMessage message, long delta) {
                            try {
Debug.println("Received midi message!: " + message);
                                if (message instanceof SysexMessage sysexMessage) {
                                    byte[] d = sysexMessage.getData();
                                    if (!Arrays.equals(d, 0, d.length, buf, 1, buf.length)) {
Debug.println("midi sysex message not by us:\n" + StringUtil.getDump(d));
                                        return;
                                    }
                                }
                                MidiMessage msg = list.get(arrayIndex.getAndIncrement());
                                assertEquals(msg.getClass(), message.getClass());
                                assertEquals(msg.getStatus(), message.getStatus());
                                assertEquals(msg.getLength(), message.getLength());
                            } catch (Exception e) {
                                failureMessage.set(e.getMessage());
Debug.println(e);
                            }
                        }

                        @Override public void close() {}
                    });

                    // default destination doesn't loopback, does it?
                    sendMidiMessagesToPort(DEFAULT_DESTINATION, list);
                    Thread.sleep(1000);
                    midiDevice.close();

Debug.println("received: " + list.size());
                    assertEquals(list.size(), arrayIndex.get());
                    assertNull(failureMessage.get());
                }
            }
        }
Debug.println("portCount: " + portCount);
    }

    /**
     * @param portName i made loopback enabled device named {@link CoreMidiDeviceProvider#DEFAULT_DESTINATION}
     */
    static void sendMidiMessagesToPort(String portName, List<MidiMessage> messages) throws MidiUnavailableException {
Debug.println("Sending messages to port " + portName);
        Info[] midiDeviceInfos = MidiSystem.getMidiDeviceInfo();
        for (Info info : midiDeviceInfos) {
Debug.println(" selected port name: " + portName);
            if (info instanceof CoreMidiDeviceInfo) {
                MidiDevice midiDevice = MidiSystem.getMidiDevice(info);
                if (midiDevice.getMaxReceivers() == 0) {
                    continue;
                }
                if (!midiDevice.getDeviceInfo().getName().contains(portName)) {
Debug.println(" port name is not the same: " + info.getName());
                    continue;
                }

                midiDevice.open();
Debug.println(" device: " + midiDevice);
                Receiver receiver = midiDevice.getReceiver();
Debug.println(" receiver: " + receiver);
                int index = 0;
                for (MidiMessage midiMessage : messages) {
Debug.println(" Sending message " + index++);
                    receiver.send(midiMessage, 0);
                }
                midiDevice.close();
Debug.println(" Sending count " + index);
            }
        }
    }
}
