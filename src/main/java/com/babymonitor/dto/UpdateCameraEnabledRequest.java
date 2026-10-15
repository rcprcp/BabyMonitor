package com.babymonitor.dto;

/**
 * Body of {@code PUT /api/cameras/{cameraNumber}/enabled}.
 *
 * @param enabled whether the camera should be switched on
 */
public record UpdateCameraEnabledRequest(Boolean enabled) {
}
