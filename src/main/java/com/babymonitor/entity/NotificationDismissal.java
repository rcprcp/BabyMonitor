package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * One account's dismissal of one notification.
 *
 * <p>Maps the {@code babymonitor.notification_dismissals} table created by
 * {@code db/init.sql}. The (notification, account) pair is the primary key, so
 * an account can dismiss a notification at most once; the feed hides the
 * notifications that have a matching row for the calling account.
 */
@Entity
@Table(name = "notification_dismissals", schema = "babymonitor")
@IdClass(NotificationDismissal.Key.class)
public class NotificationDismissal extends PanacheEntityBase {

    @Id
    @Column(name = "notification_id", nullable = false)
    public Long notificationId;

    @Id
    @Column(name = "user_id", nullable = false)
    public Long userId;

    /**
     * When the account dismissed it. The database owns the value
     * ({@code DEFAULT now()}), so it is read-only here.
     */
    @Column(name = "dismissed_at", nullable = false, insertable = false, updatable = false)
    public Instant dismissedAt;

    public NotificationDismissal() {
    }

    /**
     * Marks one notification dismissed for one account.
     *
     * <p>Written as SQL because there is no JPA way to say "ignore this insert
     * when the pair already exists", and dismissing twice has to stay a no-op
     * (a double click, or two tabs, must not fail).
     */
    public static void dismiss(Long notificationId, Long userId) {
        getEntityManager()
                .createNativeQuery("INSERT INTO babymonitor.notification_dismissals "
                        + "(notification_id, user_id) VALUES (?1, ?2) ON CONFLICT DO NOTHING")
                .setParameter(1, notificationId)
                .setParameter(2, userId)
                .executeUpdate();
    }

    /** Composite key of {@link NotificationDismissal}. */
    public static class Key implements Serializable {

        public Long notificationId;
        public Long userId;

        public Key() {
        }

        public Key(Long notificationId, Long userId) {
            this.notificationId = notificationId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(notificationId, key.notificationId)
                    && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(notificationId, userId);
        }
    }
}
