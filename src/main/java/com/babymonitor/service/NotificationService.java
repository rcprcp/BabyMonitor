package com.babymonitor.service;

import com.babymonitor.dto.NotificationDto;
import com.babymonitor.entity.EventType;
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
     * @param eventType   {@link EventType#Video} or {@link EventType#Audio}
     * @param description what happened
     * @param videoLink   link to the relevant clip, or {@code null} for audio
     * @param eventTime   when it happened; defaults to now when {@code null}
     */
    @Transactional
    public NotificationDto add(EventType eventType, String description, String videoLink, Instant eventTime) {
        Notification notification = new Notification(
                eventType,
                eventTime != null ? eventTime : Instant.now(),
                description,
                videoLink);
        notification.persist();
        return toDto(notification);
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
                notification.eventType,
                notification.description,
                notification.videoLink);
    }
}
