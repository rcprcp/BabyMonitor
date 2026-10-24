package com.babymonitor.audio;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

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

    /**
     * The ALSA device a camera captures from when it has no per-camera device of
     * its own; a {@code camera_audio.device} row overrides it. The ALSA
     * {@code default} PCM resolves to the box's default input, so a fresh install
     * needs no per-machine configuration.
     */
    @WithDefault("default")
    String device();

    /** Directory holding the per-camera pid and stderr files. */
    @WithDefault("/home/bob/babymonitor/audio")
    String baseDir();

    /**
     * Whether leftover captures are killed and every camera's desired audio state
     * is cleared at startup. Like Motion's cold start this keeps a dev-mode
     * recompile from leaving an orphaned capture that nothing tracks.
     */
    @WithDefault("true")
    boolean resetOnStart();

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

    // The classes worth waking anyone for are no longer a config property: the
    // Audio Notification Config page stores them in
    // babymonitor.audio_alert_classes (see AudioAlertSettings).

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
