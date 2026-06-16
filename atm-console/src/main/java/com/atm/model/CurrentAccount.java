package com.atm.model;

import java.math.BigDecimal;

public class CurrentAccount extends BankAccount {
    public CurrentAccount(String accountNumber, String pin, BigDecimal openingBalance, BigDecimal dailyWithdrawalLimit) {
        super(accountNumber, pin, openingBalance, dailyWithdrawalLimit);
    }

    @Override
    public String getAccountType() {
        return "Current";
    }
}
