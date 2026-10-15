package com.babymonitor.resource;

import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.RecordingDto;
import com.babymonitor.service.RecordingsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Serves the clips Motion records for a camera, so a video notification can link
 * to a recording the app plays back in the browser.
 *
 * <ul>
 *   <li>{@code GET /api/cameras/{n}/recordings}             -- list a camera's clips</li>
 *   <li>{@code GET /api/cameras/{n}/recordings/{fileName}}  -- download one clip</li>
 * </ul>
 *
 * <p>Clips come from the camera's {@code target_dir} row (see
 * {@link RecordingsService}); {@code fileName} must be a plain file name, so a
 * request cannot read outside that directory. Byte ranges are honoured, so a
 * player can seek. Everything sits behind
 * {@link com.babymonitor.auth.AuthFilter}, which is why the Notifications page
 * fetches a clip with its bearer token and plays it inside the app rather than
 * opening the URL in a new tab.
 */
@Path("/api/cameras/{cameraNumber}/recordings")
@Tag(name = "Recordings", description = "Recorded clips, played from the Notifications page")
@SecurityRequirement(name = "BearerAuth")
public class RecordingsResource {

    @Inject
    RecordingsService recordings;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "List a camera's recordings",
            description = "Every clip in the camera's recordings directory, newest first.")
    @APIResponse(
            responseCode = "404",
            description = "No such camera",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response list(@PathParam("cameraNumber") int cameraNumber) {
        java.nio.file.Path directory = recordings.directoryFor(cameraNumber);
        if (directory == null) {
            return notFoundCamera(cameraNumber);
        }
        List<RecordingDto> clips = recordings.list(cameraNumber, directory);
        return Response.ok(clips).build();
    }

    @GET
    @Path("/{fileName}")
    @Operation(
            summary = "Download one recording",
            description = "Streams the clip, honouring a byte Range. The name must be a plain "
                    + "file name inside the camera's recordings directory.")
    @APIResponse(
            responseCode = "206",
            description = "The requested byte range")
    @APIResponse(
            responseCode = "400",
            description = "The name is not a recording",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(
            responseCode = "404",
            description = "No such camera or recording",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(
            responseCode = "416",
            description = "The range does not intersect the file")
    public Response recording(
            @PathParam("cameraNumber") int cameraNumber,
            @PathParam("fileName") String fileName,
            @HeaderParam("Range") String range) {
        java.nio.file.Path directory = recordings.directoryFor(cameraNumber);
        if (directory == null) {
            return notFoundCamera(cameraNumber);
        }

        java.nio.file.Path file;
        try {
            file = recordings.resolve(directory, fileName);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
        if (file == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("No such recording: " + fileName))
                    .build();
        }

        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return serverError(e.getMessage());
        }

        RecordingsService.ByteRange requested;
        try {
            requested = RecordingsService.parseRange(range, size);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + size)
                    .header("Accept-Ranges", "bytes")
                    .build();
        }

        String contentType = contentType(fileName);
        if (requested == null) {
            return Response.ok(file.toFile())
                    .type(contentType)
                    .header("Accept-Ranges", "bytes")
                    .build();
        }

        InputStream slice;
        try {
            slice = RecordingsService.openRange(file, requested);
        } catch (IOException e) {
            return serverError(e.getMessage());
        }
        return Response.status(Response.Status.PARTIAL_CONTENT)
                .entity(slice)
                .type(contentType)
                .header("Content-Range",
                        "bytes " + requested.start() + "-" + requested.end() + "/" + size)
                .header("Content-Length", requested.length())
                .header("Accept-Ranges", "bytes")
                .build();
    }

    private static Response notFoundCamera(int cameraNumber) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("No such camera: " + cameraNumber))
                .build();
    }

    private static Response serverError(String message) {
        return Response.serverError()
                .entity(new ErrorResponse(message))
                .build();
    }

    private static String contentType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) {
            return "video/mp4";
        }
        if (lower.endsWith(".mkv")) {
            return "video/x-matroska";
        }
        if (lower.endsWith(".webm")) {
            return "video/webm";
        }
        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (lower.endsWith(".avi")) {
            return "video/x-msvideo";
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
