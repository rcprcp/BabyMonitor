package com.babymonitor.resource;

import com.babymonitor.entity.Setting;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * The few values the login screen needs before anyone is signed in.
 *
 * <p>Only the monitor's display name lives here: it is public by design (it is
 * painted on the sign-in page), and nothing else may be added without a separate
 * decision. The bearer gate exempts {@code /api/public/}.</p>
 */
@Path("/api/public")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Public", description = "Values available before sign-in")
public class PublicResource {

    /** The {@code systemName} setting, or the app's own name when unset. */
    @GET
    @Path("/system-name")
    @Transactional
    public Map<String, String> systemName() {
        Setting setting = Setting.findByTag("systemName");
        String name = setting == null || setting.value.isBlank() ? "BabyMonitor" : setting.value;
        return Map.of("name", name);
    }
}
