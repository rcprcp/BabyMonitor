package com.babymonitor.dto;

import java.math.BigDecimal;

/**
 * The high/low environment bounds shown on the Environment page.
 *
 * <p>Humidity is a percentage (0-100), temperature is Fahrenheit (32-100) and
 * brightness is lux (0-3000). The readings themselves are Celsius; the page
 * converts for the temperature comparison.</p>
 *
 * @param humidityLow     lowest acceptable humidity (%)
 * @param humidityHigh    highest acceptable humidity (%)
 * @param temperatureLow  lowest acceptable temperature (°F)
 * @param temperatureHigh highest acceptable temperature (°F)
 * @param brightnessLow   lowest acceptable brightness (lux)
 * @param brightnessHigh  highest acceptable brightness (lux)
 */
public record EnvironmentThresholdsDto(
        BigDecimal humidityLow,
        BigDecimal humidityHigh,
        BigDecimal temperatureLow,
        BigDecimal temperatureHigh,
        Integer brightnessLow,
        Integer brightnessHigh) {
}
