package com.babymonitor.dto;

/**
 * A notification shown on the Notifications page.
 *
 * @param id        stable identifier, used to mark read / dismiss
 * @param title     short headline
 * @param message   longer body text
 * @param severity  {@code info}, {@code warning} or {@code critical}
 * @param timestamp ISO-8601 instant the notification was raised
 * @param read      whether the user has already seen it
 */
public record NotificationDto(
        String id,
        String title,
        String message,
        String severity,
        String timestamp,
        boolean read) {
}
