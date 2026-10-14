package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A notification raised by a device webhook.
 *
 * <p>Maps the {@code babymonitor.notifications} table created by
 * {@code db/init.sql} and backs the Notifications page.
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

    @Column(name = "description", length = 500, nullable = false)
    public String description;

    /** Link to the relevant video clip; {@code null} for audio-only events. */
    @Column(name = "video_link", length = 500)
    public String videoLink;

    /** {@code info}, {@code warning} or {@code critical}. */
    @Column(name = "severity", length = 20, nullable = false)
    public String severity = "warning";

    @Column(name = "read", nullable = false)
    public boolean read;

    public Notification() {
    }

    public Notification(Instant eventTime, String description, String videoLink, String severity) {
        this.eventTime = eventTime;
        this.description = description;
        this.videoLink = videoLink;
        this.severity = severity;
    }
}
