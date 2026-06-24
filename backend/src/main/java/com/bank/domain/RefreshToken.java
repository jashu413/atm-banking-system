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

/**
 * Opaque, one-time-use refresh token stored as a SHA-256 hash.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>The raw token (UUID) is returned to the client once and never stored — only its hash is
 *       persisted, so a DB breach does not expose usable tokens.</li>
 *   <li>{@code sessionId} (UUID) groups every token in a rotation chain. If a token that is already
 *       {@code usedAt != null} is presented again, the entire session is revoked (token-theft
 *       detection / refresh-token family approach).</li>
 *   <li>Used/expired tokens are kept for the audit trail; a scheduled job prunes records older
 *       than the maximum refresh-token lifetime.</li>
 * </ul>
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_rt_token_hash", columnList = "token_hash"),
        @Index(name = "idx_rt_user_id", columnList = "user_id"),
        @Index(name = "idx_rt_session_id", columnList = "session_id")
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    /** Groups all tokens in one login-session rotation chain. */
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "device_info", length = 512)
    private String deviceInfo;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when this token has been exchanged for a new one (rotation). */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Set when this token or its session has been explicitly revoked. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {}

    public RefreshToken(String tokenHash, UserAccount user, String sessionId,
                        String deviceInfo, String ipAddress, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.sessionId = sessionId;
        this.deviceInfo = deviceInfo;
        this.ipAddress = ipAddress;
        this.expiresAt = expiresAt;
    }

    public boolean isValid() {
        return usedAt == null && revokedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void markUsed() { this.usedAt = Instant.now(); }
    public void revoke()   { this.revokedAt = Instant.now(); }

    public Long getId()          { return id; }
    public String getTokenHash() { return tokenHash; }
    public UserAccount getUser() { return user; }
    public String getSessionId() { return sessionId; }
    public String getDeviceInfo(){ return deviceInfo; }
    public String getIpAddress() { return ipAddress; }
    public Instant getExpiresAt(){ return expiresAt; }
    public Instant getUsedAt()   { return usedAt; }
    public Instant getRevokedAt(){ return revokedAt; }
    public Instant getCreatedAt(){ return createdAt; }
}
