package com.babymonitor.resource;

import com.babymonitor.auth.AuthFilter;
import com.babymonitor.auth.Session;
import com.babymonitor.auth.SessionStore;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.LoginRequest;
import com.babymonitor.dto.LoginResponse;
import com.babymonitor.entity.User;
import com.babymonitor.auth.PasswordHasher;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Login endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/auth/login}  -- exchange name+password for a token</li>
 *   <li>{@code POST /api/auth/logout} -- discard the current token</li>
 *   <li>{@code GET  /api/auth/me}     -- describe the current session</li>
 * </ul>
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "Login, logout and current-session endpoints")
@SecurityScheme(
        securitySchemeName = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "opaque-token",
        description = "Token returned by POST /api/auth/login. Send as 'Authorization: Bearer <token>'.")
public class AuthResource {

    @Inject
    SessionStore sessions;

    @POST
    @Path("/login")
    @Transactional // Panache needs an active transaction to run the lookup
    public Response login(LoginRequest request) {
        if (request == null || isBlank(request.name()) || isBlank(request.password())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Name and password are required"))
                    .build();
        }

        User user = User.findByName(request.name().trim());
        if (user == null || !PasswordHasher.matches(request.password(), user.passwordHash)) {
            // Same response for "no such user" and "wrong password" so the
            // endpoint cannot be used to enumerate account names.
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Invalid Name or Password."))
                    .build();
        }

        Session session = sessions.create(user.name);
        return Response.ok(new LoginResponse(session.token(), user.name, session.expiresAt().toString()))
                .build();
    }

    @POST
    @Path("/logout")
    @SecurityRequirement(name = "BearerAuth")
    public Response logout(@Context ContainerRequestContext requestContext) {
        String token = (String) requestContext.getProperty(AuthFilter.TOKEN_PROPERTY);
        sessions.invalidate(token);
        return Response.noContent().build();
    }

    @GET
    @Path("/me")
    @SecurityRequirement(name = "BearerAuth")
    public Response me(@Context ContainerRequestContext requestContext) {
        Session session = (Session) requestContext.getProperty(AuthFilter.SESSION_PROPERTY);
        return Response.ok(Map.of("name", session.name())).build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
