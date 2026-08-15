package com.acrp.phone.tools;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Synthesises the phone's interface sounds as WAV files.
 *
 * <p>The sounds are generated rather than sourced so they can be regenerated,
 * tweaked and licensed freely — lifting a real handset's system sounds would put
 * someone else's copyrighted audio in a jar handed to every player.
 *
 * <p>Everything is deliberately short and soft. Interface sounds fire constantly;
 * anything with a long tail or a sharp attack becomes unbearable by the tenth press.
 *
 * <p>Run, then encode to Vorbis (Minecraft only reads .ogg):
 * <pre>
 *   java -cp … com.acrp.phone.tools.SoundGenerator out/
 *   for f in out/*.wav; do ffmpeg -i "$f" -c:a libvorbis -q:a 5 "${f%.wav}.ogg"; done
 * </pre>
 */
public final class SoundGenerator {

    private static final int RATE = 44100;

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "sounds");
        dir.mkdirs();

        write(dir, "unlock", unlock());
        write(dir, "lock", lock());
        write(dir, "key_press", keyPress());
        write(dir, "notification", notification());
        write(dir, "message_sent", messageSent());
        write(dir, "face_ok", faceOk());
        write(dir, "face_fail", faceFail());
        write(dir, "shutter", shutter());
        write(dir, "call_end", callEnd());
        write(dir, "ring", ring());

        System.out.println("wrote WAVs to " + dir.getAbsolutePath());
    }

    // --- the sounds ---------------------------------------------------------

    /** Two rising notes — the phone opening up. */
    private static float[] unlock() {
        float[] buf = new float[samples(0.34f)];
        tone(buf, 0.00f, 0.16f, 659.25f, 0.30f, 0.006f); // E5
        tone(buf, 0.07f, 0.24f, 987.77f, 0.26f, 0.006f); // B5
        return buf;
    }

    /** The same shape inverted: falling, slightly darker. */
    private static float[] lock() {
        float[] buf = new float[samples(0.30f)];
        tone(buf, 0.00f, 0.14f, 587.33f, 0.28f, 0.005f); // D5
        tone(buf, 0.06f, 0.22f, 392.00f, 0.24f, 0.005f); // G4
        return buf;
    }

    /** A keypad tap: a click with barely any pitch to it. */
    private static float[] keyPress() {
        float[] buf = new float[samples(0.06f)];
        noise(buf, 0.0f, 0.012f, 0.10f);
        tone(buf, 0.0f, 0.045f, 1244.51f, 0.12f, 0.002f);
        return buf;
    }

    /** Bell-like, with a fifth above so it carries over ambient noise. */
    private static float[] notification() {
        float[] buf = new float[samples(0.60f)];
        tone(buf, 0.00f, 0.50f, 880.00f, 0.24f, 0.006f);
        tone(buf, 0.00f, 0.44f, 1318.51f, 0.13f, 0.006f);
        tone(buf, 0.10f, 0.40f, 1760.00f, 0.07f, 0.006f);
        return buf;
    }

    /** A short upward blip — the message leaving. */
    private static float[] messageSent() {
        float[] buf = new float[samples(0.22f)];
        sweep(buf, 0.0f, 0.18f, 740f, 1250f, 0.20f);
        return buf;
    }

    /** Bright three-note arpeggio: recognised. */
    private static float[] faceOk() {
        float[] buf = new float[samples(0.36f)];
        tone(buf, 0.00f, 0.14f, 587.33f, 0.22f, 0.004f);
        tone(buf, 0.05f, 0.14f, 739.99f, 0.22f, 0.004f);
        tone(buf, 0.10f, 0.24f, 987.77f, 0.24f, 0.004f);
        return buf;
    }

    /** Low, flat, unresolved — not recognised. */
    private static float[] faceFail() {
        float[] buf = new float[samples(0.34f)];
        tone(buf, 0.00f, 0.13f, 196.00f, 0.26f, 0.004f);
        tone(buf, 0.16f, 0.15f, 174.61f, 0.26f, 0.004f);
        return buf;
    }

    /** Shutter: two mechanical transients, no tone. */
    private static float[] shutter() {
        float[] buf = new float[samples(0.14f)];
        noise(buf, 0.00f, 0.020f, 0.30f);
        noise(buf, 0.055f, 0.030f, 0.22f);
        return buf;
    }

    /** Two descending beeps — the line dropping. */
    private static float[] callEnd() {
        float[] buf = new float[samples(0.42f)];
        tone(buf, 0.00f, 0.13f, 480f, 0.22f, 0.004f);
        tone(buf, 0.17f, 0.18f, 380f, 0.22f, 0.004f);
        return buf;
    }

    /** A two-second ringtone phrase, written to loop cleanly. */
    private static float[] ring() {
        float[] buf = new float[samples(2.0f)];
        float[] melody = {659.25f, 987.77f, 880.00f, 659.25f};
        for (int i = 0; i < melody.length; i++) {
            float at = i * 0.18f;
            tone(buf, at, 0.20f, melody[i], 0.22f, 0.006f);
            tone(buf, at, 0.16f, melody[i] * 2f, 0.06f, 0.006f);
        }
        // Second phrase, then silence so the loop has a natural gap.
        for (int i = 0; i < melody.length; i++) {
            float at = 0.80f + i * 0.18f;
            tone(buf, at, 0.20f, melody[i], 0.20f, 0.006f);
        }
        return buf;
    }

    // --- synthesis ----------------------------------------------------------

    private static int samples(float seconds) {
        return (int) (seconds * RATE);
    }

    /**
     * Adds a sine with a short attack and exponential decay.
     *
     * <p>The attack matters: starting a sine at full amplitude produces a step in
     * the waveform, which is audible as a click on every single play.
     */
    private static void tone(float[] buf, float startSec, float durSec,
                             float freq, float amp, float attackSec) {
        int start = samples(startSec);
        int length = samples(durSec);
        int attack = Math.max(1, samples(attackSec));
        double step = 2 * Math.PI * freq / RATE;

        for (int i = 0; i < length; i++) {
            int at = start + i;
            if (at >= buf.length) {
                break;
            }
            float envelope = (float) Math.exp(-3.2 * i / (double) length);
            if (i < attack) {
                envelope *= i / (float) attack;
            }
            buf[at] += (float) (Math.sin(step * i) * amp * envelope);
        }
    }

    /** Adds a sine gliding from one frequency to another. */
    private static void sweep(float[] buf, float startSec, float durSec,
                              float fromHz, float toHz, float amp) {
        int start = samples(startSec);
        int length = samples(durSec);
        int attack = Math.max(1, samples(0.004f));
        double phase = 0;

        for (int i = 0; i < length; i++) {
            int at = start + i;
            if (at >= buf.length) {
                break;
            }
            float t = i / (float) length;
            double freq = fromHz + (toHz - fromHz) * t;
            phase += 2 * Math.PI * freq / RATE;

            float envelope = (float) Math.exp(-3.0 * t);
            if (i < attack) {
                envelope *= i / (float) attack;
            }
            buf[at] += (float) (Math.sin(phase) * amp * envelope);
        }
    }

    /** Adds a decaying burst of noise — the body of a click. */
    private static void noise(float[] buf, float startSec, float durSec, float amp) {
        int start = samples(startSec);
        int length = samples(durSec);
        Random random = new Random(start * 7919L + length);
        float last = 0f;

        for (int i = 0; i < length; i++) {
            int at = start + i;
            if (at >= buf.length) {
                break;
            }
            float envelope = (float) Math.exp(-9.0 * i / (double) length);
            // One-pole low pass takes the harshness off white noise.
            float white = random.nextFloat() * 2f - 1f;
            last = last * 0.62f + white * 0.38f;
            buf[at] += last * amp * envelope;
        }
    }

    // --- WAV output ---------------------------------------------------------

    private static void write(File dir, String name, float[] samples) throws IOException {
        normalise(samples);
        File file = new File(dir, name + ".wav");
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(wav(samples));
        } finally {
            out.close();
        }
        System.out.printf("  %-14s %5.2fs%n", name + ".wav", samples.length / (float) RATE);
    }

    /** Scales to a consistent headroom so no sound is jarringly louder. */
    private static void normalise(float[] buf) {
        float peak = 0f;
        for (float v : buf) {
            peak = Math.max(peak, Math.abs(v));
        }
        if (peak < 1e-6f) {
            return;
        }
        float gain = 0.82f / peak;
        for (int i = 0; i < buf.length; i++) {
            buf[i] *= gain;
        }
    }

    private static byte[] wav(float[] samples) throws IOException {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream(samples.length * 2);
        for (float sample : samples) {
            int value = Math.round(Math.max(-1f, Math.min(1f, sample)) * 32767f);
            pcm.write(value & 0xFF);
            pcm.write((value >> 8) & 0xFF);
        }
        byte[] data = pcm.toByteArray();

        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 44);
        DataOutputStream d = new DataOutputStream(out);
        d.writeBytes("RIFF");
        writeLE(d, 36 + data.length);
        d.writeBytes("WAVEfmt ");
        writeLE(d, 16);              // PCM header size
        writeLE16(d, 1);             // format: PCM
        writeLE16(d, 1);             // channels: mono
        writeLE(d, RATE);
        writeLE(d, RATE * 2);        // byte rate
        writeLE16(d, 2);             // block align
        writeLE16(d, 16);            // bits per sample
        d.writeBytes("data");
        writeLE(d, data.length);
        d.write(data);
        return out.toByteArray();
    }

    private static void writeLE(DataOutputStream d, int value) throws IOException {
        d.write(value & 0xFF);
        d.write((value >> 8) & 0xFF);
        d.write((value >> 16) & 0xFF);
        d.write((value >> 24) & 0xFF);
    }

    private static void writeLE16(DataOutputStream d, int value) throws IOException {
        d.write(value & 0xFF);
        d.write((value >> 8) & 0xFF);
    }

    private SoundGenerator() {
    }
}
