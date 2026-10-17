package com.babymonitor.resource;

import com.babymonitor.dto.CameraParameterDto;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.MotionStatusDto;
import com.babymonitor.dto.UpdateCameraParameterRequest;
import com.babymonitor.entity.Camera;
import com.babymonitor.motion.MotionService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
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

/**
 * Read/update access to the {@code babymonitor.cameras} table, plus the Motion
 * process the app runs for each camera.
 *
 * <ul>
 *   <li>{@code GET /api/cameras}                     -- every camera's parameters</li>
 *   <li>{@code GET /api/cameras/{n}}                  -- one camera's parameters</li>
 *   <li>{@code PUT /api/cameras/{n}/parameters/{key}} -- replace a parameter's value</li>
 *   <li>{@code GET /api/cameras/{n}/motion}           -- Motion's state for the camera</li>
 *   <li>{@code POST /api/cameras/{n}/motion/apply}    -- start (or reload) Motion</li>
 *   <li>{@code POST /api/cameras/{n}/motion/stop}     -- stop the camera's Motion</li>
 * </ul>
 *
 * <p>{@code enabled} on a camera's rows is its persisted desired state. Apply
 * sets it (writing every row of the camera together) and starts Motion; Stop
 * clears it and kills the process. The
 * {@link com.babymonitor.motion.MotionSupervisor} then keeps a camera marked
 * running alive, restarting its process if it dies. Editing a parameter is
 * deliberately passive: nothing reaches Motion until {@code motion/apply}. All
 * of it sits behind {@link com.babymonitor.auth.AuthFilter}.
 */
@Path("/api/cameras")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
@Tag(name = "Cameras", description = "Camera parameters, edited from the Camera pages")
@SecurityRequirement(name = "BearerAuth")
public class CamerasResource {

    @Inject
    MotionService motion;

    @GET
    @Operation(
            summary = "List every camera parameter",
            description = "Ordered by camera number, then parameter name.")
    public List<CameraParameterDto> all() {
        return Camera.listAllOrdered().stream()
                .map(CamerasResource::toDto)
                .toList();
    }

    @GET
    @Path("/{cameraNumber}")
    @Operation(
            summary = "List one camera's parameters",
            description = "Ordered by parameter name.")
    @APIResponse(
            responseCode = "404",
            description = "No such camera",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response one(@PathParam("cameraNumber") int cameraNumber) {
        List<Camera> parameters = Camera.listByCamera(cameraNumber);
        if (parameters.isEmpty()) {
            return notFound(cameraNumber);
        }
        return Response.ok(parameters.stream().map(CamerasResource::toDto).toList()).build();
    }

    @PUT
    @Path("/{cameraNumber}/parameters/{key}")
    @Operation(
            summary = "Replace a camera parameter's value",
            description = "Returns the updated parameter. Nothing reaches Motion until "
                    + "POST /api/cameras/{n}/motion/apply.")
    @APIResponse(
            responseCode = "400",
            description = "No value supplied",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(
            responseCode = "404",
            description = "The camera does not define that parameter",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
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

    @GET
    @Path("/{cameraNumber}/motion")
    @Operation(
            summary = "Report a camera's Motion process",
            description = "Whether the app may drive Motion, whether the process is running, its "
                    + "pid and the config file it reads.")
    @APIResponse(
            responseCode = "404",
            description = "No such camera",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response motionStatus(@PathParam("cameraNumber") int cameraNumber) {
        if (Camera.listByCamera(cameraNumber).isEmpty()) {
            return notFound(cameraNumber);
        }
        return Response.ok(motion.status(cameraNumber)).build();
    }

    @POST
    @Path("/{cameraNumber}/motion/apply")
    @Operation(
            summary = "Start (or reload) a camera's Motion",
            description = "Marks the camera as running and rebuilds its config from its "
                    + "parameters. If Motion is already running it is sent SIGHUP to reload; "
                    + "otherwise it is started.")
    @APIResponse(
            responseCode = "400",
            description = "A parameter cannot safely be written",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(
            responseCode = "404",
            description = "No such camera",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response motionApply(@PathParam("cameraNumber") int cameraNumber) {
        List<Camera> parameters = Camera.listByCamera(cameraNumber);
        if (parameters.isEmpty()) {
            return notFound(cameraNumber);
        }
        try {
            MotionStatusDto status =
                    motion.apply(cameraNumber, MotionService.toParameters(parameters));
            // Only once Motion is actually up (or reloaded) does the camera
            // become one the supervisor keeps alive, so a rejected parameter
            // does not leave a camera marked running with no process.
            for (Camera parameter : parameters) {
                parameter.enabled = true;
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
    @Operation(
            summary = "Stop a camera's Motion",
            description = "Marks the camera as not running and kills the process if it is up. "
                    + "The supervisor leaves a stopped camera alone.")
    @APIResponse(
            responseCode = "404",
            description = "No such camera",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response motionStop(@PathParam("cameraNumber") int cameraNumber) {
        List<Camera> parameters = Camera.listByCamera(cameraNumber);
        if (parameters.isEmpty()) {
            return notFound(cameraNumber);
        }
        MotionStatusDto status = motion.stop(cameraNumber);
        // The user has said "do not run this one": record that, so the
        // supervisor does not revive it.
        for (Camera parameter : parameters) {
            parameter.enabled = false;
        }
        return Response.ok(status).build();
    }

    private static Response notFound(int cameraNumber) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("No such camera: " + cameraNumber))
                .build();
    }

    private static CameraParameterDto toDto(Camera parameter) {
        return new CameraParameterDto(
                parameter.cameraNumber,
                parameter.paramKey,
                parameter.value,
                parameter.enabled);
    }
}
