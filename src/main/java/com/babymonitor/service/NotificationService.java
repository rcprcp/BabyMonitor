package com.babymonitor.service;

import com.babymonitor.dto.NotificationDto;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory notification feed.
 *
 * <p>There is intentionally no notifications table yet, so the list is seeded
 * once per application start and lives in memory. Swapping this for a Panache
 * entity later only changes this class; the resource contract stays the same.
 */
@ApplicationScoped
public class NotificationService {

    /** Mutable entry backing an immutable {@link NotificationDto}. */
    private static final class Entry {
        final String id = UUID.randomUUID().toString();
        final String title;
        final String message;
        final String severity;
        final Instant timestamp;
        volatile boolean read;

        Entry(String title, String message, String severity, Instant timestamp, boolean read) {
            this.title = title;
            this.message = message;
            this.severity = severity;
            this.timestamp = timestamp;
            this.read = read;
        }

        NotificationDto toDto() {
            return new NotificationDto(id, title, message, severity, timestamp.toString(), read);
        }
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>(List.of(
            new Entry("Stream went offline", "No frames received from the camera for 2 minutes.",
                    "critical", Instant.now().minusSeconds(180), false),
            new Entry("Motion detected", "Movement detected in the crib at 02:14.",
                    "warning", Instant.now().minusSeconds(2_400), false),
            new Entry("Nightly recording saved", "The 8-hour recording finished and was stored.",
                    "info", Instant.now().minusSeconds(28_800), true)));

    /** All notifications, newest first. */
    public List<NotificationDto> list() {
        return entries.stream()
                .sorted((a, b) -> b.timestamp.compareTo(a.timestamp))
                .map(Entry::toDto)
                .toList();
    }

    /**
     * Records a new notification and returns it.
     *
     * @param title    short headline
     * @param message  longer body text
     * @param severity {@code info}, {@code warning} or {@code critical}
     */
    public NotificationDto add(String title, String message, String severity) {
        Entry entry = new Entry(title, message, severity, Instant.now(), false);
        entries.add(entry);
        return entry.toDto();
    }

    /** Marks one notification read. Returns {@code false} when the id is unknown. */
    public boolean markRead(String id) {
        Optional<Entry> entry = find(id);
        entry.ifPresent(e -> e.read = true);
        return entry.isPresent();
    }

    /** Removes one notification. Returns {@code false} when the id is unknown. */
    public boolean dismiss(String id) {
        return entries.removeIf(e -> e.id.equals(id));
    }

    private Optional<Entry> find(String id) {
        return entries.stream().filter(e -> e.id.equals(id)).findFirst();
    }
}
