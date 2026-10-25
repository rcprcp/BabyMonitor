package com.babymonitor.dto;

/**
 * The camera light sensor's configuration, calibration and last sample.
 *
 * @param cameraNumber    the camera whose stream is sampled
 * @param exposureMs      the fixed manual exposure used for a probe
 * @param enabled         whether the scheduled sampler is running
 * @param darkLuma        luma captured for the dark band, or {@code null}
 * @param veryDimLuma     luma captured for very dim, or {@code null}
 * @param dimLuma         luma captured for dim, or {@code null}
 * @param moderateLuma    luma captured for moderate, or {@code null}
 * @param brightLuma      luma captured for bright, or {@code null}
 * @param veryBrightLuma  luma captured for very bright, or {@code null}
 * @param lastLuma        the most recent sample's luma, or {@code null}
 * @param lastBand        the band of the most recent sample, or {@code null}
 * @param lastLux         the proxy lux of that band, or {@code null}
 * @param lastSampleAt    when the last sample was taken, or {@code null}
 */
public record CameraLightDto(
        int cameraNumber,
        int exposureMs,
        boolean enabled,
        Double darkLuma,
        Double veryDimLuma,
        Double dimLuma,
        Double moderateLuma,
        Double brightLuma,
        Double veryBrightLuma,
        Double lastLuma,
        String lastBand,
        Integer lastLux,
        String lastSampleAt) {
}
