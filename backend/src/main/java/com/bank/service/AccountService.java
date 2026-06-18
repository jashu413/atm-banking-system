package com.bank.service;

import com.bank.domain.BankAccount;
import com.bank.exception.AccountLockedException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InvalidPinException;
import com.bank.repository.AccountRepository;
import com.bank.util.PinPolicy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Orchestrates single-account operations (balance, deposit, withdraw, change-PIN), replacing the
 * account-facing half of the console {@code ATMService}.
 *
 * <p>The business rules themselves stay in the rich {@link BankAccount} domain model; this service
 * owns the transaction boundary, the account lookup, the lock/PIN guards, and persistence.
 * {@code @Version} optimistic locking on the entity guards against lost updates under concurrency.
 */
@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Transactional(readOnly = true)
    public BankAccount getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accountNumber) {
        return getAccount(accountNumber).getBalance();
    }

    /** Deposits cash. No PIN required, mirroring the console flow and the REST design. */
    @PreAuthorize("hasRole('CUSTOMER')")
    public BankAccount deposit(String accountNumber, BigDecimal amount) {
        BankAccount account = getAccount(accountNumber);
        ensureNotLocked(account);
        account.deposit(amount);
        return accountRepository.save(account);
    }

    /** Withdraws cash after verifying the transaction PIN and the daily limit (enforced in the domain). */
    @PreAuthorize("hasRole('CUSTOMER')")
    public BankAccount withdraw(String accountNumber, String pin, BigDecimal amount) {
        BankAccount account = getAccount(accountNumber);
        ensureNotLocked(account);
        verifyPin(account, pin);
        account.withdraw(amount);
        return accountRepository.save(account);
    }

    /** Changes the transaction PIN after verifying the current one. The new PIN is hashed before storage. */
    @PreAuthorize("hasRole('CUSTOMER')")
    public BankAccount changePin(String accountNumber, String oldPin, String newPin) {
        PinPolicy.validateFormat(newPin);
        BankAccount account = getAccount(accountNumber);
        ensureNotLocked(account);
        verifyPin(account, oldPin);
        account.changePin(passwordEncoder.encode(newPin));
        return accountRepository.save(account);
    }

    private void ensureNotLocked(BankAccount account) {
        if (account.isLocked()) {
            throw new AccountLockedException(account.getAccountNumber());
        }
    }

    private void verifyPin(BankAccount account, String rawPin) {
        if (!passwordEncoder.matches(rawPin, account.getPinHash())) {
            throw new InvalidPinException("Incorrect PIN.");
        }
    }
}
