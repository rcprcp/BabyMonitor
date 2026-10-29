package com.babymonitor.resource;

import com.babymonitor.dto.VideoDeviceDto;
import com.babymonitor.service.VideoDevices;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * The host's {@code /dev/video*} nodes, for the setup wizard's camera picker.
 * Empty when sysfs exposes no video nodes (containers, no camera attached).
 */
@Path("/api/video-devices")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "System", description = "Host capabilities")
@SecurityRequirement(name = "BearerAuth")
public class VideoDevicesResource {

    @Inject
    VideoDevices devices;

    @GET
    public List<VideoDeviceDto> list() {
        return devices.list();
    }
}
