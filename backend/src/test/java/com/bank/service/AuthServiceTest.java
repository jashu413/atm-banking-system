package com.bank.service;

import com.bank.domain.Role;
import com.bank.domain.UserAccount;
import com.bank.dto.LoginResponse;
import com.bank.repository.UserAccountRepository;
import com.bank.security.JwtTokenProvider;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}: the login flow, the ported failed-attempt lockout policy,
 * and refresh-token exchange. Collaborators are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserAccountRepository userRepository;
    @Mock
    private JwtTokenProvider tokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, userRepository, tokenProvider, MAX_ATTEMPTS);
    }

    private UserAccount customer() {
        return new UserAccount("asha", "$2a$10$hash", Role.CUSTOMER);
    }

    @Test
    void successfulLoginIssuesTokensAndResetsFailureCount() {
        UserAccount user = customer();
        user.recordFailedLogin(1); // pretend there was a prior failure (count = 1)
        Authentication auth = new UsernamePasswordAuthenticationToken("asha", "pw");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findByUsername("asha")).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken("asha", "CUSTOMER")).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken("asha")).thenReturn("refresh-token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        LoginResponse response = authService.login("asha", "pw");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
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

        assertThatThrownBy(() -> authService.login("asha", "wrong"))
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
            assertThatThrownBy(() -> authService.login("asha", "wrong"))
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

        assertThatThrownBy(() -> authService.login("ghost", "wrong"))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void refreshWithValidRefreshTokenIssuesNewTokens() {
        UserAccount user = customer();
        when(tokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(tokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(tokenProvider.getUsername("refresh-token")).thenReturn("asha");
        when(userRepository.findByUsername("asha")).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken("asha", "CUSTOMER")).thenReturn("new-access");
        when(tokenProvider.generateRefreshToken("asha")).thenReturn("new-refresh");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        LoginResponse response = authService.refresh("refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refreshRejectsAnAccessTokenOrInvalidToken() {
        when(tokenProvider.validateToken("access-token")).thenReturn(true);
        when(tokenProvider.isRefreshToken("access-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("access-token"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshRejectsTokenForLockedUser() {
        UserAccount user = customer();
        user.recordFailedLogin(1); // locked
        when(tokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(tokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(tokenProvider.getUsername("refresh-token")).thenReturn("asha");
        when(userRepository.findByUsername("asha")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh("refresh-token"))
                .isInstanceOf(LockedException.class);
    }
}
