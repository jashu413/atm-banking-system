package com.bank.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

/** A savings account. Distinguished by the {@code SAVINGS} discriminator value. */
@Entity
@DiscriminatorValue("SAVINGS")
public class SavingsAccount extends BankAccount {

    protected SavingsAccount() {
        // for JPA
    }

    public SavingsAccount(String accountNumber, String pin, BigDecimal openingBalance,
                          BigDecimal dailyWithdrawalLimit) {
        super(accountNumber, pin, openingBalance, dailyWithdrawalLimit);
    }

    @Override
    public String getAccountType() {
        return "Savings";
    }
}
