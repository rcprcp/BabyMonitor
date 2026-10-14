package com.babymonitor.dto;

/** A plain error payload: {@code {"error": "..."}}. */
public record ErrorResponse(String error) {
}
