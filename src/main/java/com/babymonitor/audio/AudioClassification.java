package com.babymonitor.audio;

/**
 * One label YAMNet scored for an analysis window.
 *
 * <p>YAMNet always returns a score for every one of its 521 classes, so these
 * come back as a ranked list rather than a single answer. The index is what the
 * configuration names ({@code alert-class-indices}) and the name is what a
 * notification shows; both are carried so a narrowing of the alert list later
 * is entirely a configuration change.</p>
 *
 * @param index      the class's position in YAMNet's class map
 * @param name       its display name, e.g. {@code Crying, sobbing}
 * @param confidence the model's score, {@code 0.0} to {@code 1.0}
 */
public record AudioClassification(int index, String name, float confidence)
        implements Comparable<AudioClassification> {

    /** Orders by confidence, lowest first, so the best class sorts last. */
    @Override
    public int compareTo(AudioClassification other) {
        return Float.compare(confidence, other.confidence);
    }
}
