package com.babymonitor.resource;

import com.babymonitor.dto.EnvironmentReadingDto;
import com.babymonitor.dto.EnvironmentThresholdsDto;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.dto.UpdateEnvironmentThresholdsRequest;
import com.babymonitor.entity.EnvironmentReading;
import com.babymonitor.entity.EnvironmentThresholds;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Read access to the {@code babymonitor.environment} time series.
 *
 * <ul>
 *   <li>{@code GET /api/environment}              -- newest samples first</li>
 *   <li>{@code GET /api/environment?limit=24}     -- at most {@code limit} rows</li>
 *   <li>{@code GET /api/environment/thresholds}   -- the high/low bounds</li>
 *   <li>{@code PUT /api/environment/thresholds}   -- replace the bounds</li>
 * </ul>
 *
 * <p>Rows are written by the sensor (outside this API); the Environment page
 * reads them. All of it sits behind {@link com.babymonitor.auth.AuthFilter}.
 */
@Path("/api/environment")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Tag(name = "Environment", description = "Nursery temperature, humidity and brightness time series")
@SecurityRequirement(name = "BearerAuth")
public class EnvironmentResource {

    /** Rows returned when the caller does not ask for a specific number: a week of hourly samples. */
    private static final int DEFAULT_LIMIT = 168;

    /** Upper bound on {@code ?limit}, so one call cannot pull the whole history. */
    private static final int MAX_LIMIT = 2000;

    @GET
    @Operation(
            summary = "List ambient readings",
            description = "Newest sample first. `limit` caps the number of rows returned.")
    @APIResponse(
            responseCode = "400",
            description = "limit is outside 1..2000",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response list(@QueryParam("limit") Integer limit) {
        if (limit != null && (limit < 1 || limit > MAX_LIMIT)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("limit must be between 1 and " + MAX_LIMIT))
                    .build();
        }
        int size = limit != null ? limit : DEFAULT_LIMIT;
        List<EnvironmentReadingDto> readings = EnvironmentReading.newestFirst(size).stream()
                .map(EnvironmentResource::toDto)
                .toList();
        return Response.ok(readings).build();
    }

    @GET
    @Path("/thresholds")
    @Operation(
            summary = "Read the environment bounds",
            description = "High/low bounds for humidity (%), temperature (°F) and brightness (lux).")
    public EnvironmentThresholdsDto thresholds() {
        return toDto(EnvironmentThresholds.current());
    }

    @PUT
    @Path("/thresholds")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Update the environment bounds",
            description = "All six bounds are required; low must be below high and inside its range.")
    @APIResponse(
            responseCode = "400",
            description = "A bound is missing, out of range, or low is not below high",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateThresholds(UpdateEnvironmentThresholdsRequest request) {
        String error = validateThresholds(request);
        if (error != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(error))
                    .build();
        }
        EnvironmentThresholds row = EnvironmentThresholds.current();
        row.humidityLow = request.humidityLow();
        row.humidityHigh = request.humidityHigh();
        row.temperatureLow = request.temperatureLow();
        row.temperatureHigh = request.temperatureHigh();
        row.brightnessLow = request.brightnessLow();
        row.brightnessHigh = request.brightnessHigh();
        return Response.ok(toDto(row)).build();
    }

    private static String validateThresholds(UpdateEnvironmentThresholdsRequest request) {
        if (request == null
                || request.humidityLow() == null
                || request.humidityHigh() == null
                || request.temperatureLow() == null
                || request.temperatureHigh() == null
                || request.brightnessLow() == null
                || request.brightnessHigh() == null) {
            return "All six bounds are required";
        }
        if (!validPair(request.humidityLow(), request.humidityHigh(), 0, 100)) {
            return "Humidity must be between 0 and 100, with low below high";
        }
        if (!validPair(request.temperatureLow(), request.temperatureHigh(), 32, 100)) {
            return "Temperature must be between 32 and 100 °F, with low below high";
        }
        if (request.brightnessLow() < 0
                || request.brightnessHigh() > 3000
                || request.brightnessLow() >= request.brightnessHigh()) {
            return "Brightness must be between 0 and 3000 lux, with low below high";
        }
        return null;
    }

    private static boolean validPair(BigDecimal low, BigDecimal high, int min, int max) {
        return low.compareTo(BigDecimal.valueOf(min)) >= 0
                && high.compareTo(BigDecimal.valueOf(max)) <= 0
                && low.compareTo(high) < 0;
    }

    private static EnvironmentThresholdsDto toDto(EnvironmentThresholds row) {
        return new EnvironmentThresholdsDto(
                row.humidityLow,
                row.humidityHigh,
                row.temperatureLow,
                row.temperatureHigh,
                row.brightnessLow,
                row.brightnessHigh);
    }

    private static EnvironmentReadingDto toDto(EnvironmentReading reading) {
        return new EnvironmentReadingDto(
                reading.eventTime.toString(),
                reading.temperature,
                reading.humidity,
                reading.brightness);
    }
}
