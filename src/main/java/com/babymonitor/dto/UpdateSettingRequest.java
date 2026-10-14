package com.babymonitor.dto;

/**
 * Body for updating a setting's value.
 *
 * @param value the new value
 */
public record UpdateSettingRequest(String value) {
}
