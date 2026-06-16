package com.bank.service;

import com.bank.domain.BankAccount;
import com.bank.exception.AccountLockedException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InvalidPinException;
import com.bank.repository.AccountRepository;
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

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public BankAccount getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accountNumber) {
        return getAccount(accountNumber).getBalance();
    }

    /** Deposits cash. No PIN required, mirroring the console flow and the REST design. */
    public BankAccount deposit(String accountNumber, BigDecimal amount) {
        BankAccount account = getAccount(accountNumber);
        ensureNotLocked(account);
        account.deposit(amount);
        return accountRepository.save(account);
    }

    /** Withdraws cash after verifying the transaction PIN and the daily limit (enforced in the domain). */
    public BankAccount withdraw(String accountNumber, String pin, BigDecimal amount) {
        BankAccount account = getAccount(accountNumber);
        ensureNotLocked(account);
        verifyPin(account, pin);
        account.withdraw(amount);
        return accountRepository.save(account);
    }

    /** Changes the transaction PIN after verifying the current one. */
    public BankAccount changePin(String accountNumber, String oldPin, String newPin) {
        BankAccount account = getAccount(accountNumber);
        ensureNotLocked(account);
        verifyPin(account, oldPin);
        account.changePin(newPin);
        return accountRepository.save(account);
    }

    private void ensureNotLocked(BankAccount account) {
        if (account.isLocked()) {
            throw new AccountLockedException(account.getAccountNumber());
        }
    }

    private void verifyPin(BankAccount account, String pin) {
        if (!account.pinMatches(pin)) {
            throw new InvalidPinException("Incorrect PIN.");
        }
    }
}
