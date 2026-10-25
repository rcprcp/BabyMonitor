package com.babymonitor.dto;

/**
 * One camera light probe result.
 *
 * @param luma      the frame's mean luma (0-255) at the fixed exposure
 * @param band      the calibrated band, or {@code null} when uncalibrated
 * @param lux       the band's representative lux, or {@code null}
 * @param sampledAt when the probe ran
 */
public record CameraLightSampleDto(double luma, String band, Integer lux, String sampledAt) {
}
