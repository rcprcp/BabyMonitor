package com.babymonitor.web;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Serves {@code index.html} for client-side routes so that a browser refresh on
 * e.g. {@code /settings} still boots the SPA.
 *
 * <p>Quarkus 3.40 has no built-in SPA-fallback option (the documented
 * {@code quarkus.http.spa-fallback-index} key does not exist), and a route
 * registered after the default routes is never reached because Jakarta REST
 * answers unmatched paths with its own 404. Registering a Vert.x <em>filter</em>
 * instead runs before every route, so it can rewrite the request via
 * {@link RoutingContext#reroute(String)}.
 *
 * <p>Only extension-less GET/HEAD requests outside {@code /api} and {@code /q}
 * are rewritten; real files and API calls pass straight through.
 */
@ApplicationScoped
public class SpaFallbackFilter {

    /** Runs early, before the routing table. */
    private static final int PRIORITY = 1_000;

    private static final String INDEX = "/index.html";

    void register(@Observes Filters filters) {
        filters.register(this::handle, PRIORITY);
    }

    private void handle(RoutingContext routingContext) {
        HttpMethod method = routingContext.request().method();
        if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
            routingContext.next();
            return;
        }

        String path = routingContext.normalizedPath();
        if (path == null || path.isEmpty() || "/".equals(path) || INDEX.equals(path)) {
            routingContext.next();
            return;
        }

        // API and the Quarkus dev console are not the SPA.
        if (path.startsWith("/api/") || "/api".equals(path) || path.startsWith("/q/") || "/q".equals(path)) {
            routingContext.next();
            return;
        }

        // Anything with a file extension is a real static asset.
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        if (lastSegment.indexOf('.') >= 0) {
            routingContext.next();
            return;
        }

        routingContext.reroute(INDEX);
    }
}
