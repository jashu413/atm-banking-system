package com.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Immutable audit record for security-relevant backend actions. */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_username", columnList = "username"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditStatus status;

    @Column(length = 50)
    private String username;

    @Column(name = "account_number", length = 20)
    private String accountNumber;

    @Column(name = "target_account_number", length = 20)
    private String targetAccountNumber;

    @Column(length = 255)
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {
        // for JPA
    }

    public AuditLog(AuditAction action, AuditStatus status, String username,
                    String accountNumber, String targetAccountNumber, String message) {
        this.action = action;
        this.status = status;
        this.username = username;
        this.accountNumber = accountNumber;
        this.targetAccountNumber = targetAccountNumber;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public String getUsername() {
        return username;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getTargetAccountNumber() {
        return targetAccountNumber;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
