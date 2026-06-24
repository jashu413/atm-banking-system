package com.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/auth/reset-password}. */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {}
