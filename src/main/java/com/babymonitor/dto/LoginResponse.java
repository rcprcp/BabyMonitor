package com.babymonitor.dto;

/**
 * Successful login result.
 *
 * @param token     bearer token to send on subsequent requests
 * @param name      the authenticated user's name
 * @param expiresAt ISO-8601 instant when the token stops working
 */
public record LoginResponse(String token, String name, String expiresAt) {
}
