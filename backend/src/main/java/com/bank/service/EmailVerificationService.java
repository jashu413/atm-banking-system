package com.bank.service;

import com.bank.domain.EmailVerificationToken;
import com.bank.domain.UserAccount;
import com.bank.exception.AccountNotFoundException;
import com.bank.repository.EmailVerificationTokenRepository;
import com.bank.repository.UserAccountRepository;
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
import java.util.Map;
import java.util.UUID;

/**
 * Email verification workflow: generates a time-limited token and verifies it when the user
 * returns with the link from the email.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final long VERIFICATION_TOKEN_EXPIRY_MS = 86_400_000L; // 24 hours

    private final UserAccountRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final String appBaseUrl;

    public EmailVerificationService(UserAccountRepository userRepository,
                                    EmailVerificationTokenRepository tokenRepository,
                                    EmailService emailService,
                                    @Value("${app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.appBaseUrl = appBaseUrl;
    }

    /** Looks up the user by username, then sends the verification email. */
    @Transactional
    public Map<String, String> sendVerificationByUsername(String username) {
        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AccountNotFoundException("User not found."));
        return sendVerification(user);
    }

    /** Sends (or resends) an email verification link to the user's registered email address. */
    @Transactional
    public Map<String, String> sendVerification(UserAccount user) {
        if (user.isEmailVerified()) {
            return Map.of("message", "Email address is already verified.");
        }
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return Map.of("message", "No email address is registered for this account.");
        }
        String rawToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusMillis(VERIFICATION_TOKEN_EXPIRY_MS);
        tokenRepository.save(new EmailVerificationToken(hash(rawToken), user, email, expiresAt));
        String link = appBaseUrl + "/verify-email?token=" + rawToken;
        emailService.sendEmailVerificationEmail(email, user.getUsername(), link);
        log.info("Email verification token issued for username={}", user.getUsername());
        return Map.of("message", "Verification email sent.");
    }

    /** Validates the token and marks the user's email as verified. */
    @Transactional
    public Map<String, String> verifyEmail(String rawToken) {
        EmailVerificationToken stored = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired verification token."));
        if (!stored.isValid()) {
            throw new BadCredentialsException("Invalid or expired verification token.");
        }
        UserAccount user = stored.getUser();
        user.markEmailVerified();
        stored.markVerified();
        userRepository.save(user);
        tokenRepository.save(stored);
        log.info("Email verified for username={}", user.getUsername());
        return Map.of("message", "Email address verified successfully.");
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        tokenRepository.deleteExpiredBefore(Instant.now());
        log.debug("Purged expired email-verification tokens.");
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
