package com.bank.service;

import com.bank.domain.AuditAction;
import com.bank.domain.UserAccount;
import com.bank.dto.LoginResponse;
import com.bank.repository.UserAccountRepository;
import com.bank.security.JwtTokenProvider;
import com.bank.security.LoginRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication operations: login, refresh-token rotation, and logout.
 *
 * <p>Refresh tokens are now opaque, DB-backed, and one-time-use (Phase 8). Each login starts a
 * new session family tracked by a {@code sessionId} UUID. Reuse of a consumed token revokes the
 * entire family (theft detection).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final LoginRateLimiter rateLimiter;
    private final int maxFailedLoginAttempts;

    public AuthService(AuthenticationManager authenticationManager,
                       UserAccountRepository userRepository,
                       JwtTokenProvider tokenProvider,
                       RefreshTokenService refreshTokenService,
                       AuditService auditService,
                       LoginRateLimiter rateLimiter,
                       @Value("${app.security.max-failed-login-attempts:3}") int maxFailedLoginAttempts) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.rateLimiter = rateLimiter;
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
    }

    /**
     * Authenticates credentials, enforces rate limiting and the failed-attempt lockout policy,
     * and returns a new token pair with an opaque refresh token.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponse login(String username, String rawPassword,
                               String deviceInfo, String ipAddress) {
        // Rate-limit check first — before any DB work.
        rateLimiter.checkAndRecord(ipAddress != null ? ipAddress : "unknown", username);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, rawPassword));
        } catch (BadCredentialsException ex) {
            registerFailedAttempt(username);
            auditService.failure(AuditAction.LOGIN, username, null, null, "Invalid login credentials.");
            log.warn("Failed login attempt for username={}", username);
            throw ex;
        }

        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));
        user.recordSuccessfulLogin();
        userRepository.save(user);
        rateLimiter.onLoginSuccess(ipAddress != null ? ipAddress : "unknown", username);

        auditService.success(AuditAction.LOGIN, user.getUsername(), null, null, "Login successful.");
        log.info("Successful login for username={}", user.getUsername());
        return buildResponse(user, deviceInfo, ipAddress);
    }

    /**
     * Validates and rotates the opaque refresh token, returning a fresh token pair. Reuse of an
     * already-consumed token revokes the entire session (theft detection).
     */
    @Transactional
    public LoginResponse refresh(String rawRefreshToken, String deviceInfo, String ipAddress) {
        RefreshTokenService.RotationResult result =
                refreshTokenService.rotate(rawRefreshToken, deviceInfo, ipAddress);
        UserAccount user = result.user();
        log.info("Refresh token rotated for username={}", user.getUsername());
        String accessToken = tokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        return LoginResponse.bearer(
                accessToken, result.newRawToken(),
                tokenProvider.getAccessTokenExpirationSeconds(),
                user.getUsername(), user.getRole().name());
    }

    /** Revokes the presented refresh token, invalidating that session. */
    @Transactional
    public void logout(String rawRefreshToken, String username) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeByRawToken(rawRefreshToken);
        }
        auditService.success(AuditAction.LOGOUT, username, null, null, "Logout: session revoked.");
        log.info("Logout: refresh token revoked for username={}", username);
    }

    private LoginResponse buildResponse(UserAccount user, String deviceInfo, String ipAddress) {
        String accessToken = tokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        String rawRefreshToken = refreshTokenService.createToken(user, deviceInfo, ipAddress);
        return LoginResponse.bearer(
                accessToken, rawRefreshToken,
                tokenProvider.getAccessTokenExpirationSeconds(),
                user.getUsername(), user.getRole().name());
    }

    private void registerFailedAttempt(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.recordFailedLogin(maxFailedLoginAttempts);
            userRepository.save(user);
        });
    }
}
