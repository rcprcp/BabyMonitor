package com.babymonitor.resource;

import com.babymonitor.dto.EnvironmentReadingDto;
import com.babymonitor.dto.ErrorResponse;
import com.babymonitor.entity.EnvironmentReading;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
 * Read access to the {@code babymonitor.environment} time series.
 *
 * <ul>
 *   <li>{@code GET /api/environment}              -- newest samples first</li>
 *   <li>{@code GET /api/environment?limit=24}     -- at most {@code limit} rows</li>
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

    private static EnvironmentReadingDto toDto(EnvironmentReading reading) {
        return new EnvironmentReadingDto(
                reading.eventTime.toString(),
                reading.temperature,
                reading.humidity,
                reading.brightness);
    }
}
