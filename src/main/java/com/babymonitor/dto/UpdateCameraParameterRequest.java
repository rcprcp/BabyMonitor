package com.babymonitor.dto;

/**
 * Body of {@code PUT /api/cameras/{cameraNumber}/parameters/{key}}.
 *
 * @param value the parameter's new value
 */
public record UpdateCameraParameterRequest(String value) {
}
