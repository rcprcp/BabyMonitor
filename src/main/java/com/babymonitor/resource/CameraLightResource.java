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
import jakarta.ws.rs.QueryParam;
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
 *   <li>{@code GET /api/environment/light}          -- configuration, calibration, last sample</li>
 *   <li>{@code GET /api/environment/light/samples}  -- hourly band history</li>
 *   <li>{@code PUT /api/environment/light}          -- save configuration and calibration</li>
 *   <li>{@code POST /api/environment/light/sample}  -- take one probe now</li>
 * </ul>
 *
 * <p>A probe needs the camera to be running: it reads a frame from the camera's
 * MJPEG stream while briefly switching to a fixed manual exposure. The sample
 * endpoint can take a camera and exposure that have not been saved yet, so the
 * UI can calibrate with the values currently on screen.</p>
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

    @GET
    @Path("/samples")
    @Operation(
            summary = "Read the camera light history",
            description =
                    "The latest calibrated band in each hour for the last `hours` hours, oldest first. "
                            + "Gaps mean the sensor was not calibrated or not sampling.")
    @APIResponse(
            responseCode = "400",
            description = "hours is outside 1..2000",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response samples(@QueryParam("hours") Integer hours) {
        if (hours != null && (hours < 1 || hours > 2000)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("hours must be between 1 and 2000"))
                    .build();
        }
        return Response.ok(sensor.hourlyBands(hours != null ? hours : 168)).build();
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
                            + "restores auto-exposure, and returns the mean luma and band. "
                            + "`cameraNumber` and `exposureMs` are optional: when supplied they override "
                            + "the saved configuration for this probe, so a calibration capture can use "
                            + "the form's unsaved values. Calibration captures pass `record=false` so "
                            + "they stay out of the history.")
    @APIResponse(
            responseCode = "400",
            description = "cameraNumber does not exist or exposureMs is outside 1..150",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(
            responseCode = "409",
            description = "The camera is not running, has no stream, or the probe tools failed",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response sample(
            @QueryParam("record") Boolean record,
            @QueryParam("cameraNumber") Integer cameraNumber,
            @QueryParam("exposureMs") Integer exposureMs) {
        String error = validateProbe(cameraNumber, exposureMs);
        if (error != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(error))
                    .build();
        }
        try {
            CameraLightSampleDto sample =
                    sensor.sampleNow(record == null || record, cameraNumber, exposureMs);
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
        if (!cameraExists(request.cameraNumber())) {
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

    private static String validateProbe(Integer cameraNumber, Integer exposureMs) {
        if (cameraNumber != null && !cameraExists(cameraNumber)) {
            return "No such camera: " + cameraNumber;
        }
        if (exposureMs != null
                && (exposureMs < MIN_EXPOSURE_MS || exposureMs > MAX_EXPOSURE_MS)) {
            return "exposureMs must be between " + MIN_EXPOSURE_MS + " and " + MAX_EXPOSURE_MS;
        }
        return null;
    }

    private static boolean cameraExists(int cameraNumber) {
        return QuarkusTransaction.requiringNew()
                .call(() -> !Camera.listByCamera(cameraNumber).isEmpty());
    }
}
