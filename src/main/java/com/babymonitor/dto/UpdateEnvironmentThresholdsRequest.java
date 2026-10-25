package com.babymonitor.dto;

import java.math.BigDecimal;

/**
 * Replaces all six environment bounds. Every field is required; the service
 * rejects a pair whose low is not below its high or that is outside its range.
 *
 * @param humidityLow     lowest acceptable humidity (%)
 * @param humidityHigh    highest acceptable humidity (%)
 * @param temperatureLow  lowest acceptable temperature (°F)
 * @param temperatureHigh highest acceptable temperature (°F)
 * @param brightnessLow   lowest acceptable brightness (lux)
 * @param brightnessHigh  highest acceptable brightness (lux)
 */
public record UpdateEnvironmentThresholdsRequest(
        BigDecimal humidityLow,
        BigDecimal humidityHigh,
        BigDecimal temperatureLow,
        BigDecimal temperatureHigh,
        Integer brightnessLow,
        Integer brightnessHigh) {
}
