package com.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Authentication principal: the login identity for a customer or administrator. Holds the
 * BCrypt-hashed login password (never plaintext), the authorization {@link Role}, and the
 * login-lockout state.
 *
 * <p>This is the Phase 3 realisation of the {@code users} table from the migration plan. The
 * lockout policy is ported from the console {@code AuthenticationService}: after a configurable
 * number of failed logins the account is locked. This lock is distinct from {@link BankAccount}'s
 * own {@code locked} flag (an administrative account freeze).
 */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(length = 120)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected UserAccount() {
        // for JPA
    }

    public UserAccount(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    // --- Lockout policy (ported from the console AuthenticationService) ---

    /**
     * Records a failed login. Locks the account once the failure count reaches
     * {@code maxAttempts}, mirroring the console's three-strikes lockout.
     */
    public void recordFailedLogin(int maxAttempts) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.accountLocked = true;
        }
    }

    /** Clears the failure counter after a successful login. */
    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
    }

    public void unlock() {
        this.accountLocked = false;
        this.failedLoginAttempts = 0;
    }

    // --- Accessors ---

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public boolean isEmailVerified() { return emailVerified; }

    public void markEmailVerified() { this.emailVerified = true; }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
