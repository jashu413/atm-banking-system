package com.bank.exception;

/** Thrown when an account has insufficient balance for a withdrawal or transfer. */
public class InsufficientFundsException extends BankingException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
