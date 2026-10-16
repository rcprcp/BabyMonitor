package com.babymonitor.resource;

import com.babymonitor.dto.CameraParameterDto;
import com.babymonitor.dto.CreateCameraParameterRequest;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.UpdateCameraEnabledRequest;
import com.babymonitor.dto.UpdateCameraParameterRequest;
import com.babymonitor.entity.Camera;
import com.babymonitor.motion.MotionService;
import com.babymonitor.service.AfterCommit;
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

/**
 * Read/update access to the {@code babymonitor.cameras} table, plus the Motion
 * process the app runs for each camera.
 *
 * <ul>
 *   <li>{@code GET /api/cameras}                          -- every camera's parameters</li>
 *   <li>{@code GET /api/cameras/{cameraNumber}}            -- one camera's parameters</li>
 *   <li>{@code POST /api/cameras/{n}/parameters}           -- add a parameter</li>
 *   <li>{@code PUT /api/cameras/{n}/parameters/{key}}      -- replace a parameter's value</li>
 *   <li>{@code DELETE /api/cameras/{n}/parameters/{key}}   -- remove a parameter</li>
 *   <li>{@code PUT /api/cameras/{n}/enabled}               -- switch a camera on or off</li>
 *   <li>{@code GET /api/cameras/{n}/motion}                -- Motion's state for the camera</li>
 *   <li>{@code POST /api/cameras/{n}/motion/apply}         -- rebuild the config and (re)start Motion</li>
 *   <li>{@code POST /api/cameras/{n}/motion/stop}          -- stop the camera's Motion</li>
 * </ul>
 *
 * <p>Because {@code enabled} is stored on every row of a camera, switching it
 * updates all of that camera's rows together, and the process is started or
 * killed once the write has committed. Adding, editing or removing a parameter
 * is deliberately passive: nothing reaches Motion until {@code motion/apply}. All of it sits
 * behind {@link com.babymonitor.auth.AuthFilter}.
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

    @Inject
    AfterCommit afterCommit;

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

    @POST
    @Path("/{cameraNumber}/parameters")
    @Operation(
            summary = "Add a camera parameter",
            description = "Returns the created parameter. Adding a row is passive, so "
                    + "nothing reaches Motion until POST /api/cameras/{n}/motion/apply.")
    @APIResponse(responseCode = "400", description = "No name or value supplied")
    @APIResponse(
            responseCode = "409",
            description = "The camera already defines that parameter",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
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
        // `enabled` is the camera's own flag, repeated on every row, so a new row
        // joins the camera in whatever state it is already in.
        List<Camera> existing = Camera.listByCamera(cameraNumber);
        parameter.enabled = !existing.isEmpty() && existing.get(0).enabled;
        parameter.persist();
        return Response.status(Response.Status.CREATED).entity(toDto(parameter)).build();
    }

    @DELETE
    @Path("/{cameraNumber}/parameters/{key}")
    @Operation(
            summary = "Remove a camera parameter",
            description = "Returns nothing. Removing a row is passive, so nothing reaches "
                    + "Motion until POST /api/cameras/{n}/motion/apply.")
    @APIResponse(responseCode = "204", description = "The parameter was removed")
    @APIResponse(
            responseCode = "404",
            description = "The camera does not define that parameter",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
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

    @PUT
    @Path("/{cameraNumber}/enabled")
    @Operation(
            summary = "Switch a camera on or off",
            description = "Applies to every parameter row of the camera and returns them all. "
                    + "Switching on starts Motion for the camera; switching off kills it. The "
                    + "process is only touched once the write has committed.")
    @APIResponse(
            responseCode = "400",
            description = "No enabled flag supplied",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(
            responseCode = "404",
            description = "No such camera",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateEnabled(
            @PathParam("cameraNumber") int cameraNumber,
            UpdateCameraEnabledRequest request) {
        if (request == null || request.enabled() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("An enabled flag is required"))
                    .build();
        }
        List<Camera> parameters = Camera.listByCamera(cameraNumber);
        if (parameters.isEmpty()) {
            return notFound(cameraNumber);
        }
        for (Camera parameter : parameters) {
            parameter.enabled = request.enabled();
        }
        List<CameraParameterDto> body = parameters.stream().map(CamerasResource::toDto).toList();
        boolean enabled = request.enabled();
        var values = MotionService.toParameters(parameters);
        afterCommit.run(() -> motion.onEnabledChanged(cameraNumber, enabled, values));
        return Response.ok(body).build();
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
            summary = "Rebuild a camera's Motion config and (re)start it",
            description = "Writes the camera's config from its parameters. If Motion is already "
                    + "running it is sent SIGHUP to reload; otherwise it is started. Nothing "
                    + "happens while babymonitor.motion.enabled is false, which is the default.")
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
            return Response.ok(motion.apply(cameraNumber, MotionService.toParameters(parameters))).build();
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
            description = "Kills the process if it is running. A no-op otherwise.")
    @APIResponse(
            responseCode = "404",
            description = "No such camera",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response motionStop(@PathParam("cameraNumber") int cameraNumber) {
        if (Camera.listByCamera(cameraNumber).isEmpty()) {
            return notFound(cameraNumber);
        }
        return Response.ok(motion.stop(cameraNumber)).build();
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
        return new CameraParameterDto(
                parameter.cameraNumber,
                parameter.paramKey,
                parameter.value,
                parameter.enabled);
    }
}
