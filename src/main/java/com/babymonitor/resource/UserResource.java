package com.babymonitor.resource;

import com.babymonitor.auth.AuthFilter;
import com.babymonitor.auth.Session;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.UpdateThemeRequest;
import com.babymonitor.dto.UserDto;
import com.babymonitor.entity.User;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * The signed-in user's own account record.
 *
 * <ul>
 *   <li>{@code GET /api/users/me}       -- read the current user's row</li>
 *   <li>{@code PUT /api/users/me/theme} -- store the light/dark/auto preference</li>
 * </ul>
 *
 * <p>Backs the "Personal settings" page. The row is re-read on every request
 * (rather than echoed from the session) so it always reflects the database.
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional // Panache needs an active transaction to run the lookup
@Tag(name = "Users", description = "The signed-in user's own account record")
@SecurityRequirement(name = "BearerAuth")
public class UserResource {

    /** Accepted values of the {@code theme} column; they match the SPA's choices. */
    private static final Set<String> THEMES = Set.of("light", "dark", "auto");

    @GET
    @Path("/me")
    public Response me(@Context ContainerRequestContext requestContext) {
        User user = currentUser(requestContext);
        if (user == null) {
            return notFound();
        }
        return Response.ok(dto(user)).build();
    }

    @PUT
    @Path("/me/theme")
    public Response updateTheme(
            UpdateThemeRequest request, @Context ContainerRequestContext requestContext) {
        String value = request == null || request.theme() == null
                ? null
                : request.theme().trim().toLowerCase(Locale.ROOT);
        if (value == null || !THEMES.contains(value)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("theme must be one of: light, dark, auto"))
                    .build();
        }

        User user = currentUser(requestContext);
        if (user == null) {
            return notFound();
        }
        user.theme = value; // dirty checking writes it back inside the transaction
        return Response.ok(dto(user)).build();
    }

    private static User currentUser(ContainerRequestContext requestContext) {
        Session session = (Session) requestContext.getProperty(AuthFilter.SESSION_PROPERTY);
        return User.findByName(session.name());
    }

    private static UserDto dto(User user) {
        return new UserDto(user.id, user.name, user.theme);
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("User record not found"))
                .build();
    }
}
