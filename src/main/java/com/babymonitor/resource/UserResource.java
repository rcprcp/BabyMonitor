package com.babymonitor.resource;

import com.babymonitor.auth.AuthFilter;
import com.babymonitor.auth.Session;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.UserDto;
import com.babymonitor.entity.User;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * The signed-in user's own account record.
 *
 * <ul>
 *   <li>{@code GET /api/users/me} -- read the current user's row from the DB</li>
 * </ul>
 *
 * <p>Backs the "Personal settings" page. The row is re-read on every request
 * (rather than echoed from the session) so it always reflects the database.
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Transactional // Panache needs an active transaction to run the lookup
@Tag(name = "Users", description = "The signed-in user's own account record")
@SecurityRequirement(name = "BearerAuth")
public class UserResource {

    @GET
    @Path("/me")
    public Response me(@Context ContainerRequestContext requestContext) {
        Session session = (Session) requestContext.getProperty(AuthFilter.SESSION_PROPERTY);
        User user = User.findByName(session.name());
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("User record not found"))
                    .build();
        }
        return Response.ok(new UserDto(user.id, user.name)).build();
    }
}
