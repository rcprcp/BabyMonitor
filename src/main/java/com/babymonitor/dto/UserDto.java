package com.babymonitor.dto;

/**
 * The signed-in user's own account record, as stored in
 * {@code babymonitor.users}.
 *
 * <p>Deliberately excludes {@code passwordHash}.
 *
 * @param id   primary key of the user row
 * @param name login name / display name
 */
public record UserDto(Long id, String name) {
}
