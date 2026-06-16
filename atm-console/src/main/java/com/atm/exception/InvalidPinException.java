package com.atm.exception;

public class InvalidPinException extends ATMException {
    public InvalidPinException(String message) {
        super(message);
    }
}
