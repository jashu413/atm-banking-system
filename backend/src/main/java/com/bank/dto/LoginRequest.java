package com.bank.dto;

import jakarta.validation.constraints.NotBlank;

/** Credentials for the {@code /auth/login} endpoint. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
