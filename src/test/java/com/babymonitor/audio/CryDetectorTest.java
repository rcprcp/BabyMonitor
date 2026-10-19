package com.babymonitor.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The detector is what stands between a model that scores every window and a
 * chime in the nursery, so its two limits are tested directly: a run of windows
 * is needed before anything fires, and one alert silences the camera for a
 * while afterwards.
 */
class CryDetectorTest {

    private static final int CRYING = 19;
    private static final int BABY_CRY = 20;
    private static final int LAUGHTER = 13;
    private static final Instant T0 = Instant.parse("2026-10-18T20:00:00Z");

    private static CryDetector detector() {
        return new CryDetector(Set.of(CRYING, BABY_CRY), 0.35, 2, Duration.ofSeconds(60));
    }

    private static List<AudioClassification> window(int index, String name, float confidence) {
        return List.of(new AudioClassification(index, name, confidence));
    }

    @Test
    void alertsOnlyAfterTheConfiguredRunOfWindows() {
        CryDetector detector = detector();

        assertTrue(detector.accept(cry(0.50f), T0).isEmpty(), "one window is not enough");
        Optional<AudioClassification> alert = detector.accept(cry(0.42f), T0.plusSeconds(1));

        assertTrue(alert.isPresent(), "the second window in a row should alert");
        assertEquals(CRYING, alert.get().index());
    }

    @Test
    void aWindowBelowTheThresholdBreaksTheRun() {
        CryDetector detector = detector();

        detector.accept(cry(0.50f), T0);
        assertTrue(detector.accept(cry(0.10f), T0.plusSeconds(1)).isEmpty(), "a quiet window resets the run");
        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(2)).isEmpty(), "the run has started over");
        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(3)).isPresent());
    }

    @Test
    void alertsOnTheCryClassEvenWhenAnotherClassScoresHigher() {
        CryDetector detector = detector();
        List<AudioClassification> mixed = List.of(
                new AudioClassification(LAUGHTER, "Laughter", 0.91f),
                new AudioClassification(CRYING, "Crying, sobbing", 0.50f));

        detector.accept(mixed, T0);
        Optional<AudioClassification> alert = detector.accept(mixed, T0.plusSeconds(1));

        assertTrue(alert.isPresent());
        assertEquals("Crying, sobbing", alert.get().name());
    }

    @Test
    void ignoresAClassThatIsNotInTheAlertList() {
        CryDetector detector = detector();

        for (int i = 0; i < 10; i++) {
            assertTrue(detector.accept(window(LAUGHTER, "Laughter", 0.99f), T0.plusSeconds(i)).isEmpty(),
                    "laughter is not a cry");
        }
    }

    @Test
    void staysQuietForTheCooldownAfterAnAlert() {
        CryDetector detector = detector();

        detector.accept(cry(0.50f), T0);
        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(1)).isPresent(), "the first alert fires");

        // The crying never stops, so a run is always ready and only the cooldown
        // holds the next alert back.
        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(30)).isEmpty(), "still cooling down");
        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(60)).isEmpty(), "one second short");

        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(61)).isPresent(),
                "sustained crying alerts again the moment the cooldown expires");
    }

    @Test
    void aFreshCryAfterTheCooldownNeedsItsOwnRun() {
        CryDetector detector = detector();

        detector.accept(cry(0.50f), T0);
        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(1)).isPresent());
        // The room goes quiet again, which ends the run...
        detector.accept(cry(0.05f), T0.plusSeconds(30));

        // ...so well after the cooldown, one window is still not enough.
        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(120)).isEmpty(), "a new run is needed");
        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(121)).isPresent());
    }

    @Test
    void resetForgetsTheRunAndTheCooldown() {
        CryDetector detector = detector();

        detector.accept(cry(0.50f), T0);
        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(1)).isPresent());
        detector.reset();

        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(2)).isEmpty(), "a reset needs a fresh run");
        assertTrue(detector.accept(cry(0.50f), T0.plusSeconds(3)).isPresent(),
                "and the cooldown was forgotten too");
    }

    @Test
    void rejectsSettingsThatCouldNotWork() {
        assertThrows(IllegalArgumentException.class,
                () -> new CryDetector(Set.of(), 0.35, 2, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new CryDetector(Set.of(CRYING), 1.5, 2, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new CryDetector(Set.of(CRYING), 0.35, 0, Duration.ofSeconds(1)));

        CryDetector detector = new CryDetector(Set.of(CRYING), 0.35, 1, Duration.ZERO);
        assertFalse(detector.accept(List.of(), T0).isPresent(), "no classes is not an alert");
    }

    private static List<AudioClassification> cry(float confidence) {
        return window(CRYING, "Crying, sobbing", confidence);
    }
}
