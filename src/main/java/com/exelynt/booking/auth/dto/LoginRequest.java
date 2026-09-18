package com.exelynt.booking.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username is required")
        @Size(max = 50, message = "username must be at most 50 characters")
        String username,

        @NotBlank(message = "password is required")
        @Size(max = 100, message = "password must be at most 100 characters")
        String password) {

    /** Never let credentials reach a log line. */
    @Override
    public String toString() {
        return "LoginRequest{username='" + username + "'}";
    }
}
