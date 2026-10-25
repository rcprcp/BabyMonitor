package com.babymonitor.service;

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

/** Every camera and its audio come up at boot, even when they were stopped before. */
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
    void startsEveryCameraAndItsAudioWhateverTheSavedStateSaid() {
        try {
            // Simulate a camera stopped before the app shut down.
            for (int cameraNumber : CAMERAS) {
                motion.stop(cameraNumber);
                audio.stop(cameraNumber);
                setCameraEnabled(cameraNumber, false);
                setAudioEnabled(cameraNumber, false);
            }

            startup.startAll();

            for (int cameraNumber : CAMERAS) {
                assertTrue(
                        motion.status(cameraNumber).running(),
                        "camera " + cameraNumber + " should be running after boot");
                assertTrue(
                        audio.status(cameraNumber).running(),
                        "camera " + cameraNumber + " audio should be running after boot");
                assertTrue(
                        cameraEnabled(cameraNumber),
                        "camera " + cameraNumber + " should come back enabled");
                assertTrue(
                        audioEnabled(cameraNumber),
                        "camera " + cameraNumber + " audio should come back enabled");
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
