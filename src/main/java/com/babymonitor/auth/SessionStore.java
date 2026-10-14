package com.babymonitor.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * In-memory store of active login sessions.
 *
 * <p>Tokens are random UUIDs held in a concurrent map and disappear on
 * restart. That is enough for a single-instance app; a clustered or
 * long-lived deployment would move this to a database or Redis.
 */
@ApplicationScoped
public class SessionStore {

    private final java.util.concurrent.ConcurrentHashMap<String, Session> sessions =
            new java.util.concurrent.ConcurrentHashMap<>();

    @ConfigProperty(name = "babymonitor.auth.session-ttl", defaultValue = "PT12H")
    java.time.Duration sessionTtl;

    /** Creates and records a new session for {@code name}. */
    public Session create(String name) {
        Instant expiresAt = Instant.now().plus(sessionTtl);
        Session session = new Session(UUID.randomUUID().toString(), name, expiresAt);
        sessions.put(session.token(), session);
        return session;
    }

    /**
     * Resolves a token to its session.
     *
     * @return the live session, or {@code null} when the token is unknown or
     *         expired (expired sessions are evicted as a side effect)
     */
    public Session validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    /** Drops a session, e.g. on logout. */
    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
