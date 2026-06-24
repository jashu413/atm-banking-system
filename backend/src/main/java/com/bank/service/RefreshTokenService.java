package com.bank.service;

import com.bank.domain.RefreshToken;
import com.bank.domain.UserAccount;
import com.bank.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Manages opaque, DB-backed, one-time-use refresh tokens.
 *
 * <p>Token lifecycle:
 * <ol>
 *   <li>On login: a raw UUID token is generated, stored as SHA-256 hash, returned to the client.</li>
 *   <li>On refresh: the raw token is hashed and looked up. If found and valid, it is marked as
 *       used and a new token (same {@code sessionId} family) is returned.</li>
 *   <li>If a token that is already marked {@code usedAt} is presented: the entire session family
 *       is revoked — this indicates token theft.</li>
 *   <li>On logout: the token is revoked by {@code sessionId}.</li>
 * </ol>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final long refreshTokenExpirationMs;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.repository = repository;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /** Creates a new refresh token for a user, starting a new session family. */
    @Transactional
    public String createToken(UserAccount user, String deviceInfo, String ipAddress) {
        String rawToken = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusMillis(refreshTokenExpirationMs);
        repository.save(new RefreshToken(hash(rawToken), user, sessionId, deviceInfo, ipAddress, expiresAt));
        return rawToken;
    }

    /**
     * Validates the presented token, rotates it (marks used, creates successor), and returns the
     * raw new refresh token together with the owning user.
     *
     * @throws BadCredentialsException on any invalid/expired/revoked/reused token.
     */
    @Transactional
    public RotationResult rotate(String rawToken, String deviceInfo, String ipAddress) {
        RefreshToken stored = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token."));

        if (stored.getUsedAt() != null) {
            // Token reuse detected: entire session is potentially compromised.
            log.warn("Refresh token reuse detected for sessionId={}. Revoking entire session.",
                    stored.getSessionId());
            repository.revokeSession(stored.getSessionId(), Instant.now());
            throw new BadCredentialsException("Refresh token has already been used. Session revoked for security.");
        }
        if (!stored.isValid()) {
            throw new BadCredentialsException("Invalid or expired refresh token.");
        }

        UserAccount user = stored.getUser();
        if (user.isAccountLocked() || !user.isEnabled()) {
            throw new BadCredentialsException("Account is locked or disabled.");
        }

        stored.markUsed();
        repository.save(stored);

        // Issue successor token in the same session family.
        String newRawToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusMillis(refreshTokenExpirationMs);
        repository.save(new RefreshToken(
                hash(newRawToken), user, stored.getSessionId(), deviceInfo, ipAddress, expiresAt));

        log.debug("Rotated refresh token for username={}, sessionId={}", user.getUsername(), stored.getSessionId());
        return new RotationResult(newRawToken, user, stored.getSessionId());
    }

    /** Revokes all active tokens in the session identified by the presented raw token. */
    @Transactional
    public void revokeByRawToken(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(stored ->
                repository.revokeSession(stored.getSessionId(), Instant.now()));
    }

    /** Revokes all tokens belonging to a specific session ID (for session-management endpoint). */
    @Transactional
    public void revokeSession(String sessionId) {
        repository.revokeSession(sessionId, Instant.now());
    }

    /** Returns active sessions for the given user. */
    @Transactional(readOnly = true)
    public List<RefreshToken> getActiveSessions(UserAccount user) {
        return repository.findActiveSessions(user, Instant.now());
    }

    /** Looks up the session ID of a raw token (used to mark the current session in the UI). */
    @Transactional(readOnly = true)
    public String getSessionId(String rawToken) {
        return repository.findByTokenHash(hash(rawToken))
                .map(RefreshToken::getSessionId)
                .orElse(null);
    }

    /** Nightly cleanup: deletes tokens that expired more than 24 hours ago. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        Instant cutoff = Instant.now().minusSeconds(86400);
        repository.deleteExpiredBefore(cutoff);
        log.info("Purged expired refresh tokens older than {}", cutoff);
    }

    public static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotationResult(String newRawToken, UserAccount user, String sessionId) {}
}
