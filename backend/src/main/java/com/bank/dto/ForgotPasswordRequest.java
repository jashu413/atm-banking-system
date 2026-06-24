package com.bank.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/forgot-password}. */
public record ForgotPasswordRequest(
        @NotBlank String username
) {}
