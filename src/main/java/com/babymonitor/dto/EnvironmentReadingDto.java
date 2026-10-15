package com.babymonitor.dto;

import java.math.BigDecimal;

/**
 * One ambient reading as returned to the SPA.
 *
 * <p>Mirrors a row of {@code babymonitor.environment}.
 *
 * @param eventTime   ISO-8601 instant the sample was taken
 * @param temperature air temperature in degrees Celsius
 * @param humidity    relative humidity as a percentage (0-100)
 * @param brightness  light level in lux
 */
public record EnvironmentReadingDto(
        String eventTime,
        BigDecimal temperature,
        BigDecimal humidity,
        Integer brightness) {
}
