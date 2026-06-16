package com.atm.service;

import com.atm.exception.AuthenticationException;
import com.atm.model.Customer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AuthenticationService {
    public static final int MAX_LOGIN_ATTEMPTS = 3;
    private static final String ADMIN_ID = "admin";
    private static final String ADMIN_PIN = "0000";

    private final Map<String, Customer> customersByAccountNumber;
    private final Map<String, Integer> failedAttemptsByAccountNumber = new HashMap<>();

    public AuthenticationService(Map<String, Customer> customersByAccountNumber) {
        this.customersByAccountNumber = customersByAccountNumber;
    }

    public Optional<Customer> login(String accountNumber, String pin) {
        Customer customer = customersByAccountNumber.get(accountNumber);
        if (customer == null) {
            throw new AuthenticationException("Account number was not found.");
        }
        if (customer.getAccount().isLocked()) {
            throw new AuthenticationException("Account is locked. Please contact the bank.");
        }
        if (!customer.getAccount().pinMatches(pin)) {
            // Failed attempts are stored outside the account so authentication policy stays in this service.
            int failedAttempts = failedAttemptsByAccountNumber.merge(accountNumber, 1, Integer::sum);
            if (failedAttempts >= MAX_LOGIN_ATTEMPTS) {
                customer.getAccount().lock();
                throw new AuthenticationException("Account locked after 3 failed login attempts.");
            }
            throw new AuthenticationException("Invalid PIN. Attempts remaining: " + (MAX_LOGIN_ATTEMPTS - failedAttempts));
        }
        failedAttemptsByAccountNumber.remove(accountNumber);
        return Optional.of(customer);
    }

    public boolean isAdminLogin(String username, String pin) {
        return ADMIN_ID.equalsIgnoreCase(username) && ADMIN_PIN.equals(pin);
    }
}
