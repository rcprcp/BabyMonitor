package com.babymonitor.dto;

/**
 * Body of {@code PUT /api/users/me/active-camera}.
 *
 * @param activeCamera the camera number as a positive integer string, for example {@code "1"} or {@code "2"}
 */
public record UpdateActiveCameraRequest(String activeCamera) {
}
