package com.bank.exception;

/** Thrown when a PIN does not satisfy the 4-digit format rule. */
public class InvalidPinException extends BankingException {
    public InvalidPinException(String message) {
        super(message);
    }
}
