package com.babymonitor.motion;

import com.babymonitor.entity.Camera;
import com.babymonitor.entity.EventType;
import com.babymonitor.service.NotificationService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * Keeps the cameras that are supposed to be running actually running.
 *
 * <p>Every {@code babymonitor.motion.check-interval} (15 seconds by default) it
 * looks at each camera whose persisted state says "running" and checks that its
 * {@code motion} process is still alive. If it is gone -- the camera was
 * unplugged, the process crashed, the OOM killer struck -- the supervisor
 * rebuilds the config, starts a new process and records a {@link EventType#System}
 * notification, because a camera that has silently stopped is the one failure a
 * baby monitor must never hide.
 *
 * <p>Cameras whose state says "not running" are left completely alone: the Stop
 * button is the user saying "do not supervise this one".
 *
 * <p>Two limits stop a camera that cannot stay up from thrashing: repeated
 * restarts back off exponentially (15s, 30s, ... capped at 5 minutes) until a
 * check finds the process healthy again, and the "went down" notification is
 * throttled to one per camera per {@value #NOTIFY_COOLDOWN_MINUTES} minutes.
 */
@ApplicationScoped
public class MotionSupervisor {

    private static final Logger LOG = Logger.getLogger(MotionSupervisor.class);

    /** Wait before the first retry of a camera that just went down. */
    static final Duration RETRY_BASE = Duration.ofSeconds(15);

    /** Longest wait between restart attempts, however many have failed. */
    static final Duration RETRY_MAX = Duration.ofMinutes(5);

    /** At most one "camera down" notification per camera in this window. */
    static final Duration NOTIFY_COOLDOWN = Duration.ofMinutes(15);
    static final long NOTIFY_COOLDOWN_MINUTES = 15;

    @Inject
    MotionService motion;

    @Inject
    NotificationService notifications;

    /** Per-camera retry and notification bookkeeping, keyed by camera number. */
    private final Map<Integer, CameraWatch> watches = new ConcurrentHashMap<>();

    /** The timer entry point; {@link #checkOnce()} is the testable body. */
    @Scheduled(every = "{babymonitor.motion.check-interval:15s}")
    void scheduledCheck() {
        try {
            checkOnce();
        } catch (RuntimeException e) {
            // A failed check must never stop the timer: the next tick retries.
            LOG.errorf("Motion check failed: %s", e.getMessage());
        }
    }

    /** Runs one pass over every camera. */
    public void checkOnce() {
        List<Camera> rows = readCameras();
        for (Map.Entry<Integer, List<Camera>> camera : groupByCamera(rows).entrySet()) {
            int cameraNumber = camera.getKey();
            List<Camera> parameters = camera.getValue();
            if (!shouldBeRunning(parameters)) {
                // The user stopped this camera; forget anything we knew about it
                // so a later start begins with a clean slate.
                watches.remove(cameraNumber);
                continue;
            }
            supervise(cameraNumber, parameters);
        }
    }

    /** Checks one camera that should be running, restarting its process if it is gone. */
    private void supervise(int cameraNumber, List<Camera> parameters) {
        Instant now = Instant.now();
        CameraWatch watch = watches.computeIfAbsent(cameraNumber, number -> new CameraWatch());

        if (motion.isRunning(cameraNumber)) {
            watch.onHealthy();
            return;
        }

        // It is supposed to be running and it is not: this is the failure the
        // supervisor exists for. Back off if we have been here recently.
        if (!watch.restartDue(now)) {
            return;
        }

        String cameraName = cameraName(parameters);
        String description;
        try {
            motion.ensureRunning(cameraNumber, MotionService.toParameters(parameters));
            LOG.warnf("Motion for camera %d was not running; restarted it", cameraNumber);
            description = "Motion for camera " + cameraNumber + cameraName
                    + " stopped unexpectedly and was restarted.";
        } catch (RuntimeException e) {
            LOG.errorf("Could not restart Motion for camera %d: %s", cameraNumber, e.getMessage());
            description = "Motion for camera " + cameraNumber + cameraName
                    + " is not running and could not be restarted: " + e.getMessage();
        }
        watch.onRestart(now);

        if (watch.notifyAllowed(now)) {
            watch.onNotified(now);
            notifications.add(EventType.System, description, null, now);
        }
    }

    private static List<Camera> readCameras() {
        return QuarkusTransaction.requiringNew().call(Camera::listAllOrdered);
    }

    /** Rolls the flat parameter rows up into one list per camera, in camera order. */
    private static Map<Integer, List<Camera>> groupByCamera(List<Camera> rows) {
        Map<Integer, List<Camera>> byCamera = new TreeMap<>();
        for (Camera row : rows) {
            byCamera.computeIfAbsent(row.cameraNumber, number -> new ArrayList<>()).add(row);
        }
        return byCamera;
    }

    /** The camera's persisted desired state; every row of a camera carries the same value. */
    private static boolean shouldBeRunning(List<Camera> parameters) {
        return !parameters.isEmpty() && parameters.get(0).enabled;
    }

    /** The camera's display name, wrapped for a sentence, or an empty string. */
    private static String cameraName(List<Camera> parameters) {
        return parameters.stream()
                .filter(row -> "camera_name".equals(row.paramKey))
                .map(row -> " (" + row.value + ")")
                .findFirst()
                .orElse("");
    }

    /**
     * Retry and notification state for one camera.
     *
     * <p>Package-private so the tests can drive the backoff and cooldown
     * decisions directly instead of sleeping through real 15-second ticks.
     */
    static final class CameraWatch {

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

        /** Whether the cooldown has passed since the last "camera down" notification. */
        boolean notifyAllowed(Instant now) {
            return !now.isBefore(lastNotifiedAt.plus(NOTIFY_COOLDOWN));
        }

        /** Records that a "camera down" notification was just written. */
        void onNotified(Instant now) {
            lastNotifiedAt = now;
        }
    }
}
