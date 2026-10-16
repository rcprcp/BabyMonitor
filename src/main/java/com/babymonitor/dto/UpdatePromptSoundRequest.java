package com.babymonitor.dto;

/**
 * Request body for {@code PUT /api/users/me/prompt-sound}.
 */
public record UpdatePromptSoundRequest(String promptSound) {
}
