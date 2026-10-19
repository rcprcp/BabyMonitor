package com.babymonitor.audio;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in {@link AudioClassifier} that replaces the model for every test
 * except {@link YamNetClassifierTest}, which injects {@link YamNetClassifier}
 * itself.
 *
 * <p>Its {@code @Priority} makes it a globally enabled alternative for the whole
 * test run, which is safe because it only exists on the test classpath -- the
 * production build has no bean it could replace. It returns whatever scores a
 * test sets on it, so a cry can be simulated without a model, a microphone or
 * 4 MB of weights, and it counts its sessions so a test can assert that a
 * capture opens one and closes it again.</p>
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class StubClassifier implements AudioClassifier {

    /** YAMNet's "Silence" position, the quiet default every test starts from. */
    private static final int SILENCE = 494;

    /** What every session reports, best class first. */
    private volatile List<AudioClassification> scores;

    private final AtomicInteger sessionsOpened = new AtomicInteger();
    private final AtomicInteger sessionsClosed = new AtomicInteger();

    @PostConstruct
    void initialise() {
        reset();
    }

    /** Forgets the counters and goes back to answering with silence. */
    public void reset() {
        this.scores = List.of(new AudioClassification(SILENCE, "Silence", 0.5f));
        sessionsOpened.set(0);
        sessionsClosed.set(0);
    }

    /** Scores every window as one class. */
    public void always(int index, String name, float confidence) {
        this.scores = List.of(new AudioClassification(index, name, confidence));
    }

    public int sessionsOpened() {
        return sessionsOpened.get();
    }

    public int sessionsClosed() {
        return sessionsClosed.get();
    }

    @Override
    public Session openSession() {
        sessionsOpened.incrementAndGet();
        return new Session() {

            @Override
            public List<AudioClassification> classify(float[] samples) {
                return scores;
            }

            @Override
            public void close() {
                sessionsClosed.incrementAndGet();
            }
        };
    }
}
