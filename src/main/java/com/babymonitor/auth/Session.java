package com.babymonitor.auth;

import java.time.Instant;

/**
 * An issued login session.
 *
 * @param token     opaque bearer token handed to the client
 * @param name      the authenticated user's name
 * @param expiresAt instant after which {@link SessionStore} rejects the token
 */
public record Session(String token, String name, Instant expiresAt) {
}
