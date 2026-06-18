package com.bank.service;

import com.bank.domain.UserAccount;
import com.bank.dto.LoginResponse;
import com.bank.repository.UserAccountRepository;
import com.bank.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication operations: login (with the ported failed-attempt lockout policy), access-token
 * refresh, and a logout placeholder.
 *
 * <p>Token revocation on logout (a Redis blacklist) is deferred to Phase 6; with stateless JWTs a
 * logout is otherwise client-side (discard the token). Access tokens are short-lived to bound the
 * exposure window until then.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final int maxFailedLoginAttempts;

    public AuthService(AuthenticationManager authenticationManager,
                       UserAccountRepository userRepository,
                       JwtTokenProvider tokenProvider,
                       @Value("${app.security.max-failed-login-attempts:3}") int maxFailedLoginAttempts) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
    }

    /**
     * Authenticates a username/password and issues tokens. On bad credentials the user's failed
     * attempt counter is incremented and the account is locked once the configured threshold is
     * reached; a locked account is rejected with {@link LockedException} before any password check.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponse login(String username, String rawPassword) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, rawPassword));
        } catch (BadCredentialsException ex) {
            registerFailedAttempt(username);
            throw ex;
        }

        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));
        user.recordSuccessfulLogin();
        userRepository.save(user);

        return issueTokens(user);
    }

    /** Exchanges a valid refresh token for a fresh pair of tokens. */
    @Transactional(readOnly = true)
    public LoginResponse refresh(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken) || !tokenProvider.isRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token.");
        }
        String username = tokenProvider.getUsername(refreshToken);
        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));
        if (user.isAccountLocked() || !user.isEnabled()) {
            throw new LockedException("Account is locked or disabled.");
        }
        return issueTokens(user);
    }

    private LoginResponse issueTokens(UserAccount user) {
        String access = tokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        String refresh = tokenProvider.generateRefreshToken(user.getUsername());
        return LoginResponse.bearer(access, refresh,
                tokenProvider.getAccessTokenExpirationSeconds(), user.getUsername(), user.getRole().name());
    }

    private void registerFailedAttempt(String username) {
        // No-op for unknown usernames to avoid persisting state for non-existent accounts.
        userRepository.findByUsername(username).ifPresent(user -> {
            user.recordFailedLogin(maxFailedLoginAttempts);
            userRepository.save(user);
        });
    }
}
