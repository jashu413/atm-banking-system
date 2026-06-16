package com.bank.dto;

import com.bank.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** API representation of a ledger entry. */
public record TransactionResponse(
        Long id,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        String relatedAccount,
        LocalDateTime createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType().name(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getDescription(),
                transaction.getRelatedAccount(),
                transaction.getCreatedAt()
        );
    }
}
