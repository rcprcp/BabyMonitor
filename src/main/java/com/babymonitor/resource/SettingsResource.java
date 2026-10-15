package com.babymonitor.resource;

import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.SettingDto;
import com.babymonitor.dto.UpdateSettingRequest;
import com.babymonitor.entity.Setting;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Read/update access to the {@code babymonitor.settings} tag/value table.
 *
 * <ul>
 *   <li>{@code GET /api/settings}         -- all settings</li>
 *   <li>{@code GET /api/settings/{tag}}   -- one setting (e.g. {@code systemName})</li>
 *   <li>{@code PUT /api/settings/{tag}}   -- replace a setting's value</li>
 * </ul>
 *
 * <p>All of it sits behind {@link com.babymonitor.auth.AuthFilter}.
 */
@Path("/api/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
@Tag(name = "Settings", description = "Read and update application settings (tag/value)")
@SecurityRequirement(name = "BearerAuth")
public class SettingsResource {

    @GET
    public List<SettingDto> all() {
        return Setting.<Setting>listAll().stream()
                .sorted((a, b) -> a.tag.compareTo(b.tag))
                .map(s -> new SettingDto(s.tag, s.value))
                .toList();
    }

    @GET
    @Path("/{tag}")
    public Response one(@PathParam("tag") String tag) {
        Setting setting = Setting.findByTag(tag);
        if (setting == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("No such setting: " + tag))
                    .build();
        }
        return Response.ok(new SettingDto(setting.tag, setting.value)).build();
    }

    @PUT
    @Path("/{tag}")
    public Response update(@PathParam("tag") String tag, UpdateSettingRequest request) {
        if (request == null || request.value() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("A value is required"))
                    .build();
        }
        Setting setting = Setting.findByTag(tag);
        if (setting == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("No such setting: " + tag))
                    .build();
        }
        setting.value = request.value();
        return Response.ok(new SettingDto(setting.tag, setting.value)).build();
    }
}
