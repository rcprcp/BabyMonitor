package com.babymonitor.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** One video4linux capture node the setup wizard can offer. */
@Schema(description = "A /dev/video* node and the kernel's name for it")
public record VideoDeviceDto(
        @Schema(description = "Device path, e.g. /dev/video0") String device,
        @Schema(description = "Kernel device name, e.g. Integrated Camera", nullable = true) String name) {
}
