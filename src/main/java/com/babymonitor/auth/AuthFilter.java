package com.babymonitor.auth;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/**
 * Bearer-token gate for the {@code /api} tree.
 *
 * <p>Every {@code /api} request must carry {@code Authorization: Bearer <token>}
 * except the login endpoint. On success the resolved {@link Session} and the raw
 * token are attached to the request context for downstream resources to read.
 *
 * <p>This is a JAX-RS filter, so it only sees REST endpoints -- static SPA
 * assets and the Quarkus dev console are unaffected.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {

    /** Request-context property holding the authenticated {@link Session}. */
    public static final String SESSION_PROPERTY = "babymonitor.session";
    /** Request-context property holding the raw bearer token. */
    public static final String TOKEN_PROPERTY = "babymonitor.token";

    private static final String API_PREFIX = "api/";
    private static final String LOGIN_PATH = "api/auth/login";
    private static final String WEBHOOK_PREFIX = "api/webhooks/";
    private static final String NOTIFICATION_STREAM_PATH = "api/notifications/stream";
    private static final String TOKEN_QUERY_PARAM = "token";
    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    SessionStore sessions;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // RESTEasy Reactive returns the path with a leading slash
        // ("/api/settings"); normalise it so the constants below (which are
        // written without one) match regardless of provider behaviour.
        String path = requestContext.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.startsWith(API_PREFIX)) {
            return; // not our concern (defensive: normally all JAX-RS paths are /api)
        }
        if (LOGIN_PATH.equals(path)) {
            return; // login must be reachable while unauthenticated
        }
        if (path.startsWith(WEBHOOK_PREFIX)) {
            return; // webhooks authenticate with a shared secret, not a session
        }

        String header = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        String token = null;
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            token = header.substring(BEARER_PREFIX.length()).trim();
        }
        // The notification stream is consumed with EventSource, which cannot set
        // an Authorization header, so it also accepts the session token as a
        // query parameter. Restricted to this one path: everywhere else the
        // token stays in the header, out of URLs and access logs.
        if ((token == null || token.isBlank()) && NOTIFICATION_STREAM_PATH.equals(path)) {
            token = requestContext.getUriInfo().getQueryParameters().getFirst(TOKEN_QUERY_PARAM);
        }
        if (token != null && !token.isBlank()) {
            Session session = sessions.validate(token);
            if (session != null) {
                requestContext.setProperty(SESSION_PROPERTY, session);
                requestContext.setProperty(TOKEN_PROPERTY, token);
                return;
            }
        }

        requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "Authentication required"))
                .build());
    }
}
