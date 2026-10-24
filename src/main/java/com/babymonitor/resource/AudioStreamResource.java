package com.babymonitor.resource;

import com.babymonitor.audio.AudioBroadcaster;
import com.babymonitor.audio.AudioConfig;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.entity.Camera;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Streams one camera's captured audio to the browser.
 *
 * <p>This is the "hear the baby" half of the audio pipeline: the same raw PCM
 * that feeds YAMNet is fanned out to listeners as signed 16-bit little-endian
 * mono at {@code babymonitor.audio.sample-rate}. The response is a chunked
 * stream with no length, so the client reads it incrementally and plays it with
 * the Web Audio API. It lives outside {@link CamerasResource}'s class-level
 * transaction on purpose: a listener may stay connected for hours.</p>
 *
 * <p>The bearer token travels in the normal {@code Authorization} header (the
 * browser fetches the stream rather than pointing an {@code <audio>} element at
 * it), so no token ever appears in a URL.</p>
 */
@Path("/api/cameras")
@Tag(name = "Cameras", description = "Camera parameters, edited from the Camera pages")
@SecurityRequirement(name = "BearerAuth")
public class AudioStreamResource {

    @Inject
    AudioBroadcaster broadcaster;

    @Inject
    AudioConfig config;

    @GET
    @Path("/{cameraNumber}/audio/stream")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Blocking
    @Operation(summary = "Stream a camera's live audio",
            description = "Endless chunked response of raw signed 16-bit little-endian mono PCM "
                    + "at the configured sample rate. The sample rate and channel count are repeated "
                    + "in the X-Audio-Sample-Rate and X-Audio-Channels headers.")
    @APIResponse(responseCode = "404", description = "No such camera",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response stream(@PathParam("cameraNumber") int cameraNumber) {
        if (cameraMissing(cameraNumber)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse("No such camera: " + cameraNumber))
                    .build();
        }
        AudioBroadcaster.Subscriber subscriber = broadcaster.subscribe(cameraNumber);
        StreamingOutput body = output -> {
            try {
                byte[] chunk;
                while ((chunk = subscriber.next()) != null) {
                    output.write(chunk);
                    output.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                broadcaster.unsubscribe(cameraNumber, subscriber);
            }
        };
        return Response.ok(body)
                .type("audio/L16;rate=" + config.sampleRate() + ";channels=1")
                .header("X-Audio-Sample-Rate", config.sampleRate())
                .header("X-Audio-Channels", 1)
                .header("Cache-Control", "no-store")
                .build();
    }

    private static boolean cameraMissing(int cameraNumber) {
        return QuarkusTransaction.requiringNew()
                .call(() -> Camera.listByCamera(cameraNumber).isEmpty());
    }
}
