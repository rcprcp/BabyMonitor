package com.babymonitor.resource;

import com.babymonitor.dto.CameraParameterDto;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.UpdateCameraEnabledRequest;
import com.babymonitor.dto.UpdateCameraParameterRequest;
import com.babymonitor.entity.Camera;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Read/update access to the {@code babymonitor.cameras} table.
 *
 * <ul>
 *   <li>{@code GET /api/cameras}                          -- every camera's parameters</li>
 *   <li>{@code GET /api/cameras/{cameraNumber}}            -- one camera's parameters</li>
 *   <li>{@code PUT /api/cameras/{n}/parameters/{key}}      -- replace a parameter's value</li>
 *   <li>{@code PUT /api/cameras/{n}/enabled}               -- switch a camera on or off</li>
 * </ul>
 *
 * <p>Because {@code enabled} is stored on every row of a camera, switching it
 * updates all of that camera's rows together. All of it sits behind
 * {@link com.babymonitor.auth.AuthFilter}.
 */
@Path("/api/cameras")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
@Tag(name = "Cameras", description = "Camera parameters, edited from the Camera pages")
@SecurityRequirement(name = "BearerAuth")
public class CamerasResource {

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
            description = "Returns the updated parameter.")
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

    @PUT
    @Path("/{cameraNumber}/enabled")
    @Operation(
            summary = "Switch a camera on or off",
            description = "Applies to every parameter row of the camera and returns them all.")
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
        return Response.ok(parameters.stream().map(CamerasResource::toDto).toList()).build();
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
