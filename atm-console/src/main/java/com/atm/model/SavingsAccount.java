package com.atm.model;

import java.math.BigDecimal;

public class SavingsAccount extends BankAccount {
    public SavingsAccount(String accountNumber, String pin, BigDecimal openingBalance, BigDecimal dailyWithdrawalLimit) {
        super(accountNumber, pin, openingBalance, dailyWithdrawalLimit);
    }

    @Override
    public String getAccountType() {
        return "Savings";
    }
}
