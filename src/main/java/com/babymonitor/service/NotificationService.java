package com.babymonitor.service;

import com.babymonitor.dto.NotificationDto;
import com.babymonitor.entity.Notification;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

/**
 * Notification feed, backed by the {@code babymonitor.notifications} table.
 *
 * <p>Rows are written by the device webhooks ({@code WebhookResource}) and read
 * by the Notifications page ({@code NotificationsResource}).
 */
@ApplicationScoped
public class NotificationService {

    /** All notifications, newest event first. */
    @Transactional
    public List<NotificationDto> list() {
        // Assigned to a typed local so Panache's generic listAll() infers
        // Notification (chaining the call directly infers the base type).
        List<Notification> rows = Notification.listAll(Sort.descending("eventTime"));
        return rows.stream()
                .map(NotificationService::toDto)
                .toList();
    }

    /**
     * Persists a new notification and returns it.
     *
     * @param description what happened
     * @param videoLink   link to the relevant clip, or {@code null} for audio
     * @param severity    {@code info}, {@code warning} or {@code critical}
     * @param eventTime   when it happened; defaults to now when {@code null}
     */
    @Transactional
    public NotificationDto add(String description, String videoLink, String severity, Instant eventTime) {
        Notification notification = new Notification(
                eventTime != null ? eventTime : Instant.now(),
                description,
                videoLink,
                severity == null || severity.isBlank() ? "warning" : severity);
        notification.persist();
        return toDto(notification);
    }

    /** Marks one notification read. Returns {@code false} when the id is unknown. */
    @Transactional
    public boolean markRead(Long id) {
        Notification notification = Notification.findById(id);
        if (notification == null) {
            return false;
        }
        notification.read = true;
        return true;
    }

    /** Removes one notification. Returns {@code false} when the id is unknown. */
    @Transactional
    public boolean dismiss(Long id) {
        return Notification.deleteById(id);
    }

    private static NotificationDto toDto(Notification notification) {
        return new NotificationDto(
                notification.id,
                notification.eventTime.toString(),
                notification.description,
                notification.videoLink,
                notification.severity,
                notification.read);
    }
}
