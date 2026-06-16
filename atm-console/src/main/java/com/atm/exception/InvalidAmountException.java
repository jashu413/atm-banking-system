package com.atm.exception;

public class InvalidAmountException extends ATMException {
    public InvalidAmountException(String message) {
        super(message);
    }
}
