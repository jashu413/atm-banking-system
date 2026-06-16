package com.bank.exception;

/** Thrown when no account exists for a given account number. Maps to 404 Not Found. */
public class AccountNotFoundException extends BankingException {
    public AccountNotFoundException(String accountNumber) {
        super("No account found for number " + accountNumber + ".");
    }
}
