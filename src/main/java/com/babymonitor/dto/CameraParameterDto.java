package com.babymonitor.dto;

/**
 * One camera parameter as returned to the SPA.
 *
 * <p>Mirrors a row of {@code babymonitor.cameras}. {@code enabled} is the
 * camera's persisted desired state and is therefore the same on every row of
 * one camera.
 *
 * @param cameraNumber the camera this parameter belongs to
 * @param key          parameter name, e.g. {@code resolution}
 * @param value        parameter value
 * @param enabled      whether the camera should be running
 */
public record CameraParameterDto(
        int cameraNumber,
        String key,
        String value,
        boolean enabled) {
}
