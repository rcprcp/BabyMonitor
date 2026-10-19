package com.babymonitor.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.babymonitor.AudioTestResource;
import com.babymonitor.dto.AudioStatusDto;
import com.babymonitor.entity.EventType;
import com.babymonitor.entity.Notification;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End to end through the process boundary, with the stubbed capture binary
 * standing in for sox and a stubbed classifier standing in for YAMNet: a
 * camera's stream must turn into windows, the silence gate must tell an audible
 * stream from a quiet one, and a run of confident cry windows must raise exactly
 * one {@code Audio} notification.
 */
@QuarkusTest
@QuarkusTestResource(AudioTestResource.class)
class AudioServiceTest {

    /** YAMNet's "Crying, sobbing" position in the class map. */
    private static final int CRYING = 19;

    @Inject
    AudioService audio;

    @Inject
    StubClassifier classifier;

    /**
     * Every test starts from silence, so only the tests that say "this is a cry"
     * can raise an alert.
     */
    @BeforeEach
    void resetClassifier() {
        classifier.reset();
    }

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
            assertEquals("Silence", status.lastClass(), "an audible window should have been classified");
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
            assertNull(status.lastClass(), "a quiet window is never classified");
        } finally {
            audio.stop(cameraNumber);
        }
    }

    @Test
    void aRunOfCryWindowsRaisesOneAudioNotification() throws InterruptedException {
        classifier.always(CRYING, "Crying, sobbing", 0.9f);
        int cameraNumber = 9;
        try {
            audio.start(cameraNumber, "hw:9,0");

            AudioStatusDto alert = await(cameraNumber,
                    seen -> seen.alertsRaised() >= 1,
                    "an audio alert");
            assertEquals("Crying, sobbing", alert.lastClass());
            assertTrue(alert.lastConfidence() >= 0.9f);

            // The profile's cooldown is ten seconds and the stream never stops
            // crying, so the same camera must not raise a second alert.
            Thread.sleep(300);
            assertEquals(1, audio.status(cameraNumber).alertsRaised(),
                    "the cooldown should hold a repeat back");
        } finally {
            audio.stop(cameraNumber);
        }
        Notification row = findNotification(cameraNumber);
        assertNotNull(row, "the alert should have been stored");
        assertEquals(EventType.Audio, row.eventType);
        assertTrue(row.description.contains("Crying, sobbing"), row.description);
    }

    @Test
    void aCryBelowTheConfidenceThresholdRaisesNothing() throws InterruptedException {
        classifier.always(CRYING, "Crying, sobbing", 0.2f);
        int cameraNumber = 10;
        try {
            audio.start(cameraNumber, "hw:10,0");

            await(cameraNumber, seen -> seen.framesSeen() >= 4, "windows to classify");
            Thread.sleep(200);
            assertEquals(0, audio.status(cameraNumber).alertsRaised(),
                    "a weak score is not worth waking anyone");
        } finally {
            audio.stop(cameraNumber);
        }
        assertNull(findNotification(cameraNumber), "nothing should have been stored");
    }

    @Test
    void aStopClosesTheClassifierSessionItOpened() {
        int cameraNumber = 11;
        audio.start(cameraNumber, "hw:11,0");
        assertEquals(1, classifier.sessionsOpened(), "one session per capture");
        audio.stop(cameraNumber);

        assertEquals(1, classifier.sessionsClosed(), "stopping the capture should close the session");
    }

    private static void assertEqualsDevice(AudioStatusDto status, String device) {
        if (!device.equals(status.device())) {
            fail("expected device " + device + " but status reported " + status.device());
        }
    }

    /** The stored audio alert for a camera, or {@code null} when there is none. */
    private static Notification findNotification(int cameraNumber) {
        return QuarkusTransaction.requiringNew().call(() -> Notification.find(
                        "description like ?1", "%heard on camera " + cameraNumber + "%")
                .firstResult());
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
