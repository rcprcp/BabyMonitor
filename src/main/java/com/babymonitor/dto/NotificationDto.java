package com.babymonitor.dto;

import com.babymonitor.entity.EventType;

/**
 * A notification shown on the Notifications page.
 *
 * <p>Mirrors a row of {@code babymonitor.notifications}.
 *
 * @param id          database id, used to dismiss the row
 * @param eventTime   ISO-8601 instant the event happened
 * @param eventType   {@code Video} or {@code Audio}
 * @param description what happened
 * @param videoLink   link to the relevant clip, or {@code null} for audio
 */
public record NotificationDto(
        Long id,
        String eventTime,
        EventType eventType,
        String description,
        String videoLink) {
}
