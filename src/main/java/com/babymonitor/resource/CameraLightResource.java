package com.babymonitor.resource;

import com.babymonitor.dto.CameraLightDto;
import com.babymonitor.dto.CameraLightSampleDto;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.UpdateCameraLightRequest;
import com.babymonitor.entity.Camera;
import com.babymonitor.environment.CameraLightBands;
import com.babymonitor.environment.CameraLightSensor;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
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
 * The camera-derived brightness sensor.
 *
 * <ul>
 *   <li>{@code GET /api/environment/light}         -- configuration, calibration, last sample</li>
 *   <li>{@code PUT /api/environment/light}         -- save configuration and calibration</li>
 *   <li>{@code POST /api/environment/light/sample} -- take one probe now</li>
 * </ul>
 *
 * <p>A probe needs the camera to be running: it reads a frame from the camera's
 * MJPEG stream while briefly switching to a fixed manual exposure.</p>
 */
@Path("/api/environment/light")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Environment", description = "Nursery temperature, humidity and brightness time series")
@SecurityRequirement(name = "BearerAuth")
public class CameraLightResource {

    private static final int MIN_EXPOSURE_MS = 1;
    private static final int MAX_EXPOSURE_MS = 150;

    @Inject
    CameraLightSensor sensor;

    @GET
    @Operation(
            summary = "Read the camera light sensor",
            description = "Configuration, six calibration lumas and the most recent probe result.")
    public CameraLightDto get() {
        return sensor.currentDto();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Update the camera light sensor",
            description =
                    "All six calibration lumas are required together; they must increase from dark to very bright. "
                            + "The sensor can only be enabled once calibrated.")
    @APIResponse(
            responseCode = "400",
            description = "The camera, exposure or calibration is invalid",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response update(UpdateCameraLightRequest request) {
        String error = validate(request);
        if (error != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(error))
                    .build();
        }
        return Response.ok(sensor.update(request)).build();
    }

    @POST
    @Path("/sample")
    @Operation(
            summary = "Take one camera light probe",
            description =
                    "Switches to a fixed manual exposure, reads one frame from the camera stream, "
                            + "restores auto-exposure, and returns the mean luma and band.")
    @APIResponse(
            responseCode = "409",
            description = "The camera is not running, has no stream, or the probe tools failed",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response sample() {
        try {
            CameraLightSampleDto sample = sensor.sampleNow();
            return Response.ok(sample).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    private static String validate(UpdateCameraLightRequest request) {
        if (request == null
                || request.cameraNumber() == null
                || request.exposureMs() == null
                || request.enabled() == null) {
            return "cameraNumber, exposureMs and enabled are required";
        }
        boolean cameraExists = QuarkusTransaction.requiringNew()
                .call(() -> !Camera.listByCamera(request.cameraNumber()).isEmpty());
        if (!cameraExists) {
            return "No such camera: " + request.cameraNumber();
        }
        if (request.exposureMs() < MIN_EXPOSURE_MS || request.exposureMs() > MAX_EXPOSURE_MS) {
            return "exposureMs must be between " + MIN_EXPOSURE_MS + " and " + MAX_EXPOSURE_MS;
        }
        boolean anyCalibration = request.darkLuma() != null
                || request.veryDimLuma() != null
                || request.dimLuma() != null
                || request.moderateLuma() != null
                || request.brightLuma() != null
                || request.veryBrightLuma() != null;
        boolean allCalibration = request.darkLuma() != null
                && request.veryDimLuma() != null
                && request.dimLuma() != null
                && request.moderateLuma() != null
                && request.brightLuma() != null
                && request.veryBrightLuma() != null;
        if (anyCalibration && !allCalibration) {
            return "All six calibration lumas are required together";
        }
        if (allCalibration) {
            String error = CameraLightBands.validate(
                    request.darkLuma(),
                    request.veryDimLuma(),
                    request.dimLuma(),
                    request.moderateLuma(),
                    request.brightLuma(),
                    request.veryBrightLuma());
            if (error != null) {
                return error;
            }
        }
        if (request.enabled() && !allCalibration) {
            return "Calibrate all six bands before enabling the camera light";
        }
        return null;
    }
}
