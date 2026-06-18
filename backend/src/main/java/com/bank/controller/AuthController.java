package com.bank.controller;

import com.bank.dto.LoginRequest;
import com.bank.dto.LoginResponse;
import com.bank.dto.RefreshTokenRequest;
import com.bank.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public authentication endpoints: login, token refresh, and logout. This is the only controller
 * built in Phase 3 — the account/transfer/admin REST surface arrives in Phase 4.
 *
 * <p>Authentication failures are mapped to status codes by the controller-local
 * {@code @ExceptionHandler}s below; the project-wide {@code GlobalExceptionHandler} is a Phase 4
 * deliverable, so the mapping is intentionally scoped to this controller for now.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        // Stateless JWT: the client discards its tokens. Server-side revocation (Redis blacklist)
        // is a Phase 6 enhancement.
        return ResponseEntity.ok(Map.of(
                "message", "Logged out. Discard your tokens; server-side revocation arrives in Phase 6."));
    }

    @ExceptionHandler({BadCredentialsException.class})
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<Map<String, Object>> handleLocked(RuntimeException ex) {
        return error(HttpStatus.LOCKED, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
