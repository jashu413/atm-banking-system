package com.bank.controller;

import com.bank.dto.ForgotPasswordRequest;
import com.bank.dto.LoginRequest;
import com.bank.dto.LoginResponse;
import com.bank.dto.RefreshTokenRequest;
import com.bank.dto.ResetPasswordRequest;
import com.bank.dto.VerifyEmailRequest;
import com.bank.service.AuthService;
import com.bank.service.EmailVerificationService;
import com.bank.service.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public authentication endpoints: login, token refresh, logout, password reset, and email
 * verification. All endpoints under {@code /api/v1/auth/**} are permitted without a JWT
 * (configured in {@link com.bank.config.SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, token lifecycle, password reset, and email verification")
public class AuthController {

    private final AuthService authService;
    private final PasswordService passwordService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthService authService,
                          PasswordService passwordService,
                          EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.passwordService = passwordService;
        this.emailVerificationService = emailVerificationService;
    }

    // ── Login ───────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Authenticate with username and password",
               description = "Returns a JWT access token and an opaque refresh token. " +
                             "Rate-limited: 20 requests/min per IP, 10 per 15 min per username.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.login(request.username(), request.password(), deviceInfo, ip));
    }

    // ── Token refresh ────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Rotate refresh token",
               description = "Exchanges a valid opaque refresh token for a new access token and " +
                             "a rotated refresh token. Reusing an already-consumed token revokes " +
                             "the entire session (theft detection).")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                 HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.refresh(request.refreshToken(), deviceInfo, ip));
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Log out and revoke refresh token",
               description = "Revokes the presented refresh token, invalidating the session. " +
                             "No access token required — provide the refresh token in the body.")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        String rawRefreshToken = request != null ? request.refreshToken() : null;
        authService.logout(rawRefreshToken, username);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    // ── Password reset ────────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @SecurityRequirements
    @Operation(summary = "Request a password reset",
               description = "Generates a single-use reset token and dispatches it via email. " +
                             "Always returns 200 to prevent username enumeration.")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(passwordService.forgotPassword(request.username()));
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    @Operation(summary = "Reset password using a token",
               description = "Validates the reset token and sets a new password. Tokens are " +
                             "single-use and expire after 1 hour.")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(passwordService.resetPassword(request.token(), request.newPassword()));
    }

    // ── Email verification ────────────────────────────────────────────────────

    @PostMapping("/send-verification")
    @Operation(summary = "Send email verification link",
               description = "Sends (or resends) a 24-hour email verification link to the " +
                             "registered address. Requires an authenticated session.")
    public ResponseEntity<Map<String, String>> sendVerification(Authentication authentication) {
        // UserAccount is resolved from the authenticated principal by the service.
        // The controller passes the username and lets the service look up the entity.
        return ResponseEntity.ok(
                emailVerificationService.sendVerificationByUsername(authentication.getName()));
    }

    @PostMapping("/verify-email")
    @SecurityRequirements
    @Operation(summary = "Verify email address",
               description = "Marks the user's email as verified. Single-use, expires in 24 hours.")
    public ResponseEntity<Map<String, String>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(emailVerificationService.verifyEmail(request.token()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
