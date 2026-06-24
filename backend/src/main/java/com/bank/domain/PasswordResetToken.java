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

/** Single-use, time-limited password-reset token (stored as SHA-256 hash). */
@Entity
@Table(name = "password_reset_tokens",
        indexes = @Index(name = "idx_prt_token_hash", columnList = "token_hash"))
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetToken() {}

    public PasswordResetToken(String tokenHash, UserAccount user, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public boolean isValid() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void markUsed() { this.usedAt = Instant.now(); }

    public Long getId()          { return id; }
    public String getTokenHash() { return tokenHash; }
    public UserAccount getUser() { return user; }
    public Instant getExpiresAt(){ return expiresAt; }
    public Instant getUsedAt()   { return usedAt; }
    public Instant getCreatedAt(){ return createdAt; }
}
