/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.osxmidi4j;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rococoa.Foundation;

import com.github.osxmidi4j.midiservices.CoreMidiLibrary;
import com.github.osxmidi4j.midiservices.MIDIPacket;
import com.github.osxmidi4j.midiservices.MIDIPacketList;
import com.sun.jna.NativeLong;
import com.sun.jna.ptr.NativeLongByReference;

import static com.github.osxmidi4j.midiservices.CoreMidiLibrary.INSTANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


/**
 * CoreMidiSourceMacOsXTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 */
class CoreMidiSourceMacOsXTest {

    private static final Logger logger = System.getLogger(CoreMidiSourceMacOsXTest.class.getName());

    private static final int TIMEOUT = 5;

    /**
     * CoreMidi calls the read proc for as long as the native input port exists, while JNA references
     * callbacks weakly and frees their native trampoline once java stops referencing them. So a source
     * that only passes its read proc to CoreMidi stops receiving at the first gc after being opened.
     */
    @Test
    @DisplayName("an opened source keeps receiving after gc")
    void testReadProcSurvivesGc() throws Exception {
        MidiClient client = CoreMidiDeviceProvider.getMIDIClient();

        // a virtual source, so nothing needs to be plugged in: what we feed it comes back through the read proc
        NativeLongByReference sourceRef = new NativeLongByReference();
        int osStatus = INSTANCE.MIDISourceCreate(client.getMidiClientRef(),
                Foundation.cfString("osxmidi4j test source"), sourceRef);
        assertEquals(0, osStatus, "MIDISourceCreate");
        MidiEndpoint endpoint = new MidiEndpoint(sourceRef.getValue());

        CoreMidiSource device = new CoreMidiSource(endpoint, endpoint.getProperty(CoreMidiLibrary.kMIDIPropertyUniqueID));
        device.open();

        BlockingQueue<MidiMessage> received = new ArrayBlockingQueue<>(16);
        Transmitter transmitter = device.getTransmitter();
        transmitter.setReceiver(new Receiver() {
            @Override public void send(MidiMessage message, long timeStamp) {
                received.offer(message);
            }
            @Override public void close() {
            }
        });

        try {
            send(endpoint, 60);
            assertNotNull(received.poll(TIMEOUT, TimeUnit.SECONDS), "before gc");

            // what happens by itself while notes are played: the source allocates a message per event
            for (int i = 0; i < 3; i++) {
                System.gc();
            }

            send(endpoint, 62);
            assertNotNull(received.poll(TIMEOUT, TimeUnit.SECONDS), "after gc: the read proc has been collected");
logger.log(Level.INFO, "the read proc is still alive after gc");
        } finally {
            device.close();
            INSTANCE.MIDIEndpointDispose(endpoint.endpointRef());
        }
    }

    /** feeds a note on into the virtual source, CoreMidi hands it to the read proc of the ports connected to it */
    private static void send(MidiEndpoint source, int note) throws Exception {
        MIDIPacketList packetList = MIDIPacketList.Factory.newInstance();
        packetList.add(new MIDIPacket(new ShortMessage(ShortMessage.NOTE_ON, 0, note, 100)));
        int osStatus = INSTANCE.MIDIReceived(new NativeLong(source.endpointRef().longValue()), packetList);
        assertEquals(0, osStatus, "MIDIReceived");
    }
}
