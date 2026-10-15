package com.babymonitor.dto;

/**
 * The signed-in user's own account record, as stored in
 * {@code babymonitor.users}.
 *
 * <p>Deliberately excludes {@code passwordHash}.
 *
 * @param id         primary key of the user row
 * @param name       login name / display name
 * @param theme      UI preference: {@code light}, {@code dark} or {@code auto}
 * @param themeColor accent colour: a Mantine palette name such as {@code blue}
 */
public record UserDto(Long id, String name, String theme, String themeColor) {
}
