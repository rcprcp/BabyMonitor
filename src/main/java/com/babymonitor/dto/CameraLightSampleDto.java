package com.babymonitor.dto;

/**
 * One camera light probe result.
 *
 * @param luma      the frame's mean luma (0-255) at the fixed exposure
 * @param band      the band number (1 = dark .. 6 = very bright), or {@code null}
 * @param lux       the band's representative lux, or {@code null}
 * @param sampledAt when the probe ran
 */
public record CameraLightSampleDto(double luma, Integer band, Integer lux, String sampledAt) {
}
