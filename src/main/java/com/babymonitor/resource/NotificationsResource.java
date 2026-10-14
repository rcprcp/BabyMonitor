package com.babymonitor.resource;

import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.NotificationDto;
import com.babymonitor.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Notification endpoints backing the SPA's Notifications page.
 *
 * <ul>
 *   <li>{@code GET    /api/notifications}           -- list</li>
 *   <li>{@code POST   /api/notifications/{id}/read} -- mark read</li>
 *   <li>{@code DELETE /api/notifications/{id}}      -- dismiss</li>
 * </ul>
 */
@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Notifications", description = "Notification feed for the SPA")
@SecurityRequirement(name = "BearerAuth")
public class NotificationsResource {

    @Inject
    NotificationService notifications;

    @GET
    public List<NotificationDto> list() {
        return notifications.list();
    }

    @POST
    @Path("/{id}/read")
    public Response markRead(@PathParam("id") String id) {
        if (!notifications.markRead(id)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("No such notification: " + id))
                    .build();
        }
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    public Response dismiss(@PathParam("id") String id) {
        if (!notifications.dismiss(id)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("No such notification: " + id))
                    .build();
        }
        return Response.noContent().build();
    }
}
