package com.babymonitor.dto;

/**
 * Replaces the camera light sensor's configuration and calibration.
 *
 * <p>All six calibration lumas are required together (or all omitted to leave
 * the sensor uncalibrated); they must increase from dark to very bright.</p>
 *
 * @param cameraNumber    the camera whose stream is sampled
 * @param exposureMs      the fixed manual exposure, 1-150 ms
 * @param enabled         whether the scheduled sampler is running
 * @param darkLuma        luma captured for the dark band
 * @param veryDimLuma     luma captured for very dim
 * @param dimLuma         luma captured for dim
 * @param moderateLuma    luma captured for moderate
 * @param brightLuma      luma captured for bright
 * @param veryBrightLuma  luma captured for very bright
 */
public record UpdateCameraLightRequest(
        Integer cameraNumber,
        Integer exposureMs,
        Boolean enabled,
        Double darkLuma,
        Double veryDimLuma,
        Double dimLuma,
        Double moderateLuma,
        Double brightLuma,
        Double veryBrightLuma) {
}
