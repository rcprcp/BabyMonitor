package com.babymonitor.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.babymonitor.AudioTestResource;
import com.babymonitor.entity.CameraAudio;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Audio startup is cold: no capture survives a restart and no camera stays
 * switched on, so audio comes up only when a camera (or its Audio button) starts
 * it. Mirrors {@link com.babymonitor.motion.MotionStartupTest}.
 *
 * <p>Runs in this package to reach the package-private
 * {@link AudioService#resetOnStartup}, which the container fires at boot and so
 * is not otherwise directly assertable.
 */
@QuarkusTest
@QuarkusTestResource(AudioTestResource.class)
class AudioStartupTest {

    /** A camera number the app does not define, so only this test owns its row. */
    private static final int CAMERA = 13;

    @Inject
    AudioService audio;

    @Test
    void aRestartKillsLeftoverCapturesAndClearsDesiredState() {
        setEnabled(CAMERA, true);
        audio.start(CAMERA, "hw:13,0");
        assertTrue(audio.status(CAMERA).running(), "the capture should be up to begin with");

        // The child outlives the JVM that spawned it, so the reset has to kill
        // it rather than leave it orphaned.
        audio.resetOnStartup(null);

        assertFalse(audio.status(CAMERA).running(), "a restart must not leave a capture running");
        assertFalse(enabled(CAMERA), "every camera comes back with desired audio state false");
        deleteRow(CAMERA);
    }

    private static void setEnabled(int cameraNumber, boolean enabled) {
        QuarkusTransaction.requiringNew().run(() -> {
            CameraAudio row = CameraAudio.findByCamera(cameraNumber);
            if (row == null) {
                row = new CameraAudio();
                row.cameraNumber = cameraNumber;
                row.enabled = enabled;
                row.persist();
            } else {
                row.enabled = enabled;
            }
        });
    }

    private static boolean enabled(int cameraNumber) {
        return QuarkusTransaction.requiringNew().call(() -> {
            CameraAudio row = CameraAudio.findByCamera(cameraNumber);
            return row != null && row.enabled;
        });
    }

    private static void deleteRow(int cameraNumber) {
        QuarkusTransaction.requiringNew()
                .run(() -> CameraAudio.delete("cameraNumber = ?1", cameraNumber));
    }
}
