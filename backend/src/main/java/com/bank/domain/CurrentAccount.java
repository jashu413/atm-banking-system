package com.bank.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

/** A current account. Distinguished by the {@code CURRENT} discriminator value. */
@Entity
@DiscriminatorValue("CURRENT")
public class CurrentAccount extends BankAccount {

    protected CurrentAccount() {
        // for JPA
    }

    public CurrentAccount(String accountNumber, String pin, BigDecimal openingBalance,
                          BigDecimal dailyWithdrawalLimit) {
        super(accountNumber, pin, openingBalance, dailyWithdrawalLimit);
    }

    @Override
    public String getAccountType() {
        return "Current";
    }
}
