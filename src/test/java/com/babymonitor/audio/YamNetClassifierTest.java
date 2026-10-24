package com.babymonitor.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * The one test that uses the bundled model for real.
 *
 * <p>It exists to catch the failures a stub cannot see: a class map that no
 * longer lines up with the model's output, a window length the model rejects, a
 * bundled resource that did not survive the build. It deliberately asserts
 * properties rather than a particular clip's scores, so it does not need a cry
 * recording checked into the repository -- the cry path itself is covered by
 * {@link CryDetectorTest} and {@link AudioServiceTest}.</p>
 */
@QuarkusTest
class YamNetClassifierTest {

    private static final int CRYING = 19;
    private static final int BABY_CRY = 20;

    /**
     * The real classifier, by its own type: the test profile replaces the
     * {@link AudioClassifier} interface with {@link StubClassifier}, and only
     * this injection point reaches the model.
     */
    @Inject
    YamNetClassifier classifier;

    @Inject
    AudioConfig config;

    @Test
    void scoresTheWholeClassMapWithNamesThatMatchIt() {
        try (AudioClassifier.Session session = classifier.openSession()) {
            List<AudioClassification> ranked = session.classify(silence());

            assertEquals(521, ranked.size(), "YAMNet should score every class in its map");
            assertEquals("Crying, sobbing", nameOf(ranked, CRYING));
            assertEquals("Baby cry, infant cry", nameOf(ranked, BABY_CRY));
            for (AudioClassification classification : ranked) {
                assertFalse(classification.name().isBlank(), "every class needs a name from the class map");
                assertTrue(classification.confidence() >= 0f && classification.confidence() <= 1f,
                        "confidence out of range for " + classification.name());
            }
        }
    }

    @Test
    void ranksTheClassesBestFirst() {
        try (AudioClassifier.Session session = classifier.openSession()) {
            List<AudioClassification> ranked = session.classify(silence());

            for (int i = 1; i < ranked.size(); i++) {
                assertTrue(ranked.get(i - 1).confidence() >= ranked.get(i).confidence(),
                        "class " + i + " outscored the one before it");
            }
        }
    }

    @Test
    void silenceDoesNotLookLikeACry() {
        try (AudioClassifier.Session session = classifier.openSession()) {
            List<AudioClassification> ranked = session.classify(silence());

            for (AudioClassification classification : ranked) {
                if (classification.index() == CRYING || classification.index() == BABY_CRY) {
                    assertTrue(classification.confidence() < 0.35,
                            "silence should not clear the cry threshold, but " + classification.name()
                                    + " scored " + classification.confidence());
                }
            }
        }
    }

    @Test
    void twoCamerasGetIndependentSessions() {
        try (AudioClassifier.Session first = classifier.openSession();
                AudioClassifier.Session second = classifier.openSession()) {
            assertEquals(521, first.classify(silence()).size());
            assertEquals(521, second.classify(silence()).size());
        }
    }

    @Test
    void concurrentSessionsDoNotRaceTheSharedInterpreter() throws Exception {
        // The model has one TFLite interpreter behind every predictor, and it is
        // not thread-safe. Before the classifier serialised access, two cameras
        // classifying at once could segfault the JVM in libtensorflowlite_jni.
        try (AudioClassifier.Session first = classifier.openSession();
                AudioClassifier.Session second = classifier.openSession()) {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Integer>> results = new ArrayList<>();
                for (int i = 0; i < 25; i++) {
                    results.add(pool.submit(() -> first.classify(silence()).size()));
                    results.add(pool.submit(() -> second.classify(silence()).size()));
                }
                for (Future<Integer> result : results) {
                    assertEquals(521, result.get(30, TimeUnit.SECONDS));
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void rejectsAWindowTheModelWasNotBuiltFor() {
        try (AudioClassifier.Session session = classifier.openSession()) {
            IllegalArgumentException failure =
                    assertThrows(IllegalArgumentException.class, () -> session.classify(new float[16]));

            assertTrue(failure.getMessage().contains(Integer.toString(config.frameSamples())),
                    failure.getMessage());
        }
    }

    private float[] silence() {
        return new float[config.frameSamples()];
    }

    private static String nameOf(List<AudioClassification> ranked, int index) {
        return ranked.stream()
                .filter(classification -> classification.index() == index)
                .map(AudioClassification::name)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no class scored at index " + index));
    }
}
