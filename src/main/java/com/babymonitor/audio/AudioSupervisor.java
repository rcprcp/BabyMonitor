package com.babymonitor.audio;

import com.babymonitor.entity.CameraAudio;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Keeps the audio capture for cameras that want it running.
 *
 * <p>Every 15 seconds, for any camera_audio row with {@code enabled=true}, it
 * verifies the capture process is alive; if it has died it restarts it. It does
 * not touch cameras that are disabled. The capture runs on a per-camera ALSA
 * {@code device}; if no device is configured, the capture is never started and
 * the supervisor logs a warning.
 */
@ApplicationScoped
public class AudioSupervisor {

    private static final Logger LOG = Logger.getLogger(AudioSupervisor.class);

    @Inject
    AudioService audio;

    @Scheduled(every = "15s")
    void supervise() {
        List<CameraAudio> rows = QuarkusTransaction.requiringNew().call(CameraAudio::listAllOrdered);
        for (CameraAudio row : rows) {
            if (!row.enabled) {
                continue;
            }
            if (audio.status(row.cameraNumber).running()) {
                continue;
            }
            String device = row.device;
            if (device == null || device.isBlank()) {
                LOG.warnf("Audio for camera %d is enabled but no ALSA device is configured; skipping",
                        row.cameraNumber);
                continue;
            }
            try {
                audio.start(row.cameraNumber, device);
                LOG.infof("Restarted audio capture for camera %d on %s", row.cameraNumber, device);
            } catch (UncheckedIOException | IllegalStateException e) {
                LOG.errorf("Could not restart audio capture for camera %d: %s",
                        row.cameraNumber, e.getMessage());
            }
        }
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
            throw new UncheckedIOException("Could not start audio capture for camera " + cameraNumber, new IOException(e));
        }
    }

    /** Stops audio capture for a camera. */
    public void stop(int cameraNumber) {
        audio.stop(cameraNumber);
    }
}
