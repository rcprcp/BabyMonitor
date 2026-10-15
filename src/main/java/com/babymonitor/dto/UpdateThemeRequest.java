package com.babymonitor.dto;

/**
 * Body of {@code PUT /api/users/me/theme}.
 *
 * @param theme one of {@code light}, {@code dark} or {@code auto}
 */
public record UpdateThemeRequest(String theme) {
}
