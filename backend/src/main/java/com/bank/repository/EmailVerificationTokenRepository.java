package com.bank.repository;

import com.bank.domain.EmailVerificationToken;
import com.bank.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** Latest pending verification for a user (for resend de-duplication). */
    Optional<EmailVerificationToken> findFirstByUserAndVerifiedAtIsNullOrderByCreatedAtDesc(UserAccount user);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :before")
    void deleteExpiredBefore(@Param("before") Instant before);
}
