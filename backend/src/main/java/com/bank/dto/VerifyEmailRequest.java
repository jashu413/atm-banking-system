package com.bank.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/verify-email}. */
public record VerifyEmailRequest(
        @NotBlank String token
) {}
