package com.atm.exception;

public class WithdrawalLimitExceededException extends ATMException {
    public WithdrawalLimitExceededException(String message) {
        super(message);
    }
}
