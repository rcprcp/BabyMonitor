package com.babymonitor.audio;

/**
 * One analysis window of mono float samples in {@code [-1, 1]}.
 *
 * @param samples the window, {@link AudioConfig#frameSamples()} long; the caller
 *                owns the array and must not assume it is shared afterwards
 * @param rms     root-mean-square of {@code samples}, used by the silence gate
 */
public record AudioFrame(float[] samples, double rms) {

    /** Whether the window is loud enough to be worth classifying. */
    public boolean loud(double silenceRms) {
        return rms >= silenceRms;
    }
}
