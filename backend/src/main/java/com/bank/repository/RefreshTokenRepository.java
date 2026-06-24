package com.bank.repository;

import com.bank.domain.RefreshToken;
import com.bank.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findBySessionId(String sessionId);

    /** Active (not used, not revoked, not expired) tokens for a user — one per live session. */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user = :user " +
           "AND rt.usedAt IS NULL AND rt.revokedAt IS NULL AND rt.expiresAt > :now " +
           "ORDER BY rt.createdAt DESC")
    List<RefreshToken> findActiveSessions(@Param("user") UserAccount user,
                                          @Param("now") Instant now);

    /** Bulk-revoke all tokens in a session family (theft detection). */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.sessionId = :sessionId AND rt.revokedAt IS NULL")
    void revokeSession(@Param("sessionId") String sessionId, @Param("now") Instant now);

    /** Scheduled cleanup: delete records that have been expired for more than {@code before}. */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :before")
    void deleteExpiredBefore(@Param("before") Instant before);
}
