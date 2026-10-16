package com.babymonitor.resource;

import com.babymonitor.auth.AuthFilter;
import com.babymonitor.auth.Session;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.UpdatePromptSoundRequest;
import com.babymonitor.dto.UpdateThemeColorRequest;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * The signed-in user's own account record.
 *
 * <ul>
 *   <li>{@code GET /api/users/me}             -- read the current user's row</li>
 *   <li>{@code PUT /api/users/me/theme}       -- store light/dark/auto</li>
 *   <li>{@code PUT /api/users/me/theme-color} -- store the accent color</li>
 *   <li>{@code PUT /api/users/me/prompt-sound}-- store the alert sound prefix</li>
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

    /**
     * Accepted values of the {@code theme_color} column: Mantine's default
     * palette (https://mantine.dev/theming/colors/) in documentation order,
     * minus {@code dark}, which is Mantine's surface color rather than an
     * accent. Kept ordered so the error message reads sensibly, and in step with
     * {@code PALETTE} in the SPA.
     */
    private static final List<String> THEME_COLORS = List.of(
            "gray", "red", "pink", "grape", "violet", "indigo", "blue", "cyan",
            "teal", "green", "lime", "yellow", "orange");

    /** Accepted prefix names for notification sounds: files in {@code /sounds/}. */
    private static final List<String> SOUND_NAMES = List.of(
            "chime", "beep", "chirp", "ding", "doorbell", "soft", "alarm-loud");

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
        String value = normalise(request == null ? null : request.theme());
        if (value == null || !THEMES.contains(value)) {
            return badRequest("theme must be one of: light, dark, auto");
        }

        User user = currentUser(requestContext);
        if (user == null) {
            return notFound();
        }
        user.theme = value; // dirty checking writes it back inside the transaction
        return Response.ok(dto(user)).build();
    }

    @PUT
    @Path("/me/theme-color")
    public Response updateThemeColor(
            UpdateThemeColorRequest request, @Context ContainerRequestContext requestContext) {
        String value = normalise(request == null ? null : request.themeColor());
        if (value == null || !THEME_COLORS.contains(value)) {
            return badRequest("themeColor must be one of: " + String.join(", ", THEME_COLORS));
        }

        User user = currentUser(requestContext);
        if (user == null) {
            return notFound();
        }
        user.themeColor = value;
        return Response.ok(dto(user)).build();
    }

    @PUT
    @Path("/me/prompt-sound")
    public Response updatePromptSound(
            UpdatePromptSoundRequest request, @Context ContainerRequestContext requestContext) {
        String value = normalise(request == null ? null : request.promptSound());
        if (value == null || !SOUND_NAMES.contains(value)) {
            return badRequest("promptSound must be one of: " + String.join(", ", SOUND_NAMES));
        }

        User user = currentUser(requestContext);
        if (user == null) {
            return notFound();
        }
        user.promptSound = value;
        return Response.ok(dto(user)).build();
    }

    private static String normalise(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static User currentUser(ContainerRequestContext requestContext) {
        Session session = (Session) requestContext.getProperty(AuthFilter.SESSION_PROPERTY);
        return User.findByName(session.name());
    }

    private static UserDto dto(User user) {
        return new UserDto(user.id, user.name, user.theme, user.themeColor, user.promptSound);
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse(message)).build();
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("User record not found"))
                .build();
    }
}
