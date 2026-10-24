package com.babymonitor.motion;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Motion keeps running and retries forever when its video device is missing, so
 * the app checks the device itself instead of trusting the process.
 */
class MotionDeviceCheckTest {

    @Test
    void reportsAMissingDevice() {
        assertNotNull(MotionService.deviceError("/dev/definitely-not-a-camera"));
    }

    @Test
    void reportsABlankDevice() {
        assertNotNull(MotionService.deviceError(null));
        assertNotNull(MotionService.deviceError("  "));
    }

    @Test
    void acceptsAnExistingReadableDevice() throws IOException {
        Path device = Files.createTempFile("babymonitor-video", ".dev");
        try {
            assertNull(MotionService.deviceError(device.toString()));
        } finally {
            Files.deleteIfExists(device);
        }
    }
}
