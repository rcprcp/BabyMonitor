package com.babymonitor.dto;

/**
 * One recording file in a camera's recordings directory, as returned to the SPA.
 *
 * @param name      the file name (its basename)
 * @param url       where to fetch it, relative to the app:
 *                  {@code /api/cameras/{n}/recordings/{name}}
 * @param sizeBytes the file size in bytes
 * @param modified  when the file was last written, as an ISO-8601 instant
 */
public record RecordingDto(String name, String url, long sizeBytes, String modified) {
}
