package com.bank.service;

import com.bank.domain.BankAccount;
import com.bank.exception.AccountLockedException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InvalidPinException;
import com.bank.exception.InvalidTransferException;
import com.bank.repository.AccountRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Moves money between two accounts as a single atomic operation, replacing the transfer half of
 * the console {@code ATMService}.
 *
 * <p>Concurrency safety (the console relied on {@code synchronized}): the debit and credit run
 * inside one {@code @Transactional} boundary, and both accounts are loaded under a pessimistic
 * write lock ({@code SELECT ... FOR UPDATE}). Locks are always acquired in a deterministic order
 * (lexicographic by account number) so two opposite transfers can never deadlock.
 */
@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public TransferService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public void transfer(String sourceAccountNumber, String targetAccountNumber,
                         String pin, BigDecimal amount) {
        if (sourceAccountNumber.equals(targetAccountNumber)) {
            throw new InvalidTransferException("Cannot transfer to the same account.");
        }

        // Lock in a stable order regardless of transfer direction to avoid deadlock.
        BankAccount first;
        BankAccount second;
        if (sourceAccountNumber.compareTo(targetAccountNumber) < 0) {
            first = lock(sourceAccountNumber);
            second = lock(targetAccountNumber);
        } else {
            second = lock(targetAccountNumber);
            first = lock(sourceAccountNumber);
        }

        BankAccount source = sourceAccountNumber.equals(first.getAccountNumber()) ? first : second;
        BankAccount target = source == first ? second : first;

        ensureNotLocked(source);
        ensureNotLocked(target);
        verifyPin(source, pin);

        source.debitForTransfer(amount, target.getAccountNumber());
        target.creditFromTransfer(amount, source.getAccountNumber());

        accountRepository.save(source);
        accountRepository.save(target);
    }

    private BankAccount lock(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
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
