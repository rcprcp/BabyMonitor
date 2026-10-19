package com.babymonitor.audio;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Set;

/**
 * Configuration of the per-camera audio capture and classification pipeline.
 *
 * <p>A child process reads one ALSA device and streams raw little-endian 16-bit
 * mono PCM, which {@link PcmFramer} slices into YAMNet's analysis windows.
 * Audible windows are scored by {@link YamNetClassifier}, and {@link CryDetector}
 * turns a run of those scores into at most one {@code Audio} notification per
 * cooldown.</p>
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

    /**
     * Samples in one analysis window (~0.975 s at 16 kHz). When
     * {@link #classifierEnabled()} this <em>must</em> be the length YAMNet was
     * built for -- 15600 -- because a window of any other size cannot be fed to
     * the model at all.
     */
    @WithDefault("15600")
    int frameSamples();

    /** Samples between the starts of two windows (~0.48 s at 16 kHz). */
    @WithDefault("7680")
    int hopSamples();

    /** Windows quieter than this root-mean-square are treated as silence. */
    @WithDefault("0.005")
    double silenceRms();

    /**
     * Whether captured windows are classified at all. Quiet windows and this
     * switch are the two ways the dry-run phase one behaviour can be restored
     * without changing any other setting.
     */
    @WithDefault("true")
    boolean classifierEnabled();

    /** Classpath resource holding the YAMNet TFLite model. */
    @WithDefault("/yamnet/yamnet.tflite")
    String modelResource();

    /** Classpath resource holding YAMNet's index-to-name class map. */
    @WithDefault("/yamnet/yamnet_class_map.csv")
    String labelsResource();

    /**
     * The class indices worth alerting on: {@code 19} (Crying, sobbing) and
     * {@code 20} (Baby cry, infant cry) out of the box. Neighbours are left in
     * the model's output but out of the alert list -- {@code 21} is Whimper,
     * {@code 11} Screaming, {@code 13} Laughter -- so widening or narrowing it
     * is a configuration change rather than a code change.
     */
    @WithDefault("19,20")
    Set<Integer> alertClassIndices();

    /** Lowest YAMNet confidence in an alert class that counts as a detection. */
    @WithDefault("0.35")
    double minConfidence();

    /** Consecutive detections needed before an alert fires. */
    @WithDefault("2")
    int windowsToAlert();

    /** Quiet time after an alert before the same camera may alert again. */
    @WithDefault("60")
    long cooldownSeconds();
}
