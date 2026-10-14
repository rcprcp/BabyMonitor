package com.babymonitor.dto;

/**
 * A notification shown on the Notifications page.
 *
 * <p>Mirrors a row of {@code babymonitor.notifications}.
 *
 * @param id          database id, used to mark read / dismiss
 * @param eventTime   ISO-8601 instant the event happened
 * @param description what happened
 * @param videoLink   link to the relevant video clip, or {@code null}
 * @param severity    {@code info}, {@code warning} or {@code critical}
 * @param read        whether the notification has already been seen
 */
public record NotificationDto(
        Long id,
        String eventTime,
        String description,
        String videoLink,
        String severity,
        boolean read) {
}
