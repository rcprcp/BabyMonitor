package com.babymonitor.dto;

/**
 * Login credentials submitted by the SPA.
 *
 * @param name     the account name
 * @param password the plaintext password
 */
public record LoginRequest(String name, String password) {
}
