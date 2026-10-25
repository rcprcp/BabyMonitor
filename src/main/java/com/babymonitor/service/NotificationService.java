package com.babymonitor.service;

import com.babymonitor.dto.NotificationDto;
import com.babymonitor.entity.EventType;
import com.babymonitor.entity.Notification;
import com.babymonitor.entity.NotificationDismissal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

/**
 * Notification feed, backed by the {@code babymonitor.notifications} table, plus
 * the per-account dismissals in {@code babymonitor.notification_dismissals}.
 *
 * <p>Rows are written by the device webhooks ({@code WebhookResource}) and read
 * by the Notifications page ({@code NotificationsResource}).
 *
 * <p>New rows are also pushed to any open event streams ({@link NotificationBroadcaster})
 * so the SPA can alert without polling.
 */
@ApplicationScoped
public class NotificationService {

    @Inject
    NotificationBroadcaster broadcaster;

    @Inject
    AfterCommit afterCommit;

    /**
     * The notifications one account should see: everything it has not dismissed,
     * newest event first.
     *
     * <p>The filter is an anti-join on the (notification, account) pair rather
     * than on the notification alone: a notification another account dismissed
     * stays visible here.
     *
     * @param userId the account reading the feed, from its session
     */
    @Transactional
    public List<NotificationDto> list(Long userId) {
        // Assigned to a typed local so Panache's query type is inferred rather
        // than resolved from the return statement.
        List<Notification> rows = Notification.find(
                        "select n from Notification n where not exists "
                                + "(select d.notificationId from NotificationDismissal d "
                                + "where d.notificationId = n.id and d.userId = ?1) "
                                + "order by n.eventTime desc",
                        userId)
                .list();
        return rows.stream()
                .map(NotificationService::toDto)
                .toList();
    }

    /**
     * The notifications one account should see with an id greater than
     * {@code lastId}, oldest first — the catch-up an event stream sends when a
     * client reconnects with the last id it saw.
     *
     * <p>Ordered by id rather than {@code eventTime} because a webhook may
     * back-date an event; the id is monotonic regardless.
     *
     * @param userId the account reading the feed, from its session
     * @param lastId the highest id the client has already seen
     */
    @Transactional
    public List<NotificationDto> listNewerThan(Long userId, Long lastId) {
        List<Notification> rows = Notification.find(
                        "select n from Notification n where n.id > ?1 and not exists "
                                + "(select d.notificationId from NotificationDismissal d "
                                + "where d.notificationId = n.id and d.userId = ?2) "
                                + "order by n.id asc",
                        lastId, userId)
                .list();
        return rows.stream()
                .map(NotificationService::toDto)
                .toList();
    }

    /**
     * Persists a new notification and returns it.
     *
     * <p>The row is also pushed to any open event streams, once the surrounding
     * transaction has committed, so a client cannot be told about a row that
     * later rolls back. Broadcasting is best-effort: a dead stream never fails
     * the write.
     *
     * @param eventType   {@link EventType#Video}, {@link EventType#Audio} or
     *                    {@link EventType#System}
     * @param description what happened
     * @param videoLink   link to the relevant clip, or {@code null} for audio/system
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
        NotificationDto dto = toDto(notification);
        afterCommit.run(() -> broadcaster.broadcast(dto));
        return dto;
    }

    /**
     * Marks a notification dismissed for one account, so the feed hides it for
     * that account only. Idempotent: dismissing twice is a no-op, not an error.
     *
     * @param id     the notification to dismiss
     * @param userId the account dismissing it, from its session
     * @return {@code false} when the notification itself does not exist
     */
    @Transactional
    public boolean dismiss(Long id, Long userId) {
        if (Notification.findById(id) == null) {
            return false;
        }
        NotificationDismissal.dismiss(id, userId);
        return true;
    }

    /**
     * Marks every notification dismissed for one account, so the feed is empty
     * for that account only. Idempotent.
     *
     * @param userId the account dismissing them, from its session
     */
    @Transactional
    public void dismissAll(Long userId) {
        NotificationDismissal.dismissAll(userId);
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
