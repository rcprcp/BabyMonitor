package com.babymonitor.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Slices a stream of raw little-endian signed 16-bit mono PCM into overlapping
 * analysis windows.
 *
 * <p>The capture tool emits an unbounded byte stream, so whatever has not yet
 * completed a window is kept between calls. A window is emitted as soon as
 * {@code frameSamples} are available, after which the read head advances by
 * {@code hopSamples} -- with a hop shorter than the window, consecutive windows
 * overlap, which is the framing YAMNet's front end expects.</p>
 *
 * <p>A chunk may end on an odd byte boundary and a 16-bit sample may be split
 * across two reads; that trailing byte is carried over rather than dropped, so
 * the decoded stream stays sample-aligned. This class is deliberately free of
 * CDI and I/O so the framing maths can be unit-tested on its own.</p>
 */
public final class PcmFramer {

    private final int frameSamples;
    private final int hopSamples;

    /** Decoded samples that have not yet completed a window. */
    private float[] pending = new float[0];

    /** A trailing odd byte from the previous chunk, or -1 when there is none. */
    private int carriedByte = -1;

    public PcmFramer(int frameSamples, int hopSamples) {
        if (frameSamples < 1) {
            throw new IllegalArgumentException("frameSamples must be positive");
        }
        if (hopSamples < 1 || hopSamples > frameSamples) {
            throw new IllegalArgumentException("hopSamples must be between 1 and frameSamples");
        }
        this.frameSamples = frameSamples;
        this.hopSamples = hopSamples;
    }

    /**
     * Feeds one chunk of PCM and returns every window it completes.
     *
     * @param chunk any number of s16le bytes; may be empty
     * @return the completed windows, oldest first; empty when the chunk did not
     *         complete one
     */
    public List<AudioFrame> accept(byte[] chunk) {
        float[] decoded = decode(chunk);
        float[] all = new float[pending.length + decoded.length];
        System.arraycopy(pending, 0, all, 0, pending.length);
        System.arraycopy(decoded, 0, all, pending.length, decoded.length);

        List<AudioFrame> frames = new ArrayList<>();
        int offset = 0;
        while (all.length - offset >= frameSamples) {
            float[] window = new float[frameSamples];
            System.arraycopy(all, offset, window, 0, frameSamples);
            frames.add(new AudioFrame(window, rms(window)));
            offset += hopSamples;
        }
        pending = new float[all.length - offset];
        System.arraycopy(all, offset, pending, 0, pending.length);
        return frames;
    }

    /** Decodes s16le bytes (plus any carried odd byte) into float samples. */
    private float[] decode(byte[] chunk) {
        byte[] data = chunk;
        if (carriedByte >= 0 && chunk.length > 0) {
            data = new byte[chunk.length + 1];
            data[0] = (byte) carriedByte;
            System.arraycopy(chunk, 0, data, 1, chunk.length);
            carriedByte = -1;
        }
        int usable = data.length - (data.length % 2);
        if (usable < data.length) {
            carriedByte = data[data.length - 1] & 0xFF;
        }
        float[] samples = new float[usable / 2];
        for (int i = 0; i < samples.length; i++) {
            int value = (short) (((data[2 * i + 1] & 0xFF) << 8) | (data[2 * i] & 0xFF));
            samples[i] = value / 32768f;
        }
        return samples;
    }

    private static double rms(float[] samples) {
        if (samples.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (float sample : samples) {
            sum += (double) sample * sample;
        }
        return Math.sqrt(sum / samples.length);
    }
}
