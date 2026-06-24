package com.bank.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Development/test email implementation: logs the email content to the console instead of
 * sending. Activate SMTP delivery in production by implementing {@link EmailService} with
 * {@code spring-boot-starter-mail} and marking it {@code @Profile("smtp")}.
 */
@Service
public class ConsoleEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

    @Override
    public void sendPasswordResetEmail(String to, String username, String resetLink) {
        log.info("""
                ──────────────────────────────────────────────────
                [EMAIL] Password Reset Request
                To:      {}
                Subject: Reset your ATM Banking password
                ──────────────────────────────────────────────────
                Hi {},

                You requested a password reset. Click the link below
                (valid for 1 hour):

                  {}

                If you did not request a reset, ignore this email.
                ──────────────────────────────────────────────────
                """, to, username, resetLink);
    }

    @Override
    public void sendEmailVerificationEmail(String to, String username, String verificationLink) {
        log.info("""
                ──────────────────────────────────────────────────
                [EMAIL] Email Verification
                To:      {}
                Subject: Verify your ATM Banking email address
                ──────────────────────────────────────────────────
                Hi {},

                Please verify your email address:

                  {}

                This link expires in 24 hours.
                ──────────────────────────────────────────────────
                """, to, username, verificationLink);
    }
}
