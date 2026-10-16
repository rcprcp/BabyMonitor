package com.babymonitor.service;

import com.babymonitor.dto.NotificationDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes new notifications to connected event streams, so a parent is alerted
 * as soon as a webhook records an event instead of only when the Notifications
 * page is next refreshed.
 *
 * <p>There is a single application instance, so the set of open
 * {@link SseEventSink}s lives in memory. A sink after which the client vanishes
 * (tab closed, network dropped) is detected either by {@link SseEventSink#isClosed()}
 * or by a failed {@link SseEventSink#send} and dropped from the set; there is no
 * separate reaper.
 *
 * <p>{@link Sse} is a stateless factory, so the instance handed to a stream when
 * it opens is reused to build every event. All sending is serialised on
 * {@code lock} so an event can never interleave with another on the same sink.
 */
@ApplicationScoped
public class NotificationBroadcaster {

    /** Every currently connected stream. Thread-safe, iterated under {@link #lock}. */
    private final Set<SseEventSink> sinks = ConcurrentHashMap.newKeySet();

    /** Serialises registration, replay and broadcast so sends never interleave. */
    private final Object lock = new Object();

    /** Stateless JAX-RS SSE factory, captured from the first stream that opens. */
    private volatile Sse sse;

    /**
     * Registers a stream so it receives future notifications. Called before the
     * catch-up query so that an event committed during the query is never lost:
     * the worst case is that it arrives twice (live and in the catch-up), which
     * the client de-duplicates by notification id.
     */
    public void register(SseEventSink sink, Sse sse) {
        this.sse = sse;
        synchronized (lock) {
            sinks.add(sink);
        }
    }

    /**
     * Sends a stream the notifications it missed while disconnected, oldest
     * first. Called after {@link #register} so a concurrent broadcast can only
     * duplicate an event, never drop one.
     */
    public void replay(SseEventSink sink, List<NotificationDto> backlog) {
        if (backlog.isEmpty()) {
            return;
        }
        synchronized (lock) {
            for (NotificationDto notification : backlog) {
                send(sink, notification);
            }
        }
    }

    /** Pushes one notification to every connected stream. */
    public void broadcast(NotificationDto notification) {
        Sse current = sse;
        if (current == null) {
            return; // nobody is listening
        }
        synchronized (lock) {
            for (SseEventSink sink : sinks) {
                send(sink, notification);
            }
        }
    }

    private void send(SseEventSink sink, NotificationDto notification) {
        if (sink.isClosed()) {
            sinks.remove(sink);
            return;
        }
        try {
            sink.send(sse.newEventBuilder()
                    .id(String.valueOf(notification.id()))
                    .name("notification")
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .data(notification)
                    .build());
        } catch (Exception e) {
            // The client is gone; drop it. A failed stream must never break the
            // caller (a webhook recording an event).
            sinks.remove(sink);
        }
    }
}
