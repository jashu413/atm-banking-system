package com.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * An immutable ledger entry. Migrated from the console {@code Transaction}.
 *
 * <p>{@code createdAt} is stamped in the constructor (as the console did) rather than via
 * {@code @CreationTimestamp}, so the value is available to in-memory domain logic (e.g. the
 * daily-withdrawal calculation in {@link BankAccount}) before the entity is flushed.
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_txn_account", columnList = "account_id"),
        @Index(name = "idx_txn_created_at", columnList = "created_at")
})
public class Transaction {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private BankAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 255)
    private String description;

    /** Counterparty account number for transfers; null otherwise. */
    @Column(name = "related_account", length = 20)
    private String relatedAccount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Transaction() {
        // for JPA
    }

    public Transaction(BankAccount account, TransactionType type, BigDecimal amount,
                       BigDecimal balanceAfter, String description, String relatedAccount) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.relatedAccount = relatedAccount;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public BankAccount getAccount() {
        return account;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public String getRelatedAccount() {
        return relatedAccount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "%s | %-12s | $%10.2f | Balance: $%10.2f | %s".formatted(
                createdAt.format(FORMATTER),
                type,
                amount,
                balanceAfter,
                description
        );
    }
}
