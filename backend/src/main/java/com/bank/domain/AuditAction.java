package com.bank.domain;

/** Auditable application actions that matter for security and operations. */
public enum AuditAction {
    LOGIN,
    LOGOUT,
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    PIN_CHANGE,
    ACCOUNT_LOCK,
    ACCOUNT_UNLOCK
}
