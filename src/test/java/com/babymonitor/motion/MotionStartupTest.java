package com.babymonitor.motion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.babymonitor.MotionTestResource;
import com.babymonitor.dto.MotionStatusDto;
import com.babymonitor.entity.Camera;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Startup is cold: no {@code motion} survives a restart and no camera stays
 * enabled, so a camera only comes up when the user switches it on.
 *
 * <p>Runs in this package to reach the package-private
 * {@link MotionService#resetOnStartup}, which is otherwise only fired by the
 * container at boot and so not directly assertable.
 */
@QuarkusTest
@QuarkusTestResource(MotionTestResource.class)
class MotionStartupTest {

    @Inject
    MotionService motion;

    @Test
    void aRestartKillsLeftoverProcessesAndSwitchesEveryCameraOff() {
        int cameraNumber = 2;
        List<Camera> rows = Camera.listByCamera(cameraNumber);
        assertFalse(rows.isEmpty(), "camera 2 is seeded with parameters");

        // A user switches the camera on from its page: the config is written and
        // the process comes up.
        MotionStatusDto up =
                motion.apply(cameraNumber, MotionService.toParameters(rows));
        assertTrue(up.running(), "switching a camera on should start Motion");

        // Then the app restarts. The child outlives the JVM that spawned it, so
        // the reset has to kill it rather than leave it orphaned.
        motion.resetOnStartup(null);

        MotionStatusDto after = motion.status(cameraNumber);
        assertFalse(after.running(), "a restart must not leave Motion running");
        assertNull(after.pid(), "the leftover process should be gone");
        assertFalse(
                Camera.listByCamera(cameraNumber).get(0).enabled,
                "every camera comes back switched off");
    }
}
