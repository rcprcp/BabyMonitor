package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A notification raised by a device webhook.
 *
 * <p>Maps the {@code babymonitor.notifications} table created by the Flyway
 * migrations and backs the Notifications page.
 */
@Entity
@Table(name = "notifications", schema = "babymonitor")
public class Notification extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    /** When the event happened (not when the row was written). */
    @Column(name = "event_time", nullable = false)
    public Instant eventTime;

    /**
     * {@link EventType#Video} for clip alerts, {@link EventType#Audio} for
     * sound alerts, {@link EventType#System} for app health alerts and
     * {@link EventType#Environment} for nursery threshold alerts.
     *
     * <p>{@code NAMED_ENUM} tells Hibernate to bind the value as the native
     * PostgreSQL enum type named by {@code columnDefinition}, rather than as a
     * plain varchar (which PostgreSQL would refuse to compare with the enum).
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "event_type", columnDefinition = "babymonitor.event_type", nullable = false)
    public EventType eventType;

    @Column(name = "description", length = 500, nullable = false)
    public String description;

    /** Link to the relevant video clip; {@code null} for audio and system events. */
    @Column(name = "video_link", length = 500)
    public String videoLink;

    public Notification() {
    }

    public Notification(EventType eventType, Instant eventTime, String description, String videoLink) {
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.description = description;
        this.videoLink = videoLink;
    }
}
