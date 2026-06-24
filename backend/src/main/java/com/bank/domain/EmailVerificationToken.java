package com.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Single-use, time-limited email-verification token (stored as SHA-256 hash). */
@Entity
@Table(name = "email_verification_tokens",
        indexes = @Index(name = "idx_evt_token_hash", columnList = "token_hash"))
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmailVerificationToken() {}

    public EmailVerificationToken(String tokenHash, UserAccount user, String email, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.email = email;
        this.expiresAt = expiresAt;
    }

    public boolean isValid() {
        return verifiedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void markVerified() { this.verifiedAt = Instant.now(); }

    public Long getId()           { return id; }
    public String getTokenHash()  { return tokenHash; }
    public UserAccount getUser()  { return user; }
    public String getEmail()      { return email; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getVerifiedAt(){ return verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
