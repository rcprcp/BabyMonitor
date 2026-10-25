package com.babymonitor.dto;

/**
 * One hour of camera light history: the latest band sampled in that hour.
 *
 * @param hour      the hour bucket in UTC, ISO-8601 (e.g. {@code 2026-10-24T21:00:00Z})
 * @param band      the band number (1 = dark .. 6 = very bright)
 * @param proxyLux  the band's representative lux, used by the brightness alert
 */
public record CameraLightBandDto(String hour, Integer band, Integer proxyLux) {
}
