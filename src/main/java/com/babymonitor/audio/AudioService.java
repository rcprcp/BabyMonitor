package com.babymonitor.audio;

import com.babymonitor.dto.AudioStatusDto;
import com.babymonitor.entity.CameraAudio;
import com.babymonitor.entity.EventType;
import com.babymonitor.service.NotificationService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.logging.Logger;

/**
 * Captures one ALSA device per camera, scores it with YAMNet and raises an
 * {@code Audio} notification when a cry is heard.
 *
 * <p>A camera's capture is a child process whose stdout is raw PCM; the reader
 * thread turns that into analysis windows, drops the quiet ones at the silence
 * gate, and hands the rest to the classifier. A run of confident cry windows --
 * newer than a single window, see {@link CryDetector} -- becomes one
 * {@link EventType#Audio} row through {@link NotificationService}, so the feed,
 * the event stream, the dismissals and the chime all behave as they already do
 * for a device's audio webhook.</p>
 *
 * <p>The process lifecycle is deliberately the same shape as
 * {@link com.babymonitor.motion.MotionService}, so a camera's Start/Stop can
 * drive it later: start begins a capture, stop ends it and joins the reader.
 * Nothing is shared between cameras except the loaded model.</p>
 */
@ApplicationScoped
public class AudioService {

    private static final Logger LOG = Logger.getLogger(AudioService.class);

    @Inject
    AudioConfig config;

    @Inject
    AudioProcessManager processes;

    @Inject
    AudioClassifier classifier;

    @Inject
    NotificationService notifications;

    @Inject
    AudioBroadcaster broadcaster;

    /** What has been seen since a camera's capture started. */
    private final Map<Integer, Counters> counters = new ConcurrentHashMap<>();

    /** The ALSA device each running capture was started with. */
    private final Map<Integer, String> devices = new ConcurrentHashMap<>();

    /** The stdout reader per camera, so a restart can join the old one. */
    private final Map<Integer, Thread> readers = new ConcurrentHashMap<>();

    /** The current state of a camera's capture process and classifier. */
    public AudioStatusDto status(int cameraNumber) {
        return status(cameraNumber, false);
    }

    /**
     * The current state, carrying the caller's view of the camera's desired audio
     * state. The service does not read the {@code camera_audio} row itself, so
     * the HTTP layer and the supervisor each pass the value they already hold.
     */
    public AudioStatusDto status(int cameraNumber, boolean enabled) {
        Long pid = processes.pidOf(cameraNumber);
        boolean running = pid != null;
        Counters seen = counters.get(cameraNumber);
        return new AudioStatusDto(
                cameraNumber,
                enabled,
                running,
                running ? pid : null,
                running ? devices.get(cameraNumber) : null,
                seen == null ? 0L : seen.frames.get(),
                seen == null ? 0.0 : seen.lastRms,
                seen != null && seen.lastLoud,
                seen == null ? null : seen.lastClass,
                seen == null ? 0f : seen.lastConfidence,
                seen == null ? 0L : seen.alerts.get());
    }

    /**
     * Starts capturing a camera's ALSA device, reads its PCM in the background
     * and classifies it. Starting an already-running camera is a no-op.
     *
     * <p>The model is loaded, and a classifier session opened, before the
     * capture process is spawned; a missing or broken model therefore leaves
     * nothing running rather than a process with nothing reading it.</p>
     *
     * @param device the ALSA device, e.g. {@code hw:2,0}
     * @throws UncheckedIOException when the capture process cannot be started
     */
    public AudioStatusDto start(int cameraNumber, String device) {
        if (processes.isRunning(cameraNumber)) {
            LOG.warnf("Audio capture for camera %d is already running", cameraNumber);
            return status(cameraNumber, true);
        }
        AudioClassifier.Session session = config.classifierEnabled() ? classifier.openSession() : null;
        CryDetector detector = session == null ? null : newDetector();
        Process process;
        try {
            process = processes.start(cameraNumber, device);
        } catch (IOException e) {
            closeQuietly(session);
            throw new UncheckedIOException(
                    "Could not start the audio capture for camera " + cameraNumber, e);
        }
        devices.put(cameraNumber, device);
        counters.put(cameraNumber, new Counters());
        Thread reader = new Thread(
                () -> readLoop(cameraNumber, process.getInputStream(), session, detector),
                "audio-capture-" + cameraNumber);
        reader.setDaemon(true);
        readers.put(cameraNumber, reader);
        reader.start();
        return status(cameraNumber, true);
    }

