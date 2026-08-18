/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;

import vavi.sound.midi.MidiUtil.MidiMatcher;
import vavi.util.Debug;
import vavi.util.StringUtil;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;
import vavi.util.properties.annotation.PropsEntity.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static vavi.sound.midi.MidiUtil.getMidiDevice;
import static vavi.sound.midi.MidiUtil.volume;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-05-07 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    static {
        System.setProperty("javax.sound.midi.Sequencer", "#Real Time Sequencer");
//        System.setProperty("javax.sound.midi.Synthesizer", "#VoiceVox MIDI Synthesizer"); // TODO make this pluggable
    }

    @Property
    String receiver;

    @Property
    String midi;

    @Property(name = "in.name")
    String inName;

    @Property(name = "in.vendor")
    String inVendor;

    @Property(name = "in.description")
    String inDescription;

    @Property(name = "out.name")
    String outName;

    @Property(name = "out.vendor")
    String outVendor;

    @Property(name = "out.description")
    String outDescription;

    @Property(name = "vavi.test.volume.midi")
    float midiVolume = 0.2f;

    static boolean onIde = System.getProperty("vavi.test", "").equals("ide");
    static long time = onIde ? 1000 * 1000 : 10 * 1000;

    @BeforeEach
    void setupEach() throws IOException {
        if (localPropertiesExists()) {
            Util.bind(this);
        }

        outName = outName != null ? (outName.isEmpty() ? null : outName) : null;
        outVendor = outVendor != null ? (outVendor.isEmpty() ? null : outVendor) : null;
        outName = outDescription != null ? (outDescription.isEmpty() ? null : outDescription) : null;

        System.setProperty("javax.sound.midi.Receiver", receiver);

Debug.println("volume: " + midiVolume);
    }

    /** opens the midi keyboard which is specified by local.properties */
    MidiDevice openInputDevice() throws Exception {
        Info info = getMidiDevice(new MidiMatcher(inName, inVendor, inDescription, null), true);
        MidiDevice device =  MidiSystem.getMidiDevice(info);
Debug.println("---- " + info +" (" + device.getClass().getName() + ")" + " ----");
Debug.println("name      : " + info.getName());
Debug.println("vendor    : " + info.getVendor());
Debug.println("descriptor: " + info.getDescription());
Debug.println("version   : " + info.getVersion());
        device.open();
        return device;
    }

    @Test
    @DisplayName("my receiver in the middle")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test2() throws Exception {

        MidiDevice device = openInputDevice();

//        Info outInfo = getMidiDevice(new MidiMatcher(outName, outVendor, outDescription, null), true);
//        MidiDevice outDevice =  MidiSystem.getMidiDevice(outInfo);
//Debug.println("---- " + outInfo +" (" + outDevice.getClass().getName() + ")" + " ----");
//Debug.println("name      : " + outInfo.getName());
//Debug.println("vendor    : " + outInfo.getVendor());
//Debug.println("descriptor: " + outInfo.getDescription());
//Debug.println("version   : " + outInfo.getVersion());
//        outDevice.open();

        // Now, display strings from synthInfos list in GUI.

        Receiver receiver = MidiSystem.getReceiver();
Debug.println("receiver: " + receiver);
        Transmitter transmitter = device.getTransmitter();

        transmitter.setReceiver(new SimpleReceiver(receiver));

        CountDownLatch cdl = new CountDownLatch(1);
Debug.println("waiting...");
        cdl.await();
Debug.println("done");

        device.close();
    }

    /** print received message */
    static class SimpleReceiver implements Receiver {
        Receiver receiver;
        SimpleReceiver(Receiver receiver) {
            this.receiver = receiver;
        }
        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (message instanceof ShortMessage shortMessage) {
                int channel = shortMessage.getChannel();
                int command = shortMessage.getCommand();
                int data1 = shortMessage.getData1();
                int data2 = shortMessage.getData2();
Debug.printf("short: command: %02x, channel: %d, data1: %d, data2: %d", command, channel, data1, data2);
                switch (command) {
                    case ShortMessage.NOTE_OFF:
                        break;
                    case ShortMessage.NOTE_ON:
                        break;
                    case ShortMessage.POLY_PRESSURE:
                        break;
                    case ShortMessage.CONTROL_CHANGE:
                        break;
                    case ShortMessage.PROGRAM_CHANGE:
                        break;
                    case ShortMessage.CHANNEL_PRESSURE:
                        break;
                    case ShortMessage.PITCH_BEND:
                        break;
                }
            } else if (message instanceof SysexMessage sysexMessage) {
                byte[] data = sysexMessage.getData();
Debug.println("sysex: %02X\n%s".formatted(sysexMessage.getStatus(), StringUtil.getDump(data, 32)));
            } else if (message instanceof MetaMessage metaMessage) {
Debug.println("meta: %02x".formatted(metaMessage.getType()));
            } else {
                assert false;
            }

            receiver.send(message, timeStamp);
        }

        @Override
        public void close() {
        }
    }

    /** sounds each played note together with the notes at the given intervals */
    static class Harmonizer implements Receiver {
        Receiver receiver;
        /** semitones to stack onto each played note, e.g. 4, 7 makes a major triad */
        int[] intervals;
        Harmonizer(Receiver receiver, int... intervals) {
            this.receiver = receiver;
            this.intervals = intervals;
        }
        @Override
        public void send(MidiMessage message, long timeStamp) {
            receiver.send(message, timeStamp);

            if (message instanceof ShortMessage shortMessage) {
                int command = shortMessage.getCommand();
                if (command != ShortMessage.NOTE_ON && command != ShortMessage.NOTE_OFF) {
                    return; // control change, pitch bend, etc. are passed through as they are
                }
                for (int interval : intervals) {
                    int note = shortMessage.getData1() + interval;
                    if (note > 127) {
                        continue;
                    }
                    try {
                        // the harmony follows the key: a note off comes back for each note on
                        receiver.send(new ShortMessage(command, shortMessage.getChannel(), note, shortMessage.getData2()), timeStamp);
                    } catch (InvalidMidiDataException e) {
Debug.printStackTrace(e);
                    }
                }
            }
        }

        @Override
        public void close() {
        }
    }

    /** sounds the notes you hold one after another, lowest to highest, wrapping around */
    static class Arpeggiator implements Receiver {
        Receiver receiver;
        /** the notes which are held now, sorted by pitch. the keyboard thread adds, the step thread reads */
        NavigableSet<Integer> notes = new ConcurrentSkipListSet<>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "arpeggiator");
            thread.setDaemon(true);
            return thread;
        });
        int channel;
        int velocity = 100;
        /** the note which is sounding now, -1 when silent */
        int sounding = -1;

        /** @param step milliseconds per note, e.g. 125 is 16th notes at 120 bpm */
        Arpeggiator(Receiver receiver, int step) {
            this.receiver = receiver;
            scheduler.scheduleAtFixedRate(this::step, step, step, TimeUnit.MILLISECONDS);
        }

        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (message instanceof ShortMessage shortMessage) {
                int command = shortMessage.getCommand();
                int note = shortMessage.getData1();
                int velocity = shortMessage.getData2();
                if (command == ShortMessage.NOTE_ON && velocity > 0) {
                    channel = shortMessage.getChannel();
                    this.velocity = velocity;
                    notes.add(note);
                    return; // the keys themselves are not sounded, step() sounds them one by one
                } else if (command == ShortMessage.NOTE_OFF || command == ShortMessage.NOTE_ON) {
                    notes.remove(note); // a note on w/ velocity 0 means a note off
                    return;
                }
            }

            receiver.send(message, timeStamp);
        }

        /** sounds the next held note, silencing the previous one */
        void step() {
            try {
                if (sounding != -1) {
                    receiver.send(new ShortMessage(ShortMessage.NOTE_OFF, channel, sounding, 0), -1);
                }
                if (notes.isEmpty()) {
                    sounding = -1; // the round starts from the lowest note again
                    return;
                }
                // higher() works on the pitch, so releasing the sounding key doesn't break the round
                Integer next = sounding == -1 ? notes.first() : notes.higher(sounding);
                sounding = next != null ? next : notes.first();
                receiver.send(new ShortMessage(ShortMessage.NOTE_ON, channel, sounding, velocity), -1);
            } catch (Exception e) {
                // the scheduler silently stops stepping when this escapes
Debug.printStackTrace(e);
            }
        }

        @Override
        public void close() {
            scheduler.shutdownNow();
        }
    }

    @Test
    @DisplayName("harmonizer")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test3() throws Exception {

        MidiDevice device = openInputDevice();

        Receiver receiver = MidiSystem.getReceiver();
Debug.println("receiver: " + receiver);
        Transmitter transmitter = device.getTransmitter();

        transmitter.setReceiver(new Harmonizer(receiver, 4, 7)); // each key sounds as a major triad
        volume(receiver, midiVolume);

        CountDownLatch cdl = new CountDownLatch(1);
Debug.println("play the keyboard...");
        cdl.await();
Debug.println("done");

        device.close();
    }

    @Test
    @DisplayName("arpeggiator")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test4() throws Exception {

        MidiDevice device = openInputDevice();

        Receiver receiver = MidiSystem.getReceiver();
Debug.println("receiver: " + receiver);
        Transmitter transmitter = device.getTransmitter();

        transmitter.setReceiver(new Arpeggiator(receiver, 125)); // 16th notes at 120 bpm
        volume(receiver, midiVolume);

        CountDownLatch cdl = new CountDownLatch(1);
Debug.println("hold a chord...");
        cdl.await();
Debug.println("done");

        device.close();
    }

    @Test
    @DisplayName("connect outer device")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test() throws Exception {

        Receiver receiver = MidiSystem.getReceiver();
Debug.println("receiver: " + receiver);

        Sequencer sequencer = MidiSystem.getSequencer(false);
        sequencer.getTransmitter().setReceiver(receiver);
        sequencer.open();
Debug.println("sequencer: " + sequencer);

        Path file = Paths.get(midi);
Debug.println("file: " + file);

        Sequence seq = MidiSystem.getSequence(new BufferedInputStream(Files.newInputStream(file)));

        CountDownLatch cdl = new CountDownLatch(1);
        MetaEventListener mel = meta -> {
Debug.println("META: " + meta.getType());
            if (meta.getType() == 47) cdl.countDown();
        };
        sequencer.setSequence(seq);
        sequencer.addMetaEventListener(mel);
Debug.println("START");
        sequencer.start();
        volume(receiver, midiVolume);
if (!onIde) {
 Thread.sleep(time);
 sequencer.stop();
Debug.println("STOP");
} else {
        cdl.await();
}
Debug.println("END");
        sequencer.removeMetaEventListener(mel);
        sequencer.close();

        receiver.close();
    }
}
