package com.babymonitor.resource;

import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.NotificationDto;
import com.babymonitor.dto.NotificationRequest;
import com.babymonitor.entity.EventType;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Webhook receivers for the camera / motion detector.
 *
 * <ul>
 *   <li>{@code POST /api/webhooks/audioNotification} -- stores an {@code Audio} event</li>
 *   <li>{@code POST /api/webhooks/videoNotification} -- stores a {@code Video} event</li>
 * </ul>
 *
 * <p>The body is a {@link NotificationRequest}: an event time, a description and
 * (for video) a link to the relevant clip. The event type is taken from the
 * endpoint, not the payload, so it can never disagree with the URL. Both
 * endpoints persist a row in {@code babymonitor.notifications}, which the
 * Notifications page lists.
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

    @Inject
    NotificationService notifications;

    @ConfigProperty(name = "babymonitor.webhooks.secret")
    Optional<String> webhookSecret;

    @POST
    @Path("/videoNotification")
    @Operation(
            summary = "Receive a video alert",
            description = "Stores an event_type=Video row with the description and the link to the "
                    + "relevant video. Returns 400 when either is missing.")
    public Response videoNotification(
            @HeaderParam("X-Webhook-Secret") String providedSecret, NotificationRequest request) {
        return record(providedSecret, request, EventType.Video);
    }

    @POST
    @Path("/audioNotification")
    @Operation(
            summary = "Receive an audio alert",
            description = "Stores an event_type=Audio row with the description. Audio events have "
                    + "no video, so any videoLink in the payload is ignored.")
    public Response audioNotification(
            @HeaderParam("X-Webhook-Secret") String providedSecret, NotificationRequest request) {
        return record(providedSecret, request, EventType.Audio);
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

    private Response record(String providedSecret, NotificationRequest request, EventType eventType) {
        String requiredSecret = webhookSecret.filter(value -> !value.isBlank()).orElse(null);
        if (requiredSecret != null && !requiredSecret.equals(providedSecret)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Invalid webhook secret"))
                    .build();
        }
        if (request == null || isBlank(request.description())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("description is required"))
                    .build();
        }
        if (eventType == EventType.Video && isBlank(request.videoLink())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("videoLink is required for videoNotification"))
                    .build();
        }

        NotificationDto created = notifications.add(
                eventType,
                request.description().trim(),
                eventType == EventType.Video ? blankToNull(request.videoLink()) : null,
                request.eventTime());
        return Response.accepted(Map.of(
                "status", "accepted",
                "id", created.id(),
                "eventType", created.eventType(),
                "eventTime", created.eventTime())).build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
