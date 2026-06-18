package com.bank.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for the {@code /auth/refresh} endpoint. */
public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {
}
