package com.babymonitor.resource;

import com.babymonitor.audio.AudioAlertSettings;
import com.babymonitor.dto.AudioAlertConfigDto;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.UpdateAudioAlertClassRequest;
import com.babymonitor.dto.UpdateAudioAlertClassesRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * The Audio Notification Config page's backend: every YAMNet class, its
 * category and its alert switch. The class map is fixed; only the switches are
 * stored, so the page can enable or disable a sound without a rebuild.
 */
@Path("/api/audio/alert-classes")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Audio alerts", description = "Which YAMNet classes may raise an Audio notification")
@SecurityRequirement(name = "BearerAuth")
public class AudioAlertConfigResource {

    @Inject
    AudioAlertSettings settings;

    @GET
    @Operation(summary = "List every YAMNet class and its alert switch")
    public AudioAlertConfigDto config() {
        return settings.config();
    }

    @PUT
    @Path("/{index}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Enable or disable one class")
    @APIResponse(responseCode = "400", description = "No enabled flag supplied",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "No such class index",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response setOne(@PathParam("index") int index, UpdateAudioAlertClassRequest request) {
        if (request == null || request.enabled() == null) {
            return badRequest("An enabled flag is required");
        }
        try {
            settings.setEnabled(index, request.enabled());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
        return Response.noContent().build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Replace the whole alert set",
            description = "The group macro buttons and the reset use this shape: everything not listed is off.")
    @APIResponse(responseCode = "400", description = "No indices supplied",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response setAll(UpdateAudioAlertClassesRequest request) {
        if (request == null || request.indices() == null) {
            return badRequest("indices is required");
        }
        settings.setEnabledIndices(request.indices());
        return Response.noContent().build();
    }

    @POST
    @Path("/reset")
    @Operation(summary = "Restore the shipped default",
            description = "Everything off except the Speech & Human Sounds group (indices 0-23).")
    public AudioAlertConfigDto reset() {
        settings.resetToDefault();
        return settings.config();
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(message))
                .build();
    }
}
