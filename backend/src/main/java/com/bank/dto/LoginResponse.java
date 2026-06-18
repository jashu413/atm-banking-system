package com.bank.dto;

/**
 * Issued credentials returned by {@code /auth/login} and {@code /auth/refresh}: a bearer access
 * token (plus its lifetime in seconds), a refresh token, and the authenticated identity.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        String username,
        String role
) {
    public static LoginResponse bearer(String accessToken, String refreshToken,
                                       long expiresInSeconds, String username, String role) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, username, role);
    }
}
