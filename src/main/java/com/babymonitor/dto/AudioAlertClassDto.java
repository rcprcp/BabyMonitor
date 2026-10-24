package com.babymonitor.dto;

/**
 * One YAMNet class and whether it may raise an Audio notification.
 *
 * @param index   the class index in the bundled class map
 * @param name    the class's display name, e.g. {@code Crying, sobbing}
 * @param groupId the category it belongs to
 * @param enabled whether an alert may fire on this class
 */
public record AudioAlertClassDto(int index, String name, int groupId, boolean enabled) {
}
