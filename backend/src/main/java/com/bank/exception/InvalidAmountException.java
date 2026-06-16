package com.bank.exception;

/** Thrown when a monetary amount is null, zero, or negative where a positive value is required. */
public class InvalidAmountException extends BankingException {
    public InvalidAmountException(String message) {
        super(message);
    }
}
