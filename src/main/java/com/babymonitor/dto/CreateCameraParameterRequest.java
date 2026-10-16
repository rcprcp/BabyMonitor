package com.babymonitor.dto;

/**
 * Body of {@code POST /api/cameras/{cameraNumber}/parameters}.
 *
 * <p>The Camera pages build a new row from these two fields. {@code key} is the
 * Motion option name, {@code value} its setting; editing a parameter only
 * stores it, and nothing reaches Motion until
 * {@code POST /api/cameras/{n}/motion/apply}.
 *
 * @param key   the parameter name, e.g. {@code framerate}
 * @param value the parameter's value
 */
public record CreateCameraParameterRequest(String key, String value) {
}
