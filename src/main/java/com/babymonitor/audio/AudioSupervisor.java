package com.babymonitor.audio;

import com.babymonitor.entity.CameraAudio;
import com.babymonitor.entity.EventType;
import com.babymonitor.service.NotificationService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * Keeps the audio capture for cameras that want it running.
 *
 * <p>Every {@code babymonitor.audio.check-interval} (15 seconds by default) it
 * looks at each camera whose persisted state says "audio on" and checks that its
 * capture process is still alive. If it is gone -- {@code sox} crashed, the
 * device was unplugged, the OOM killer struck -- it starts a new capture and
 * records a {@link EventType#System} notification, because a camera that has
 * silently stopped listening is the one failure a baby monitor must never hide.
 * It is the audio twin of {@link com.babymonitor.motion.MotionSupervisor}.</p>
 *
 * <p>Cameras whose state says "audio off" are left completely alone: the Audio
 * Stop button is the user saying "do not supervise this one". The device is the
 * camera's own {@code camera_audio.device} when set, otherwise
 * {@link AudioConfig#device()}.</p>
 *
 * <p>Two limits stop a camera that cannot stay up from thrashing: repeated
 * restarts back off exponentially (15s, 30s, ... capped at 5 minutes) until a
 * check finds the process healthy again, and the "went down" notification is
 * throttled to one per camera per {@value #NOTIFY_COOLDOWN_MINUTES} minutes.
 */
@ApplicationScoped
public class AudioSupervisor {

    private static final Logger LOG = Logger.getLogger(AudioSupervisor.class);

    /** Wait before the first retry of a camera whose capture just went down. */
    static final Duration RETRY_BASE = Duration.ofSeconds(15);

    /** Longest wait between restart attempts, however many have failed. */
    static final Duration RETRY_MAX = Duration.ofMinutes(5);

    /** At most one "audio down" notification per camera in this window. */
    static final Duration NOTIFY_COOLDOWN = Duration.ofMinutes(15);
    static final long NOTIFY_COOLDOWN_MINUTES = 15;

    @Inject
    AudioService audio;

    @Inject
    AudioConfig config;

    @Inject
    NotificationService notifications;

    /** Per-camera retry and notification bookkeeping, keyed by camera number. */
    private final Map<Integer, CaptureWatch> watches = new ConcurrentHashMap<>();

    /** The timer entry point; {@link #checkOnce()} is the testable body. */
    @Scheduled(every = "{babymonitor.audio.check-interval:15s}")
    void scheduledCheck() {
        try {
            checkOnce();
        } catch (RuntimeException e) {
            // A failed check must never stop the timer: the next tick retries.
            LOG.errorf("Audio check failed: %s", e.getMessage());
        }
    }

    /** Runs one pass over every camera that wants audio. */
    public void checkOnce() {
        List<CameraAudio> rows = QuarkusTransaction.requiringNew().call(CameraAudio::listAllOrdered);
        for (CameraAudio row : rows) {
            if (!row.enabled) {
                // The user stopped this camera's audio; forget anything we knew
                // about it so a later start begins with a clean slate.
                watches.remove(row.cameraNumber);
                continue;
            }
            supervise(row);
        }
    }

    /** Checks one camera that wants audio, restarting its capture if it is gone. */
    private void supervise(CameraAudio row) {
        int cameraNumber = row.cameraNumber;
        Instant now = Instant.now();
        CaptureWatch watch = watches.computeIfAbsent(cameraNumber, number -> new CaptureWatch());

        if (audio.status(cameraNumber).running()) {
            watch.onHealthy();
            return;
        }

        // It should be running and it is not: this is the failure the supervisor
        // exists for. Back off if we have been here recently.
        if (!watch.restartDue(now)) {
            return;
        }

        String device = row.deviceOrDefault(config.device());
        String description;
        try {
            audio.start(cameraNumber, device);
            LOG.warnf("Audio for camera %d was not running; restarted it on %s", cameraNumber, device);
            description = "Audio for camera " + cameraNumber
                    + " stopped unexpectedly and was restarted.";
        } catch (RuntimeException e) {
            LOG.errorf("Could not restart audio for camera %d: %s", cameraNumber, e.getMessage());
            description = "Audio for camera " + cameraNumber
                    + " is not running and could not be restarted: " + e.getMessage();
        }
        watch.onRestart(now);

        if (watch.notifyAllowed(now)) {
            watch.onNotified(now);
            notifications.add(EventType.System, description, null, now);
        }
    }

    /**
     * The ALSA device a camera should capture from: its own row value when set,
     * otherwise the configured default.
     */
    public String deviceFor(int cameraNumber) {
        CameraAudio row = CameraAudio.findByCamera(cameraNumber);
        return row == null ? config.device() : row.deviceOrDefault(config.device());
    }

    /** Ensures audio is running for a camera, restarting it if necessary. */
    public boolean ensureRunning(int cameraNumber, String device) {
        if (audio.status(cameraNumber).running()) {
            return false;
        }
        try {
            audio.start(cameraNumber, device);
            return true;
        } catch (RuntimeException e) {
            throw new UncheckedIOException(
                    "Could not start audio capture for camera " + cameraNumber, new IOException(e));
        }
    }

    /** Stops audio capture for a camera. */
    public void stop(int cameraNumber) {
        audio.stop(cameraNumber);
    }

    /**
     * Retry and notification state for one camera.
     *
     * <p>Package-private so the tests can drive the backoff and cooldown
     * decisions directly instead of sleeping through real 15-second ticks.
     */
    static final class CaptureWatch {

        private int failures;
        private Instant nextRestartAt = Instant.EPOCH;
        private Instant lastNotifiedAt = Instant.EPOCH;

        /** Whether a restart may be attempted now, given the current backoff. */
        boolean restartDue(Instant now) {
            return !now.isBefore(nextRestartAt);
        }

        /** The process was found alive: forget the failures and the backoff. */
        void onHealthy() {
            failures = 0;
            nextRestartAt = Instant.EPOCH;
        }

        /** A restart was attempted: widen the backoff before the next one. */
        void onRestart(Instant now) {
            failures++;
            Duration delay = RETRY_BASE.multipliedBy(1L << Math.min(failures - 1, 6));
            if (delay.compareTo(RETRY_MAX) > 0) {
                delay = RETRY_MAX;
            }
            nextRestartAt = now.plus(delay);
        }

        /** Whether the cooldown has passed since the last "audio down" notification. */
        boolean notifyAllowed(Instant now) {
            return !now.isBefore(lastNotifiedAt.plus(NOTIFY_COOLDOWN));
        }

        /** Records that an "audio down" notification was just written. */
        void onNotified(Instant now) {
            lastNotifiedAt = now;
        }
    }
}
