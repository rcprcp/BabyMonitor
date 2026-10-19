package com.babymonitor.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.babymonitor.AudioTestResource;
import com.babymonitor.dto.AudioStatusDto;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * End to end through the process boundary, with the stubbed capture binary
 * standing in for sox: a camera's stream must turn into windows, and the silence
 * gate must tell an audible stream from a quiet one. Nothing here touches a real
 * microphone, a database row or the network.
 */
@QuarkusTest
@QuarkusTestResource(AudioTestResource.class)
class AudioServiceTest {

    @Inject
    AudioService audio;

    @Test
    void capturesAnAudibleStreamIntoWindows() {
        int cameraNumber = 7;
        try {
            AudioStatusDto started = audio.start(cameraNumber, "hw:7,0");
            assertTrue(started.running(), "starting a capture should leave it running");
            assertNotNull(started.pid());
            assertEqualsDevice(started, "hw:7,0");

            AudioStatusDto status = await(cameraNumber,
                    seen -> seen.framesSeen() >= 2 && seen.lastFrameLoud(),
                    "audible windows");
            assertTrue(status.lastFrameLoud(), "random PCM should clear the silence gate");
            assertTrue(status.lastRms() > 0.0);
        } finally {
            audio.stop(cameraNumber);
        }
        assertFalse(audio.status(cameraNumber).running(), "a stop must leave nothing running");
        assertNull(audio.status(cameraNumber).pid());
    }

    @Test
    void aQuietStreamProducesWindowsButDoesNotClearTheGate() {
        int cameraNumber = 8;
        try {
            // The stub streams zeros when its device argument contains "silent".
            audio.start(cameraNumber, "silent");

            AudioStatusDto status = await(cameraNumber,
                    seen -> seen.framesSeen() >= 2,
                    "windows from the quiet stream");
            assertFalse(status.lastFrameLoud(), "silence must not clear the gate");
            assertTrue(status.lastRms() < 0.01);
        } finally {
            audio.stop(cameraNumber);
        }
    }

    private static void assertEqualsDevice(AudioStatusDto status, String device) {
        if (!device.equals(status.device())) {
            fail("expected device " + device + " but status reported " + status.device());
        }
    }

    /** Polls the status until the condition holds, or fails after ten seconds. */
    private AudioStatusDto await(int cameraNumber, Predicate<AudioStatusDto> condition, String what) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        AudioStatusDto status;
        do {
            status = audio.status(cameraNumber);
            if (condition.test(status)) {
                return status;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (Instant.now().isBefore(deadline));
        return fail("timed out waiting for " + what + "; last status " + status);
    }
}
