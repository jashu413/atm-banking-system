package com.bank.domain;

/** Categorizes a {@link Transaction}. Persisted as a string via {@code @Enumerated(STRING)}. */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_IN,
    TRANSFER_OUT,
    PIN_CHANGE
}
