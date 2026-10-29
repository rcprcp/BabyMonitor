package com.babymonitor.resource;

import com.babymonitor.audio.AudioConfig;
import com.babymonitor.audio.AudioSupervisor;
import com.babymonitor.audio.AudioService;
import com.babymonitor.dto.CameraParameterDto;
import com.babymonitor.dto.CreateCameraParameterRequest;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.MotionStatusDto;
import com.babymonitor.dto.UpdateCameraParameterRequest;
import com.babymonitor.entity.Camera;
import com.babymonitor.entity.CameraAudio;
import com.babymonitor.motion.MotionService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

@Path("/api/cameras")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
@Tag(name = "Cameras", description = "Camera parameters, edited from the Camera pages")
@SecurityRequirement(name = "BearerAuth")
public class CamerasResource {

    private static final Logger LOG = Logger.getLogger(CamerasResource.class);

    @Inject
    MotionService motion;

    @Inject
    AudioSupervisor audio;

    @Inject
    AudioService audioService;

    @Inject
    AudioConfig audioConfig;

    @GET
    @Operation(summary = "List every camera parameter", description = "Ordered by camera number, then parameter name.")
    public List<CameraParameterDto> all() {
        return Camera.listAllOrdered().stream()
                .map(CamerasResource::toDto)
                .toList();
    }

    @GET
    @Path("/{cameraNumber}")
    @Operation(summary = "List one camera's parameters", description = "Ordered by parameter name.")
    @APIResponse(responseCode = "404", description = "No such camera",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response one(@PathParam("cameraNumber") int cameraNumber) {
        List<Camera> parameters = Camera.listByCamera(cameraNumber);
        if (parameters.isEmpty()) {
            return notFound(cameraNumber);
        }
        return Response.ok(parameters.stream().map(CamerasResource::toDto).toList()).build();
    }

    @PUT
    @Path("/{cameraNumber}/parameters/{key}")
    @Operation(summary = "Replace a camera parameter's value",
            description = "Returns the updated parameter.")
    @APIResponse(responseCode = "400", description = "No value supplied",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "The camera does not define that parameter",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateParameter(
            @PathParam("cameraNumber") int cameraNumber,
            @PathParam("key") String key,
            UpdateCameraParameterRequest request) {
        if (request == null || request.value() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("A value is required"))
                    .build();
        }
        Camera parameter = Camera.findOne(cameraNumber, key);
        if (parameter == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("No such parameter: " + key + " on camera " + cameraNumber))
                    .build();
        }
        parameter.value = request.value();
        return Response.ok(toDto(parameter)).build();
    }

    @POST
    @Path("/{cameraNumber}/parameters")
    @Operation(summary = "Add a camera parameter",
            description = "Returns the created parameter.")
    @APIResponse(responseCode = "400", description = "No name or value supplied",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "409", description = "The camera already defines that parameter",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response createParameter(
            @PathParam("cameraNumber") int cameraNumber,
            CreateCameraParameterRequest request) {
        if (request == null || request.key() == null || request.key().isBlank()) {
            return badRequest("A parameter name is required");
        }
        if (request.value() == null || request.value().isBlank()) {
            return badRequest("A value is required");
        }
        String key = request.key().strip();
        if (Camera.findOne(cameraNumber, key) != null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("Camera " + cameraNumber + " already defines " + key))
                    .build();
        }
        Camera parameter = new Camera();
        parameter.cameraNumber = cameraNumber;
        parameter.paramKey = key;
        parameter.value = request.value();
        List<Camera> existing = Camera.listByCamera(cameraNumber);
        parameter.enabled = !existing.isEmpty() && existing.get(0).enabled;
        parameter.persist();
        return Response.status(Response.Status.CREATED).entity(toDto(parameter)).build();
    }

