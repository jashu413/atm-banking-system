package com.bank.service;

import com.bank.domain.Role;
import com.bank.domain.UserAccount;
import com.bank.dto.LoginResponse;
import com.bank.exception.RateLimitExceededException;
import com.bank.repository.UserAccountRepository;
import com.bank.security.JwtTokenProvider;
import com.bank.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}: login/lockout policy, opaque refresh-token rotation,
 * rate limiting integration, and logout.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserAccountRepository userRepository;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuditService auditService;
    @Mock private LoginRateLimiter rateLimiter;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, userRepository, tokenProvider,
                refreshTokenService, auditService, rateLimiter, MAX_ATTEMPTS);
    }

    private UserAccount customer() {
        return new UserAccount("asha", "$2a$10$hash", Role.CUSTOMER);
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @Test
    void successfulLoginIssuesTokensAndResetsFailureCount() {
        UserAccount user = customer();
        user.recordFailedLogin(1);
        Authentication auth = new UsernamePasswordAuthenticationToken("asha", "pw");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findByUsername("asha")).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken("asha", "CUSTOMER")).thenReturn("access-token");
        when(refreshTokenService.createToken(eq(user), isNull(), isNull())).thenReturn("opaque-refresh");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        LoginResponse response = authService.login("asha", "pw", null, null);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("opaque-refresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.role()).isEqualTo("CUSTOMER");
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(userRepository).save(user);
    }

    @Test
    void badCredentialsIncrementTheFailureCounter() {
        UserAccount user = customer();
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));
        when(userRepository.findByUsername("asha")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("asha", "wrong", null, null))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.isAccountLocked()).isFalse();
    }

    @Test
    void accountLocksAfterReachingTheConfiguredThreshold() {
        UserAccount user = customer();
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));
        when(userRepository.findByUsername("asha")).thenReturn(Optional.of(user));

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> authService.login("asha", "wrong", null, null))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThat(user.getFailedLoginAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(user.isAccountLocked()).isTrue();
        verify(userRepository, atLeastOnce()).save(user);
    }

    @Test
    void unknownUsernameOnBadCredentialsDoesNotPersistAnything() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "wrong", null, null))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginBlockedByRateLimiter() {
        doThrow(new RateLimitExceededException("Too many attempts."))
                .when(rateLimiter).checkAndRecord(anyString(), anyString());

        assertThatThrownBy(() -> authService.login("asha", "pw", null, "192.168.1.1"))
                .isInstanceOf(RateLimitExceededException.class);

        verify(authenticationManager, never()).authenticate(any());
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Test
    void refreshWithValidOpaqueTokenIssuesNewTokenPair() {
        UserAccount user = customer();
        RefreshTokenService.RotationResult result =
                new RefreshTokenService.RotationResult("new-opaque-refresh", user, "session-uuid");
        when(refreshTokenService.rotate("old-opaque", null, null)).thenReturn(result);
        when(tokenProvider.generateAccessToken("asha", "CUSTOMER")).thenReturn("new-access");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        LoginResponse response = authService.refresh("old-opaque", null, null);

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-opaque-refresh");
    }

    @Test
    void refreshWithInvalidTokenThrows() {
        when(refreshTokenService.rotate(eq("bad-token"), any(), any()))
                .thenThrow(new BadCredentialsException("Invalid token."));

        assertThatThrownBy(() -> authService.refresh("bad-token", null, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshWithReuseDetectedThrows() {
        when(refreshTokenService.rotate(eq("reused-token"), any(), any()))
                .thenThrow(new BadCredentialsException("Refresh token has already been used. Session revoked for security."));

        assertThatThrownBy(() -> authService.refresh("reused-token", null, null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Session revoked");
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    void logoutRevokesRefreshToken() {
        authService.logout("some-refresh-token", "asha");
        verify(refreshTokenService).revokeByRawToken("some-refresh-token");
    }

    @Test
    void logoutWithNullTokenStillSucceeds() {
        authService.logout(null, "asha");
        verify(refreshTokenService, never()).revokeByRawToken(any());
    }
}
