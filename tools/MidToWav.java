import com.sun.media.sound.AudioSynthesizer;

import javax.sound.midi.*;
import javax.sound.sampled.*;
import java.io.*;
import java.util.*;

public class MidToWav {
    private static final float SAMPLE_RATE = 8000f;
    private static final int TAIL_MICROS = 0;
    private static final int FADE_MICROS = 3_000;
    private static final AudioFormat RENDER_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE,
            16,
            1,
            2,
            SAMPLE_RATE,
            false
    );
    private static final AudioFormat WAV_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_UNSIGNED,
            SAMPLE_RATE,
            8,
            1,
            1,
            SAMPLE_RATE,
            false
    );

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java MidToWav input.mid output.wav");
            System.exit(2);
        }

        render(new File(args[0]), new File(args[1]));
    }

    public static void render(File midiFile, File wavFile) throws Exception {
        Sequence seq = MidiSystem.getSequence(midiFile);

        AudioSynthesizer synth = findAudioSynthesizer();
        if (synth == null) {
            throw new IllegalStateException("No AudioSynthesizer found.");
        }

        AudioInputStream synthStream = null;
        Receiver receiver = null;
        try {
            synthStream = synth.openStream(RENDER_FORMAT, null);
            receiver = synth.getReceiver();

            long durationMicros = sendMidiEvents(seq, receiver);
            long frames = (long) Math.ceil(
                    (durationMicros + TAIL_MICROS) * (SAMPLE_RATE / 1_000_000.0)
            );

            AudioInputStream limitedStream =
                    new AudioInputStream(synthStream, RENDER_FORMAT, frames);
            byte[] rendered = readFully(limitedStream);
            byte[] wavData = toUnsigned8Pcm(rendered);
            fadeEdges(wavData);

            AudioInputStream wavStream = new AudioInputStream(
                    new ByteArrayInputStream(wavData),
                    WAV_FORMAT,
                    wavData.length
            );
            try {
                AudioSystem.write(wavStream, AudioFileFormat.Type.WAVE, wavFile);
            } finally {
                wavStream.close();
            }
        } finally {
            if (receiver != null) {
                receiver.close();
            }
            if (synthStream != null) {
                synthStream.close();
            }
            synth.close();
        }
    }

    private static AudioSynthesizer findAudioSynthesizer() throws Exception {
        Synthesizer s = MidiSystem.getSynthesizer();
        if (s instanceof AudioSynthesizer) {
            return (AudioSynthesizer) s;
        }

        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            MidiDevice dev = MidiSystem.getMidiDevice(info);
            if (dev instanceof AudioSynthesizer) {
                return (AudioSynthesizer) dev;
            }
        }

        return null;
    }

    private static byte[] readFully(AudioInputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];

        while (true) {
            int read = stream.read(buffer);
            if (read < 0) {
                break;
            }
            out.write(buffer, 0, read);
        }

        return out.toByteArray();
    }

    private static byte[] toUnsigned8Pcm(byte[] signed16Pcm) {
        byte[] unsigned8Pcm = new byte[signed16Pcm.length / 2];

        for (int in = 0, out = 0; out < unsigned8Pcm.length; in += 2, out++) {
            int sample = (short) (((signed16Pcm[in + 1] & 0xff) << 8) |
                    (signed16Pcm[in] & 0xff));
            unsigned8Pcm[out] = (byte) (128 + (sample >> 8));
        }

        return unsigned8Pcm;
    }

    private static void fadeEdges(byte[] audio) {
        int fadeFrames = Math.round(SAMPLE_RATE * FADE_MICROS / 1_000_000f);
        fadeFrames = Math.min(fadeFrames, audio.length / 2);

        for (int i = 0; i < fadeFrames; i++) {
            audio[i] = scaleFromSilence(audio[i], i, fadeFrames);
        }

        for (int i = 0; i < fadeFrames; i++) {
            int index = audio.length - 1 - i;
            audio[index] = scaleFromSilence(audio[index], i, fadeFrames);
        }
    }

    private static byte scaleFromSilence(byte sample, int level, int maxLevel) {
        int centered = (sample & 0xff) - 128;
        return (byte) (128 + centered * level / maxLevel);
    }

    private static long sendMidiEvents(Sequence seq, Receiver receiver) {
        List<MidiEvent> events = new ArrayList<>();

        for (Track track : seq.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                events.add(track.get(i));
            }
        }

        events.sort(Comparator.comparingLong(MidiEvent::getTick));

        double micros = 0.0;
        long lastTick = 0;

        int tempoMPQ = 500_000;
        float divisionType = seq.getDivisionType();
        int resolution = seq.getResolution();

        for (MidiEvent event : events) {
            long tick = event.getTick();
            long deltaTicks = tick - lastTick;

            if (divisionType == Sequence.PPQ) {
                micros += deltaTicks * (tempoMPQ / (double) resolution);
            } else {
                micros += deltaTicks * (1_000_000.0 / (divisionType * resolution));
            }

            lastTick = tick;

            MidiMessage msg = event.getMessage();

            if (msg instanceof MetaMessage) {
                MetaMessage meta = (MetaMessage) msg;

                // 0x51 = Set Tempo
                if (meta.getType() == 0x51) {
                    byte[] d = meta.getData();
                    if (d.length == 3) {
                        tempoMPQ =
                                ((d[0] & 0xff) << 16) |
                                ((d[1] & 0xff) << 8) |
                                (d[2] & 0xff);
                    }
                }
            } else {
                receiver.send(msg, Math.round(micros));
            }
        }

        return Math.round(micros);
    }
}
