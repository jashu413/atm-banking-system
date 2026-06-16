package com.bank.exception;

/**
 * Base type for all banking domain/business-rule exceptions.
 *
 * <p>Migrated from the console application's {@code ATMException} and renamed to fit the
 * broader banking domain. Subclasses map to HTTP status codes in the Phase 4
 * {@code GlobalExceptionHandler}.
 */
public class BankingException extends RuntimeException {
    public BankingException(String message) {
        super(message);
    }
}
