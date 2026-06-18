package com.bank.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtTokenProvider}: token generation, round-trip claim extraction,
 * access/refresh discrimination, and rejection of tampered or expired tokens.
 */
class JwtTokenProviderTest {

    // 32+ byte base64 secret (HS256 requires >= 256 bits).
    private static final String SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW9ubHktMTIzNDU2Nzg5MA==";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 900_000L, 604_800_000L);

    @Test
    void generatesAndValidatesAnAccessTokenCarryingTheRole() {
        String token = provider.generateAccessToken("asha", "CUSTOMER");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsername(token)).isEqualTo("asha");
        assertThat(provider.getRole(token)).isEqualTo("CUSTOMER");
        assertThat(provider.isRefreshToken(token)).isFalse();
    }

    @Test
    void refreshTokenIsMarkedAsRefreshAndHasNoRole() {
        String token = provider.generateRefreshToken("asha");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.isRefreshToken(token)).isTrue();
        assertThat(provider.getRole(token)).isNull();
    }

    @Test
    void rejectsATamperedToken() {
        String token = provider.generateAccessToken("asha", "CUSTOMER");
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(provider.validateToken(tampered)).isFalse();
    }

    @Test
    void rejectsGarbageAndEmptyInput() {
        assertThat(provider.validateToken("not-a-jwt")).isFalse();
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    void rejectsAnExpiredToken() {
        // Negative TTL produces a token whose expiry is already in the past.
        JwtTokenProvider expiring = new JwtTokenProvider(SECRET, -1_000L, -1_000L);
        String token = expiring.generateAccessToken("asha", "CUSTOMER");

        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void rejectsATokenSignedWithADifferentKey() {
        String otherSecret = "YW5vdGhlci1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtMTIzNDU2Nzg=";
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherSecret, 900_000L, 604_800_000L);
        String foreignToken = otherProvider.generateAccessToken("asha", "CUSTOMER");

        assertThat(provider.validateToken(foreignToken)).isFalse();
    }
}
