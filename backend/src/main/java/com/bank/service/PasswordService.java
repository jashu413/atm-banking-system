package com.bank.service;

import com.bank.domain.AuditAction;
import com.bank.domain.PasswordResetToken;
import com.bank.domain.UserAccount;
import com.bank.repository.PasswordResetTokenRepository;
import com.bank.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Password reset workflow.
 *
 * <ol>
 *   <li>User calls {@link #forgotPassword}: a single-use token is generated and the reset link
 *       is "sent" via {@link EmailService}. The response is always 200 (prevents username
 *       enumeration).</li>
 *   <li>User calls {@link #resetPassword} with the raw token and their chosen new password.
 *       The token is validated, marked used, and the password hash is updated.</li>
 * </ol>
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);
    private static final long RESET_TOKEN_EXPIRY_MS = 3_600_000L; // 1 hour

    private final UserAccountRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final String appBaseUrl;

    public PasswordService(UserAccountRepository userRepository,
                           PasswordResetTokenRepository tokenRepository,
                           EmailService emailService,
                           PasswordEncoder passwordEncoder,
                           AuditService auditService,
                           @Value("${app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Generates a reset token for the user and triggers email delivery. Always returns a
     * neutral message so callers cannot enumerate valid usernames.
     */
    @Transactional
    public Map<String, String> forgotPassword(String username) {
        Optional<UserAccount> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            UserAccount user = userOpt.get();
            String rawToken = UUID.randomUUID().toString();
            Instant expiresAt = Instant.now().plusMillis(RESET_TOKEN_EXPIRY_MS);
            tokenRepository.save(new PasswordResetToken(hash(rawToken), user, expiresAt));
            String resetLink = appBaseUrl + "/reset-password?token=" + rawToken;
            String email = user.getEmail() != null ? user.getEmail() : username + "@example.com";
            emailService.sendPasswordResetEmail(email, username, resetLink);
            auditService.success(AuditAction.LOGIN, username, null, null, "Password reset requested.");
            log.info("Password reset token issued for username={}", username);
        } else {
            log.warn("Password reset requested for unknown username={}", username);
        }
        return Map.of("message", "If that account exists, a reset link has been sent.");
    }

    @Transactional
    public Map<String, String> resetPassword(String rawToken, String newPassword) {
        PasswordResetToken stored = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired reset token."));
        if (!stored.isValid()) {
            throw new BadCredentialsException("Invalid or expired reset token.");
        }
        UserAccount user = stored.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        stored.markUsed();
        userRepository.save(user);
        tokenRepository.save(stored);
        auditService.success(AuditAction.LOGIN, user.getUsername(), null, null, "Password reset completed.");
        log.info("Password successfully reset for username={}", user.getUsername());
        return Map.of("message", "Password reset successfully. Please log in.");
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        tokenRepository.deleteExpiredBefore(Instant.now());
        log.debug("Purged expired password-reset tokens.");
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
