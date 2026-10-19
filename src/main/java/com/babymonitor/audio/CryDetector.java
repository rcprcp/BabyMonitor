package com.babymonitor.audio;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decides when a run of per-window YAMNet scores is worth an alert.
 *
 * <p>A single window is a poor trigger. YAMNet's score for a real cry ramps
 * over several windows rather than jumping to one, and a lone window above the
 * threshold is as likely to be a door or a cough that briefly resembles one.
 * So an alert needs {@code windowsToAlert} consecutive windows whose best
 * alert-class score clears {@code minConfidence}, and then stays quiet for
 * {@code cooldown} before it may fire again -- the same two limits
 * {@link com.babymonitor.motion.MotionSupervisor} uses to stop a camera that
 * flaps from alerting on every check.</p>
 *
 * <p>The class to alert on is picked from the configured indices rather than
 * from the model's overall best class, so a clear "Laughter" win can never
 * raise a cry alert. A run that is already long enough keeps counting through
 * the cooldown, so a baby who cries without pause is not asked to prove himself
 * again the instant the cooldown expires. One instance watches one camera and is
 * not thread-safe; the caller keeps it on the camera's own reader thread.</p>
 */
public final class CryDetector {

    private final Set<Integer> alertIndices;
    private final float minConfidence;
    private final int windowsToAlert;
    private final Duration cooldown;

    /** How many windows in a row have cleared the threshold. */
    private int streak;

    /** When this detector last fired, or {@code null} if it never has. */
    private Instant lastAlert;

    /**
     * @param alertIndices   the class indices worth alerting on, e.g. YAMNet's
     *                       {@code 19} (Crying, sobbing) and {@code 20} (Baby cry)
     * @param minConfidence  lowest confidence that counts as a detection
     * @param windowsToAlert consecutive windows needed before alerting
     * @param cooldown       quiet time after an alert before another may fire
     */
    public CryDetector(
            Set<Integer> alertIndices, double minConfidence, int windowsToAlert, Duration cooldown) {
        if (alertIndices.isEmpty()) {
            throw new IllegalArgumentException("alertIndices must not be empty");
        }
        if (minConfidence < 0.0 || minConfidence > 1.0) {
            throw new IllegalArgumentException("minConfidence must be between 0 and 1");
        }
        if (windowsToAlert < 1) {
            throw new IllegalArgumentException("windowsToAlert must be positive");
        }
        this.alertIndices = Set.copyOf(alertIndices);
        this.minConfidence = (float) minConfidence;
        this.windowsToAlert = windowsToAlert;
        this.cooldown = cooldown;
    }

    /**
     * Feeds one window's ranked classes and reports whether it should alert.
     *
     * @param ranked what {@link AudioClassifier} scored, best first
     * @param now    when the window ended, from the caller's clock
     * @return the class to alert on, or empty while the run is not yet long
     *         enough or the cooldown is still running
     */
    public Optional<AudioClassification> accept(List<AudioClassification> ranked, Instant now) {
        Optional<AudioClassification> best = strongestAlertClass(ranked);
        if (best.isEmpty() || best.get().confidence() < minConfidence) {
            streak = 0;
            return Optional.empty();
        }
        streak++;
        if (streak < windowsToAlert) {
            return Optional.empty();
        }
        if (lastAlert != null && now.isBefore(lastAlert.plus(cooldown))) {
            return Optional.empty();
        }
        // Starting the run again after an alert means the next one needs just as
        // much evidence as this one did.
        streak = 0;
        lastAlert = now;
        return best;
    }

    /** The highest-scoring class among the configured alert indices. */
    private Optional<AudioClassification> strongestAlertClass(List<AudioClassification> ranked) {
        return ranked.stream()
                .filter(classification -> alertIndices.contains(classification.index()))
                .max(Comparator.comparingDouble(AudioClassification::confidence));
    }

    /** Forgets the current run and cooldown, for a capture starting over. */
    public void reset() {
        streak = 0;
        lastAlert = null;
    }
}
