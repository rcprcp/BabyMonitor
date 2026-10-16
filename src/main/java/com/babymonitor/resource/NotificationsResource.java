package com.babymonitor.resource;

import com.babymonitor.auth.AuthFilter;
import com.babymonitor.auth.Session;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.NotificationDto;
import com.babymonitor.entity.User;
import com.babymonitor.service.NotificationBroadcaster;
import com.babymonitor.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Notification endpoints backing the SPA's Notifications page.
 *
 * <ul>
 *   <li>{@code GET    /api/notifications}        -- list (newest first), minus the
 *       ones the calling account has dismissed</li>
 *   <li>{@code GET    /api/notifications/stream} -- live push of new notifications</li>
 *   <li>{@code DELETE /api/notifications/{id}}   -- dismiss for the calling account</li>
 * </ul>
 *
 * <p>Dismissal is per account: it records a row in
 * {@code babymonitor.notification_dismissals} and the notification itself stays
 * put, so another account still sees it and the event stays on record.
 * Transactions are handled by {@link NotificationService}.
 */
@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Notifications", description = "Notification feed for the SPA")
@SecurityRequirement(name = "BearerAuth")
public class NotificationsResource {

    @Inject
    NotificationService notifications;

    @Inject
    NotificationBroadcaster broadcaster;

    @GET
    @Operation(
            summary = "List notifications",
            description = "Newest event first. Notifications the calling account has dismissed "
                    + "are left out; notifications dismissed by other accounts are not.")
    public List<NotificationDto> list(@Context ContainerRequestContext requestContext) {
        User user = currentUser(requestContext);
        return notifications.list(user.id);
    }

    /**
     * Server-sent events of new notifications, for near-real-time alerting.
     *
     * <p>A client reconnecting passes the last event id it saw in the standard
     * {@code Last-Event-ID} header (set automatically by {@code EventSource}) or
     * a {@code lastId} query parameter, and is sent everything newer, in order.
     *
     * <p>{@code EventSource} cannot set an {@code Authorization} header, so
     * {@code AuthFilter} also accepts the session token in a {@code token} query
     * parameter for this one path.
     */
    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Operation(
            summary = "Stream new notifications",
            description = "Server-sent events: one `notification` event per new notification. "
                    + "Pass `lastId` (or the `Last-Event-ID` header) to receive missed events first.")
    public void stream(
            @Context SseEventSink sink,
            @Context Sse sse,
            @HeaderParam("Last-Event-ID") String lastEventId,
            @QueryParam("lastId") Long lastId,
            @Context ContainerRequestContext requestContext) {
        User user = currentUser(requestContext);
        Long last = parseLast(lastId, lastEventId);

        // Register before the catch-up query: a notification committed in
        // between is delivered live rather than lost. It may also appear in the
        // catch-up, which the client de-duplicates by id.
        broadcaster.register(sink, sse);
        if (last != null) {
            broadcaster.replay(sink, notifications.listNewerThan(user.id, last));
        }
    }

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Dismiss a notification",
            description = "Dismisses the notification for the calling account only, which hides it "
                    + "from that account's feed. Idempotent: dismissing it again still returns 204.")
    @APIResponse(
            responseCode = "204",
            description = "Dismissed, or already dismissed by this account")
    @APIResponse(
            responseCode = "404",
            description = "No notification with that id",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response dismiss(@PathParam("id") Long id, @Context ContainerRequestContext requestContext) {
        User user = currentUser(requestContext);
        if (!notifications.dismiss(id, user.id)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("No such notification: " + id))
                    .build();
        }
        return Response.noContent().build();
    }

    /**
     * The account making the request, from the session {@link AuthFilter}
     * attached. Both failures are inconsistencies rather than normal outcomes:
     * the filter turns away an unauthenticated caller before it gets here, and a
     * session is only ever issued to a user row that exists.
     */
    private static User currentUser(ContainerRequestContext requestContext) {
        Session session = (Session) requestContext.getProperty(AuthFilter.SESSION_PROPERTY);
        if (session == null) {
            throw abort(Response.Status.UNAUTHORIZED, "Authentication required");
        }
        User user = User.findByName(session.name());
        if (user == null) {
            throw abort(Response.Status.NOT_FOUND, "User record not found");
        }
        return user;
    }

    /**
     * The last id a reconnecting client has seen, from either the query
     * parameter or the {@code Last-Event-ID} header {@code EventSource} resends.
     * Returns {@code null} (no catch-up) when neither is a valid number.
     */
    private static Long parseLast(Long lastId, String lastEventId) {
        if (lastId != null) {
            return lastId;
        }
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                return Long.parseLong(lastEventId.trim());
            } catch (NumberFormatException ignored) {
                // Malformed header: fall through to no catch-up.
            }
        }
        return null;
    }

    private static WebApplicationException abort(Response.Status status, String message) {
        return new WebApplicationException(Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(message))
                .build());
    }
}
