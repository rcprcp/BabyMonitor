package com.babymonitor.audio;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of the per-camera audio capture pipeline.
 *
 * <p>This is phase one of the SoX + YAMNet work: a child process reads one ALSA
 * device and streams raw little-endian 16-bit mono PCM, which {@link PcmFramer}
 * slices into YAMNet's analysis windows. Nothing is classified and nothing is
 * persisted yet -- {@link AudioService} only logs which windows it would hand on,
 * after the silence gate.</p>
 *
 * <p>Like Motion's settings, every value is driven from here rather than from
 * systemd, and tests repoint {@link #binary()} at a stub so nothing opens a real
 * microphone.</p>
 */
@ConfigMapping(prefix = "babymonitor.audio")
public interface AudioConfig {

    /** The capture executable: SoX, or ffmpeg (which ships with the OS here). */
    @WithDefault("sox")
    String binary();

    /** Directory holding the per-camera pid and stderr files. */
    @WithDefault("/home/bob/babymonitor/audio")
    String baseDir();

    /** Sample rate the capture process is asked to produce; YAMNet needs 16 kHz. */
    @WithDefault("16000")
    int sampleRate();

    /** Samples in one analysis window (~0.975 s at 16 kHz). */
    @WithDefault("15600")
    int frameSamples();

    /** Samples between the starts of two windows (~0.48 s at 16 kHz). */
    @WithDefault("7680")
    int hopSamples();

    /** Windows quieter than this root-mean-square are treated as silence. */
    @WithDefault("0.005")
    double silenceRms();
}
