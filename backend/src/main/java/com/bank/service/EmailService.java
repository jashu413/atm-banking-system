package com.bank.service;

/**
 * Abstraction over email delivery. The active implementation is chosen by Spring profile:
 * the default {@link ConsoleEmailService} logs to the console (dev/test), and a future
 * SMTP implementation can be activated in production via {@code @Profile("smtp")}.
 */
public interface EmailService {

    void sendPasswordResetEmail(String to, String username, String resetLink);

    void sendEmailVerificationEmail(String to, String username, String verificationLink);
}
