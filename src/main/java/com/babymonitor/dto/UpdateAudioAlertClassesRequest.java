package com.babymonitor.dto;

import java.util.List;

/**
 * Replaces the whole alert set at once, which is how the page's group macro
 * buttons and its filtered views save.
 *
 * @param indices the classes that should be enabled; everything else is off
 */
public record UpdateAudioAlertClassesRequest(List<Integer> indices) {
}
