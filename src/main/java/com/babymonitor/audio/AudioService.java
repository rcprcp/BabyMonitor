package com.babymonitor.audio;

import com.babymonitor.dto.AudioStatusDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.logging.Logger;

/**
 * Phase one of the audio pipeline: capture one ALSA device per camera and slice
 * it into YAMNet-sized analysis windows, logging what it would hand to the
 * classifier.
 *
 * <p>Nothing is classified and nothing is written to the database yet. The
 * silence gate only decides what to log -- quiet windows go to debug, an
 * audible window (or the return to quiet) is logged at info once per change, so
 * a running capture does not flood the log. When the classifier and the
 * {@code camera_audio} table arrive, this is where they plug in; the process
 * lifecycle is intentionally the same shape as {@link com.babymonitor.motion.MotionService}
 * so the camera's Start/Stop can drive it later.</p>
 */
@ApplicationScoped
public class AudioService {

    private static final Logger LOG = Logger.getLogger(AudioService.class);

    @Inject
    AudioConfig config;

    @Inject
    AudioProcessManager processes;

    /** What has been seen since a camera's capture started. */
    private final Map<Integer, Counters> counters = new ConcurrentHashMap<>();

    /** The ALSA device each running capture was started with. */
    private final Map<Integer, String> devices = new ConcurrentHashMap<>();

    /** The stdout reader per camera, so a restart can join the old one. */
    private final Map<Integer, Thread> readers = new ConcurrentHashMap<>();

    /** The current state of a camera's capture process. */
    public AudioStatusDto status(int cameraNumber) {
        Long pid = processes.pidOf(cameraNumber);
        boolean running = pid != null;
        Counters seen = counters.get(cameraNumber);
        return new AudioStatusDto(
                cameraNumber,
                running,
                running ? pid : null,
                running ? devices.get(cameraNumber) : null,
                seen == null ? 0L : seen.frames.get(),
                seen == null ? 0.0 : seen.lastRms,
                seen != null && seen.lastLoud);
    }

    /**
     * Starts capturing a camera's ALSA device and reads its PCM in the
     * background. Starting an already-running camera is a no-op.
     *
     * @param device the ALSA device, e.g. {@code hw:2,0}
     * @throws UncheckedIOException when the capture process cannot be started
     */
    public AudioStatusDto start(int cameraNumber, String device) {
        if (processes.isRunning(cameraNumber)) {
            LOG.warnf("Audio capture for camera %d is already running", cameraNumber);
            return status(cameraNumber);
        }
        Process process;
        try {
            process = processes.start(cameraNumber, device);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not start the audio capture for camera " + cameraNumber, e);
        }
        devices.put(cameraNumber, device);
        counters.put(cameraNumber, new Counters());
        Thread reader = new Thread(
                () -> readLoop(cameraNumber, process.getInputStream()),
                "audio-capture-" + cameraNumber);
        reader.setDaemon(true);
        readers.put(cameraNumber, reader);
        reader.start();
        return status(cameraNumber);
    }

    /** Stops a camera's capture and waits for its reader to finish. */
    public AudioStatusDto stop(int cameraNumber) {
        Thread reader = readers.remove(cameraNumber);
        processes.stop(cameraNumber);
        if (reader != null) {
            try {
                reader.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        counters.remove(cameraNumber);
        devices.remove(cameraNumber);
        return status(cameraNumber);
    }

    /** Reads the capture's stdout into windows until the process closes it. */
    private void readLoop(int cameraNumber, InputStream in) {
        PcmFramer framer = new PcmFramer(config.frameSamples(), config.hopSamples());
        Counters seen = counters.computeIfAbsent(cameraNumber, number -> new Counters());
        byte[] buffer = new byte[8192];
        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                for (AudioFrame frame : framer.accept(Arrays.copyOf(buffer, read))) {
                    onFrame(cameraNumber, frame, seen);
                }
            }
        } catch (IOException e) {
            // Stopping the capture closes the pipe; that is a normal exit, and a
            // genuine read error is worth no more than a debug line.
            LOG.debugf("Audio capture for camera %d ended: %s", cameraNumber, e.getMessage());
        }
    }

    /** Applies the silence gate and logs the window (phase one does nothing else). */
    private void onFrame(int cameraNumber, AudioFrame frame, Counters seen) {
        boolean loud = frame.loud(config.silenceRms());
        seen.frames.incrementAndGet();
        seen.lastRms = frame.rms();
        if (loud != seen.lastLoud) {
            seen.lastLoud = loud;
            LOG.infof("Camera %d audio %s (rms=%.4f)",
                    cameraNumber, loud ? "is audible" : "went quiet", frame.rms());
        } else {
            LOG.debugf("Camera %d audio window %d: rms=%.4f (%s); would classify",
                    cameraNumber, seen.frames.get(), frame.rms(), loud ? "audible" : "quiet");
        }
    }

    /** Mutable per-camera tallies, written by the reader and read by status(). */
    private static final class Counters {
        private final AtomicLong frames = new AtomicLong();
        private volatile double lastRms;
        private volatile boolean lastLoud;
    }
}