    @DELETE
    @Path("/{cameraNumber}/parameters/{key}")
    @Operation(summary = "Delete a camera parameter",
            description = "Removes the row from the database outright.")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "404", description = "The camera does not define that parameter",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response deleteParameter(
            @PathParam("cameraNumber") int cameraNumber, @PathParam("key") String key) {
        Camera parameter = Camera.findOne(cameraNumber, key);
        if (parameter == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("No such parameter: " + key + " on camera " + cameraNumber))
                    .build();
        }
        parameter.delete();
        return Response.noContent().build();
    }

    @GET
    @Path("/{cameraNumber}/motion")
    @Operation(summary = "Report a camera's Motion process",
            description = "Whether Motion process is running and its config.")
    @APIResponse(responseCode = "404", description = "No such camera",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response motionStatus(@PathParam("cameraNumber") int cameraNumber) {
        List<Camera> parameters = Camera.listByCamera(cameraNumber);
        if (parameters.isEmpty()) {
            return notFound(cameraNumber);
        }
        return Response.ok(motion.status(cameraNumber, MotionService.toParameters(parameters))).build();
    }

    @POST
    @Path("/{cameraNumber}/motion/apply")
    @Operation(summary = "Start (or reload) a camera's Motion",
            description = "Marks camera as running, rebuilds config and starts/reloads Motion.")
    @APIResponse(responseCode = "400", description = "A parameter cannot safely be written",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "No such camera",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response motionApply(@PathParam("cameraNumber") int cameraNumber) {
        List<Camera> parameters = Camera.listByCamera(cameraNumber);
        if (parameters.isEmpty()) {
            return notFound(cameraNumber);
        }
        try {
            MotionStatusDto status =
                    motion.apply(cameraNumber, MotionService.toParameters(parameters));
            for (Camera parameter : parameters) {
                parameter.enabled = true;
            }
            // Audio follows the camera, but "Listen for cries" is the user's
            // preference: a camera started with audio off stays silent. A failure
            // here must not fail the camera, so it is logged and left to
            // AudioSupervisor to retry.
            CameraAudio audioRow = audioRowFor(cameraNumber);
            if (audioRow.enabled) {
                try {
                    audio.ensureRunning(cameraNumber, audioRow.deviceOrDefault(audioConfig.device()));
                } catch (RuntimeException e) {
                    LOG.warnf("Could not start audio for camera %d: %s", cameraNumber, e.getMessage());
                }
            }
            return Response.ok(status).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (RuntimeException e) {
            return Response.serverError()
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/{cameraNumber}/motion/stop")
    @Operation(summary = "Stop a camera's Motion",
            description = "Marks camera as not running and kills processes.")
    @APIResponse(responseCode = "404", description = "No such camera",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response motionStop(@PathParam("cameraNumber") int cameraNumber) {
        List<Camera> parameters = Camera.listByCamera(cameraNumber);
        if (parameters.isEmpty()) {
            return notFound(cameraNumber);
        }
        MotionStatusDto status = motion.stop(cameraNumber, MotionService.toParameters(parameters));
        audio.stop(cameraNumber);
        for (Camera parameter : parameters) {
            parameter.enabled = false;
        }
        // camera_audio.enabled is the "Listen for cries" preference, not the run
        // state: stopping the camera must not forget that the user wants audio
        // when it is on again. Cameras start again at the next boot anyway.
        return Response.ok(status).build();
    }

    @GET
    @Path("/{cameraNumber}/audio")
    @Operation(summary = "Report a camera's audio capture",
            description = "Whether the capture process is running, its pid, device, and classification stats.")
    @APIResponse(responseCode = "404", description = "No such camera",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response audioStatus(@PathParam("cameraNumber") int cameraNumber) {
        if (Camera.listByCamera(cameraNumber).isEmpty()) {
            return notFound(cameraNumber);
        }
        CameraAudio row = CameraAudio.findByCamera(cameraNumber);
        return Response.ok(audioService.status(cameraNumber, row != null && row.enabled)).build();
    }

    @POST
    @Path("/{cameraNumber}/audio/start")
    @Operation(summary = "Start a camera's audio capture",
            description = "Turns the camera's audio on and starts (or leaves running) its capture. "
                    + "A failure does not turn it back off, so the supervisor keeps retrying.")
    @APIResponse(responseCode = "404", description = "No such camera",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response audioStart(@PathParam("cameraNumber") int cameraNumber) {
        if (Camera.listByCamera(cameraNumber).isEmpty()) {
            return notFound(cameraNumber);
        }
        CameraAudio row = audioRowFor(cameraNumber);
        row.enabled = true;
        try {
            audio.ensureRunning(cameraNumber, row.deviceOrDefault(audioConfig.device()));
        } catch (RuntimeException e) {
            // Desired state stays on: AudioSupervisor retries with backoff, and
            // the status below reports the capture as down until it succeeds.
            LOG.warnf("Could not start audio for camera %d: %s", cameraNumber, e.getMessage());
        }
        return Response.ok(audioService.status(cameraNumber, true)).build();
    }

    @POST
    @Path("/{cameraNumber}/audio/stop")
    @Operation(summary = "Stop a camera's audio capture",
            description = "Turns the camera's audio off and kills its capture process.")
    @APIResponse(responseCode = "404", description = "No such camera",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response audioStop(@PathParam("cameraNumber") int cameraNumber) {
        if (Camera.listByCamera(cameraNumber).isEmpty()) {
            return notFound(cameraNumber);
        }
        CameraAudio row = audioRowFor(cameraNumber);
        row.enabled = false;
        return Response.ok(audioService.stop(cameraNumber)).build();
    }

    /**
     * The camera's {@code camera_audio} row, created with audio on (the default
     * preference) on first use. {@code enabled} is the user's "Listen for cries"
     * preference: it survives a Stop and is applied the next time the camera
     * starts.
     */
    private static CameraAudio audioRowFor(int cameraNumber) {
        CameraAudio row = CameraAudio.findByCamera(cameraNumber);
        if (row == null) {
            row = new CameraAudio();
            row.cameraNumber = cameraNumber;
            row.enabled = true;
            row.persist();
        }
        return row;
    }

    private static Response notFound(int cameraNumber) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("No such camera: " + cameraNumber))
                .build();
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(message))
                .build();
    }

    private static CameraParameterDto toDto(Camera parameter) {
        return new CameraParameterDto(parameter.cameraNumber, parameter.paramKey, parameter.value, parameter.enabled);
    }
}
