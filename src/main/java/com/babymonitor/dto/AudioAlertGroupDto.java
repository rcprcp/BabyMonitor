package com.babymonitor.dto;

/**
 * One category of YAMNet classes, as shown on the Audio Notification Config
 * page. The ranges are the AudioSet ordering the bundled class map follows.
 *
 * @param id         stable group id (1-6), used by the page's macro buttons
 * @param name       the category's display name
 * @param firstIndex first class index in the group (inclusive)
 * @param lastIndex  last class index in the group (inclusive)
 */
public record AudioAlertGroupDto(int id, String name, int firstIndex, int lastIndex) {
}
