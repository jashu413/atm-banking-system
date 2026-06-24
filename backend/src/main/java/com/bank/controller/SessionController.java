package com.bank.controller;

import com.bank.domain.RefreshToken;
import com.bank.domain.UserAccount;
import com.bank.dto.SessionResponse;
import com.bank.repository.UserAccountRepository;
import com.bank.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Allows authenticated users to view and revoke their active sessions (refresh token families).
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "Active session management")
public class SessionController {

    private final RefreshTokenService refreshTokenService;
    private final UserAccountRepository userRepository;

    public SessionController(RefreshTokenService refreshTokenService,
                             UserAccountRepository userRepository) {
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(summary = "List active sessions",
               description = "Returns all active sessions for the authenticated user. " +
                             "Pass the current refresh token as 'currentToken' to flag the caller's session.")
    public ResponseEntity<List<SessionResponse>> listSessions(
            Authentication auth,
            @RequestParam(required = false) String currentToken) {
        UserAccount user = resolveUser(auth);
        String currentSessionId = currentToken != null
                ? refreshTokenService.getSessionId(currentToken) : null;
        List<RefreshToken> sessions = refreshTokenService.getActiveSessions(user);
        List<SessionResponse> response = sessions.stream()
                .map(rt -> SessionResponse.from(rt, currentSessionId))
                .toList();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Revoke a session",
               description = "Revokes a specific session, invalidating its refresh token. " +
                             "Only sessions belonging to the authenticated user may be revoked.")
    public ResponseEntity<Map<String, String>> revokeSession(
            @PathVariable String sessionId, Authentication auth) {
        UserAccount user = resolveUser(auth);
        // Verify the session belongs to this user before revoking.
        boolean owned = refreshTokenService.getActiveSessions(user).stream()
                .anyMatch(rt -> rt.getSessionId().equals(sessionId));
        if (!owned) {
            throw new BadCredentialsException("Session not found or does not belong to this user.");
        }
        refreshTokenService.revokeSession(sessionId);
        return ResponseEntity.ok(Map.of("message", "Session revoked."));
    }

    @DeleteMapping
    @Operation(summary = "Revoke all sessions",
               description = "Logs out from all devices by revoking every active refresh token.")
    public ResponseEntity<Map<String, String>> revokeAllSessions(Authentication auth) {
        UserAccount user = resolveUser(auth);
        refreshTokenService.getActiveSessions(user)
                .forEach(rt -> refreshTokenService.revokeSession(rt.getSessionId()));
        return ResponseEntity.ok(Map.of("message", "All sessions revoked."));
    }

    private UserAccount resolveUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new BadCredentialsException("User not found."));
    }
}