    /** Stops a camera's capture and waits for its reader to finish. */
    public AudioStatusDto stop(int cameraNumber) {
        // End any live-listen stream first, so a listener's HTTP response does
        // not hang open waiting for audio that will never come.
        broadcaster.finish(cameraNumber);
        Thread reader = readers.remove(cameraNumber);
        processes.stop(cameraNumber);
        if (reader != null) {
            try {
                reader.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // The reader owns its classifier session and closes it in its own
        // finally, once the stopped process closes stdout. Nothing is closed
        // here: a thread that would not join must not have its predictor
        // pulled out from under it.
        counters.remove(cameraNumber);
        devices.remove(cameraNumber);
        return status(cameraNumber, false);
    }

    /**
     * Gives the app a cold start for audio: any capture left over from a previous
     * run is killed and every camera's desired audio state is cleared, so audio
     * comes up only when a camera (or its Audio button) starts it.
     */
    void resetOnStartup(@Observes StartupEvent event) {
        if (!config.resetOnStart()) {
            return;
        }
        processes.stopAll();
        try {
            QuarkusTransaction.requiringNew().run(() -> CameraAudio.update("set enabled = false"));
        } catch (RuntimeException e) {
            LOG.warnf("Could not clear the cameras' desired audio state: %s", e.getMessage());
        }
    }

    /** Reads the capture's stdout into windows until the process closes it. */
    private void readLoop(
            int cameraNumber, InputStream in, AudioClassifier.Session session, CryDetector detector) {
        PcmFramer framer = new PcmFramer(config.frameSamples(), config.hopSamples());
        Counters seen = counters.computeIfAbsent(cameraNumber, number -> new Counters());
        byte[] buffer = new byte[8192];
        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                byte[] chunk = Arrays.copyOf(buffer, read);
                // Tee the raw PCM to live listeners before framing it for YAMNet,
                // so hearing the camera and classifying it share one capture.
                broadcaster.publish(cameraNumber, chunk);
                for (AudioFrame frame : framer.accept(chunk)) {
                    try {
                        onFrame(cameraNumber, frame, seen, session, detector);
                    } catch (RuntimeException e) {
                        // One bad window -- a classifier hiccup, a database that
                        // refused the alert -- must not end the capture.
                        LOG.warnf("Camera %d audio window failed: %s", cameraNumber, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            // Stopping the capture closes the pipe; that is a normal exit, and a
            // genuine read error is worth no more than a debug line.
            LOG.debugf("Audio capture for camera %d ended: %s", cameraNumber, e.getMessage());
        } finally {
            closeQuietly(session);
        }
    }

    /** Applies the silence gate, then classifies and possibly alerts. */
    private void onFrame(
            int cameraNumber,
            AudioFrame frame,
            Counters seen,
            AudioClassifier.Session session,
            CryDetector detector) {
        boolean loud = frame.loud(config.silenceRms());
        seen.frames.incrementAndGet();
        seen.lastRms = frame.rms();
        if (loud != seen.lastLoud) {
            seen.lastLoud = loud;
            LOG.infof("Camera %d audio %s (rms=%.4f)",
                    cameraNumber, loud ? "is audible" : "went quiet", frame.rms());
        }
        if (session == null || detector == null || !loud) {
            return;
        }
        List<AudioClassification> ranked = session.classify(frame.samples());
        if (!ranked.isEmpty()) {
            AudioClassification best = ranked.get(0);
            seen.lastClass = best.name();
            seen.lastConfidence = best.confidence();
        }
        Instant now = Instant.now();
        detector.accept(ranked, now).ifPresent(detection -> raise(cameraNumber, detection, now, seen));
    }

    /** Records one audio alert the moment the detector decides it is real. */
    private void raise(int cameraNumber, AudioClassification detection, Instant now, Counters seen) {
        String description = String.format(
                "%s (%.0f%%) heard on camera %d",
                detection.name(), detection.confidence() * 100, cameraNumber);
        notifications.add(EventType.Audio, description, null, now);
        seen.alerts.incrementAndGet();
        LOG.infof("Camera %d audio alert: %s", cameraNumber, description);
    }

    private CryDetector newDetector() {
        return new CryDetector(
                config.alertClassIndices(),
                config.minConfidence(),
                config.windowsToAlert(),
                Duration.ofSeconds(config.cooldownSeconds()));
    }

    private static void closeQuietly(AudioClassifier.Session session) {
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (RuntimeException e) {
            LOG.debugf("Could not close a classifier session: %s", e.getMessage());
        }
    }

    /** Mutable per-camera tallies, written by the reader and read by status(). */
    private static final class Counters {
        private final AtomicLong frames = new AtomicLong();
        private final AtomicLong alerts = new AtomicLong();
        private volatile double lastRms;
        private volatile boolean lastLoud;
        private volatile String lastClass;
        private volatile float lastConfidence;
    }
}
