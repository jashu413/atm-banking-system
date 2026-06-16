package com.atm.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Transaction {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TransactionType type;
    private final BigDecimal amount;
    private final LocalDateTime dateTime;
    private final String description;
    private final BigDecimal balanceAfterTransaction;

    public Transaction(TransactionType type, BigDecimal amount, String description, BigDecimal balanceAfterTransaction) {
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.balanceAfterTransaction = balanceAfterTransaction;
        this.dateTime = LocalDateTime.now();
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getBalanceAfterTransaction() {
        return balanceAfterTransaction;
    }

    @Override
    public String toString() {
        return "%s | %-12s | $%10.2f | Balance: $%10.2f | %s".formatted(
                dateTime.format(FORMATTER),
                type,
                amount,
                balanceAfterTransaction,
                description
        );
    }
}
