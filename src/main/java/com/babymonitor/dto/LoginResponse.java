package com.babymonitor.dto;

/**
 * Successful login result.
 *
 * @param token      bearer token to send on subsequent requests
 * @param name       the authenticated user's name
 * @param expiresAt  ISO-8601 instant when the token stops working
 * @param theme      the account's stored UI theme, so the SPA can apply it
 *                   without a second round trip
 * @param themeColor  the account's stored accent color, applied alongside it
 * @param promptSound the account's selected notification sound prefix
 * @param activeCamera the user's last viewed camera on the Live Stream page
 */
public record LoginResponse(
        String token, String name, String expiresAt, String theme, String themeColor, String promptSound, String activeCamera) {
}
