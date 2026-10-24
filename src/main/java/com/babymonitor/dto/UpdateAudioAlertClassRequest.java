package com.babymonitor.dto;

/**
 * Toggles one class's alert switch.
 *
 * @param enabled the new state; required
 */
public record UpdateAudioAlertClassRequest(Boolean enabled) {
}
