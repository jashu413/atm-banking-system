package com.bank.exception;

/** Thrown when a transfer request is structurally invalid (e.g. source equals target). Maps to 400. */
public class InvalidTransferException extends BankingException {
    public InvalidTransferException(String message) {
        super(message);
    }
}
