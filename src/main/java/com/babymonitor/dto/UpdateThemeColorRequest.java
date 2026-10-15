package com.babymonitor.dto;

/**
 * Body of {@code PUT /api/users/me/theme-color}.
 *
 * @param themeColor a Mantine palette colour name, for example {@code blue} or
 *                   {@code grape}
 */
public record UpdateThemeColorRequest(String themeColor) {
}
