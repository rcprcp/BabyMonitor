package com.babymonitor.dto;

import java.util.List;

/**
 * The whole Audio Notification Config: the categories and every class with its
 * switch. Returned by {@code GET /api/audio/alert-classes}.
 *
 * @param groups  the six categories, in index order
 * @param classes every class in the bundled class map, in index order
 */
public record AudioAlertConfigDto(List<AudioAlertGroupDto> groups, List<AudioAlertClassDto> classes) {
}
