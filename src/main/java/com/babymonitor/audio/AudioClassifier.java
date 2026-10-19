package com.babymonitor.audio;

import java.util.List;

/**
 * Scores one analysis window against the audio classes the model knows.
 *
 * <p>Deliberately an interface: the CDI bean is {@link YamNetClassifier}, but
 * the capture loop only needs "give me the ranked classes for this window", so
 * tests can stand in a stub that returns a canned cry without loading a model
 * or opening a microphone.</p>
 *
 * <p>A predictor is not thread-safe, and the capture loop runs one reader
 * thread per camera, so classification is handed out as a {@link Session}:
 * {@link #openSession()} when a camera's capture starts, {@link Session#close()}
 * when it stops. One session belongs to one thread.</p>
 */
public interface AudioClassifier {

    /** Opens a thread-confined classifier for one camera's capture. */
    Session openSession();

    /** A classifier bound to the thread that created it, until it is closed. */
    interface Session extends AutoCloseable {

        /**
         * Scores one window.
         *
         * @param samples mono float samples in {@code [-1, 1]}, exactly
         *                {@link AudioConfig#frameSamples()} long
         * @return every class, best first; never empty for a loaded model
         * @throws IllegalStateException when inference fails
         */
        List<AudioClassification> classify(float[] samples);

        @Override
        void close();
    }
}
