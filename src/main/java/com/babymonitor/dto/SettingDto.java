package com.babymonitor.dto;

/**
 * A setting as returned to the SPA.
 *
 * @param tag   the setting key
 * @param value the setting value
 */
public record SettingDto(String tag, String value) {
}
