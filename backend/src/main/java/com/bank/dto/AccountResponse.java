package com.bank.dto;

import com.bank.domain.BankAccount;

import java.math.BigDecimal;

/** API representation of an account. */
public record AccountResponse(
        Long id,
        String accountNumber,
        String accountType,
        String customerName,
        BigDecimal balance,
        BigDecimal dailyWithdrawalLimit,
        boolean locked
) {
    public static AccountResponse from(BankAccount account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getCustomer().getName(),
                account.getBalance(),
                account.getDailyWithdrawalLimit(),
                account.isLocked()
        );
    }
}
