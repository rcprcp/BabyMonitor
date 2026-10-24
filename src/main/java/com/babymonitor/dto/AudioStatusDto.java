package com.babymonitor.dto;

/**
 * The state of one camera's audio capture and classification.
 *
 * <p>The window counters and the most recent class exist so the pipeline can be
 * asserted through the process boundary in tests, without waiting on a
 * notification row or an API.</p>
 *
 * @param cameraNumber   the camera
 * @param enabled        the camera's desired audio state (its {@code camera_audio}
 *                       row), set by Start and cleared by Stop. When it is true
 *                       and {@code running} is false the supervisor is expected
 *                       to bring the capture back
 * @param running        whether a capture process is running for this camera
 * @param pid            its process id, or {@code null} when it is stopped
 * @param device         the ALSA device being captured, or {@code null} when stopped
 * @param framesSeen     analysis windows produced since the last start
 * @param lastRms        root-mean-square of the most recent window, 0 when none
 * @param lastFrameLoud  whether that window cleared the silence gate
 * @param lastClass      the best class YAMNet scored for the most recent
 *                       classified window, or {@code null} when none has been
 * @param lastConfidence that class's confidence, 0 when none has been scored
 * @param alertsRaised   {@code Audio} notifications raised since the last start
 */
public record AudioStatusDto(
        int cameraNumber,
        boolean enabled,
        boolean running,
        Long pid,
        String device,
        long framesSeen,
        double lastRms,
        boolean lastFrameLoud,
        String lastClass,
        float lastConfidence,
        long alertsRaised) {
}
