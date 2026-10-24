package com.babymonitor.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.babymonitor.dto.AudioAlertClassDto;
import com.babymonitor.dto.AudioAlertConfigDto;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The DB-backed alert set the Audio Notification Config page edits. */
@QuarkusTest
class AudioAlertSettingsTest {

    @Inject
    AudioAlertSettings settings;

    @AfterEach
    void reset() {
        settings.resetToDefault();
    }

    @Test
    void aFreshInstallEnablesOnlyTheSpeechAndHumanSoundsGroup() {
        assertEquals(defaults(), settings.enabledIndices());
    }

    @Test
    void theConfigListsEveryClassInItsGroup() {
        AudioAlertConfigDto config = settings.config();

        assertEquals(521, config.classes().size());
        assertEquals(6, config.groups().size());

        AudioAlertClassDto crying = classAt(config, 19);
        assertEquals("Crying, sobbing", crying.name());
        assertEquals(1, crying.groupId());
        assertTrue(crying.enabled());

        AudioAlertClassDto drum = classAt(config, 159);
        assertEquals("Drum", drum.name());
        assertEquals(2, drum.groupId());
        assertFalse(drum.enabled());
    }

    @Test
    void togglingOneClassTakesEffectImmediately() {
        settings.setEnabled(159, true);
        assertTrue(settings.enabledIndices().contains(159));

        settings.setEnabled(159, false);
        assertFalse(settings.enabledIndices().contains(159));
    }

    @Test
    void aBulkUpdateReplacesTheWholeSet() {
        settings.setEnabledIndices(Set.of(11, 19, 20, 21));

        assertEquals(Set.of(11, 19, 20, 21), settings.enabledIndices());
    }

    @Test
    void resetRestoresTheDefaultGroup() {
        settings.setEnabledIndices(Set.of(159));

        settings.resetToDefault();

        assertEquals(defaults(), settings.enabledIndices());
    }

    private static Set<Integer> defaults() {
        return IntStream.rangeClosed(0, 61).boxed().collect(Collectors.toSet());
    }

    private static AudioAlertClassDto classAt(AudioAlertConfigDto config, int index) {
        return config.classes().stream()
                .filter(candidate -> candidate.index() == index)
                .findFirst()
                .orElseThrow();
    }
}
