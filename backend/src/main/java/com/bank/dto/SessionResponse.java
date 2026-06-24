package com.bank.dto;

import com.bank.domain.RefreshToken;

import java.time.Instant;

/** A single active session visible to the authenticated user. */
public record SessionResponse(
        Long id,
        String sessionId,
        String deviceInfo,
        String ipAddress,
        Instant createdAt,
        Instant expiresAt,
        boolean current
) {
    public static SessionResponse from(RefreshToken rt, String currentSessionId) {
        return new SessionResponse(
                rt.getId(),
                rt.getSessionId(),
                rt.getDeviceInfo(),
                rt.getIpAddress(),
                rt.getCreatedAt(),
                rt.getExpiresAt(),
                rt.getSessionId().equals(currentSessionId)
        );
    }
}
