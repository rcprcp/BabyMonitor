package com.babymonitor.resource;

import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.NotificationDto;
import com.babymonitor.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Webhook receivers for the camera / motion detector.
 *
 * <ul>
 *   <li>{@code POST /api/webhooks/audioNotification}</li>
 *   <li>{@code POST /api/webhooks/videoNotification}</li>
 * </ul>
 *
 * <p>Each call raises a notification that shows up on the Notifications page.
 * The body is treated as an opaque payload: whatever the device sends is
 * summarised into the notification text.
 *
 * <p>These endpoints are exempt from {@link com.babymonitor.auth.AuthFilter}
 * because the caller is a device rather than a signed-in browser. If
 * {@code babymonitor.webhooks.secret} is set, callers must present the same
 * value in the {@code X-Webhook-Secret} header.
 *
 * <p>The {@code GET} variants exist only so a provider can verify the URL is
 * reachable before sending real events.
 */
@Path("/api/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Webhooks", description = "Unauthenticated receivers for device audio/video alerts")
public class WebhookResource {

    private static final int MAX_DETAIL_LENGTH = 300;

    @Inject
    NotificationService notifications;

    @ConfigProperty(name = "babymonitor.webhooks.secret")
    Optional<String> webhookSecret;

    @POST
    @Path("/audioNotification")
    public Response audioNotification(
            @HeaderParam("X-Webhook-Secret") String providedSecret, String body) {
        return record("Audio alert",
                "Audio detected by the baby monitor.",
                providedSecret, body);
    }

    @POST
    @Path("/videoNotification")
    public Response videoNotification(
            @HeaderParam("X-Webhook-Secret") String providedSecret, String body) {
        return record("Video alert",
                "Video/motion detected by the baby monitor.",
                providedSecret, body);
    }

    /* ------------------------------------------------------------------ */
    /* Provider verification hooks: report that the endpoint is reachable. */
    /* ------------------------------------------------------------------ */

    @GET
    @Path("/audioNotification")
    public Response audioNotificationReady() {
        return Response.ok(Map.of("status", "ready", "endpoint", "audioNotification")).build();
    }

    @GET
    @Path("/videoNotification")
    public Response videoNotificationReady() {
        return Response.ok(Map.of("status", "ready", "endpoint", "videoNotification")).build();
    }

    private Response record(String title, String summary, String providedSecret, String body) {
        String requiredSecret = webhookSecret.filter(value -> !value.isBlank()).orElse(null);
        if (requiredSecret != null && !requiredSecret.equals(providedSecret)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Invalid webhook secret"))
                    .build();
        }

        String detail = summarise(body);
        String message = detail.isEmpty() ? summary : summary + " " + detail;
        NotificationDto created = notifications.add(title, message, "warning");
        return Response.accepted(Map.of(
                "status", "accepted",
                "id", created.id(),
                "timestamp", created.timestamp())).build();
    }

    /** Collapses whitespace and truncates an opaque payload for display. */
    private static String summarise(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String collapsed = body.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= MAX_DETAIL_LENGTH
                ? collapsed
                : collapsed.substring(0, MAX_DETAIL_LENGTH) + "…";
    }
}
