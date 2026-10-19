package com.babymonitor.dto;

/**
 * The state of one camera's audio capture process.
 *
 * <p>Phase one reports capture only, so there is no class or confidence here
 * yet; those arrive with the classifier. The window counters exist so the
 * dry-run pipeline can be asserted in tests without a database or an API.</p>
 *
 * @param cameraNumber  the camera
 * @param running       whether a capture process is running for this camera
 * @param pid           its process id, or {@code null} when it is stopped
 * @param device        the ALSA device being captured, or {@code null} when stopped
 * @param framesSeen    analysis windows produced since the last start
 * @param lastRms       root-mean-square of the most recent window, 0 when none
 * @param lastFrameLoud whether that window cleared the silence gate
 */
public record AudioStatusDto(
        int cameraNumber,
        boolean running,
        Long pid,
        String device,
        long framesSeen,
        double lastRms,
        boolean lastFrameLoud) {
}
