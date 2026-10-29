package com.babymonitor.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.babymonitor.AudioTestResource;
import com.babymonitor.MotionTestResource;
import com.babymonitor.audio.AudioService;
import com.babymonitor.entity.Camera;
import com.babymonitor.entity.CameraAudio;
import com.babymonitor.motion.MotionService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every camera comes up at boot even when it was stopped before; audio follows
 * its "Listen for cries" preference.
 */
@QuarkusTest
@QuarkusTestResource(MotionTestResource.class)
@QuarkusTestResource(AudioTestResource.class)
class CameraStartupTest {

    private static final List<Integer> CAMERAS = List.of(1, 2);

    @Inject
    CameraStartup startup;

    @Inject
    MotionService motion;

    @Inject
    AudioService audio;

    @Test
    void startsEveryCameraWhateverTheSavedStateSaid() {
        try {
            // Simulate a camera stopped before the app shut down, with audio
            // still wanted.
            for (int cameraNumber : CAMERAS) {
                motion.stop(cameraNumber);
                audio.stop(cameraNumber);
                setCameraEnabled(cameraNumber, false);
                setAudioEnabled(cameraNumber, true);
            }

            startup.startAll();

            for (int cameraNumber : CAMERAS) {
                assertTrue(
                        motion.status(cameraNumber).running(),
                        "camera " + cameraNumber + " should be running after boot");
                assertTrue(
                        cameraEnabled(cameraNumber),
                        "camera " + cameraNumber + " should come back enabled");
                assertTrue(
                        audio.status(cameraNumber).running(),
                        "camera " + cameraNumber + " audio is wanted and should be running");
            }
        } finally {
            for (int cameraNumber : CAMERAS) {
                motion.stop(cameraNumber);
                audio.stop(cameraNumber);
                setCameraEnabled(cameraNumber, false);
                deleteAudioRow(cameraNumber);
            }
        }
    }

    @Test
    void leavesAudioOffWhenThePreferenceIsOff() {
        try {
            for (int cameraNumber : CAMERAS) {
                motion.stop(cameraNumber);
                audio.stop(cameraNumber);
                setCameraEnabled(cameraNumber, false);
            }
            // Camera 1 listens; camera 2 was deliberately muted.
            setAudioEnabled(1, true);
            setAudioEnabled(2, false);

            startup.startAll();

            assertTrue(motion.status(1).running(), "camera 1 should still start");
            assertTrue(audio.status(1).running(), "camera 1 wants audio");
            assertTrue(motion.status(2).running(), "camera 2 should still start");
            assertFalse(audio.status(2).running(), "camera 2's audio preference is off");
            assertFalse(audioEnabled(2), "boot must not overrule the audio preference");
        } finally {
            for (int cameraNumber : CAMERAS) {
                motion.stop(cameraNumber);
                audio.stop(cameraNumber);
                setCameraEnabled(cameraNumber, false);
                deleteAudioRow(cameraNumber);
            }
        }
    }

    @Test
    void startingACameraAgainIsNotAnError() {
        try {
            startup.startAll();
            // The boot observer and the supervisor may both ask; the second
            // call must leave the running process alone rather than spawn a
            // duplicate.
            startup.startAll();

            for (int cameraNumber : CAMERAS) {
                assertTrue(motion.status(cameraNumber).running());
            }
        } finally {
            for (int cameraNumber : CAMERAS) {
                motion.stop(cameraNumber);
                audio.stop(cameraNumber);
                setCameraEnabled(cameraNumber, false);
                deleteAudioRow(cameraNumber);
            }
        }
    }

    private static void setCameraEnabled(int cameraNumber, boolean enabled) {
        QuarkusTransaction.requiringNew().run(() -> Camera.update(
                "set enabled = ?1 where cameraNumber = ?2", enabled, cameraNumber));
    }

    private static boolean cameraEnabled(int cameraNumber) {
        return QuarkusTransaction.requiringNew()
                .call(() -> Camera.listByCamera(cameraNumber).get(0).enabled);
    }

    private static void setAudioEnabled(int cameraNumber, boolean enabled) {
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

    private static boolean audioEnabled(int cameraNumber) {
        return QuarkusTransaction.requiringNew().call(() -> {
            CameraAudio row = CameraAudio.findByCamera(cameraNumber);
            return row != null && row.enabled;
        });
    }

    private static void deleteAudioRow(int cameraNumber) {
        QuarkusTransaction.requiringNew()
                .run(() -> CameraAudio.delete("cameraNumber = ?1", cameraNumber));
    }
}
