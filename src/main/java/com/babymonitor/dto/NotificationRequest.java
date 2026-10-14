package com.babymonitor.dto;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Payload posted to the alert webhooks.
 *
 * <p>This is the OpenAPI schema for {@code POST /api/webhooks/videoNotification}
 * (and, minus the video link, {@code POST /api/webhooks/audioNotification}).
 *
 * @param eventTime   when the event happened; defaults to "now" when omitted
 * @param description what happened (required)
 * @param videoLink   link to the relevant video clip; required for the video
 *                    webhook, omitted for audio-only events
 */
@Schema(
        name = "NotificationRequest",
        description = "Alert posted by the camera / motion detector. Persisted to "
                + "babymonitor.notifications and shown on the Notifications page.")
public record NotificationRequest(
        @Schema(
                description = "When the event happened (ISO-8601). Defaults to the time the webhook is received.",
                examples = {"2026-10-14T20:15:00Z"})
        Instant eventTime,

        @Schema(
                description = "Human-readable description of what happened.",
                required = true,
                examples = {"Motion detected in the crib"})
        String description,

        @Schema(
                description = "Link to the relevant video clip. Required for videoNotification; "
                        + "omit it for audioNotification.",
                examples = {"http://192.168.0.2:8081/recordings/motion-2026-10-14.mp4"})
        String videoLink) {
}
