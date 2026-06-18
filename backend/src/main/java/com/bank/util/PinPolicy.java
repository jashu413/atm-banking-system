package com.bank.util;

import com.bank.exception.InvalidPinException;

/**
 * Format policy for transaction PINs. Validates the raw (cleartext) PIN entered by a user before
 * it is hashed; the persisted value is always a BCrypt hash, never the raw PIN.
 *
 * <p>The four-digit rule is ported from the console application's {@code BankAccount.validatePin}.
 */
public final class PinPolicy {

    private PinPolicy() {
    }

    /** @throws InvalidPinException if {@code rawPin} is not exactly four digits. */
    public static void validateFormat(String rawPin) {
        if (rawPin == null || !rawPin.matches("\\d{4}")) {
            throw new InvalidPinException("PIN must be exactly 4 digits.");
        }
    }
}
