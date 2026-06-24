package com.bank.dto;

/** Returned by {@code GET /api/v1/users/me}. */
public record UserProfileResponse(
        String username,
        String role,
        String customerName,
        String accountNumber
) {}
