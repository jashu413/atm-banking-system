package com.bank.exception;

/**
 * Thrown when an operation is attempted on a locked account. Maps to 423 Locked.
 *
 * <p>The console application locked an account after three failed PIN attempts; that policy
 * moves to the security layer in Phase 3, but the lock state itself lives on the account.
 */
public class AccountLockedException extends BankingException {
    public AccountLockedException(String accountNumber) {
        super("Account " + accountNumber + " is locked. Please contact your branch.");
    }
}
