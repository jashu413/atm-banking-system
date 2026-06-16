package com.bank.exception;

/** Thrown when a withdrawal would exceed the account's daily withdrawal limit. */
public class WithdrawalLimitExceededException extends BankingException {
    public WithdrawalLimitExceededException(String message) {
        super(message);
    }
}
