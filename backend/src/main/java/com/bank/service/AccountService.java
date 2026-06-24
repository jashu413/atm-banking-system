package com.bank.service;

import com.bank.domain.AuditAction;
import com.bank.domain.BankAccount;
import com.bank.dto.AccountResponse;
import com.bank.exception.AccountLockedException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InvalidPinException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import com.bank.util.PinPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Orchestrates single-account operations (details, deposit, withdraw, change-PIN).
 *
 * <p>Ownership enforcement: every customer-facing method calls {@link #requireOwnedAccount} which
 * uses a SQL join through {@code Customer → UserAccount} to verify that the requested account
 * belongs to the authenticated principal. A non-owned account is indistinguishable from a missing
 * one (both return 404) to prevent account-number enumeration.
 *
 * <p>Daily withdrawal limit: computed via a SQL {@code SUM} aggregate in
 * {@link TransactionRepository#sumWithdrawalsToday} instead of loading the full transaction
 * collection into memory, eliminating the N+1 issue in the original in-memory implementation.
 *
 * <p>All public methods return {@link AccountResponse} DTOs so that lazy associations are resolved
 * inside the {@code @Transactional} boundary before the controller sees the result.
 */
@Service
@Transactional
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          PasswordEncoder passwordEncoder,
                          AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    // ── Admin operations (no ownership check) ──────────────────────────────────

    /** Retrieves any account by number. Admin-only — no ownership restriction. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountNumber) {
        return AccountResponse.from(load(accountNumber));
    }

    /** Balance lookup without a full DTO. Used internally and in tests. */
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accountNumber) {
        return load(accountNumber).getBalance();
    }

    // ── Customer operations (ownership enforced) ────────────────────────────────

    /**
     * Returns account details for the authenticated customer. Returns 404 if the account does not
     * exist or does not belong to {@code username}, so callers cannot distinguish between the two.
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public AccountResponse getOwnedAccount(String accountNumber, String username) {
        return AccountResponse.from(requireOwnedAccount(accountNumber, username));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public AccountResponse deposit(String accountNumber, String username, BigDecimal amount) {
        try {
            BankAccount account = requireOwnedAccount(accountNumber, username);
            ensureNotLocked(account);
            account.deposit(amount);
            AccountResponse response = AccountResponse.from(accountRepository.save(account));
            auditService.success(AuditAction.DEPOSIT, username, accountNumber, null, "Deposit completed.");
            log.info("Deposit completed for username={}, accountNumber={}", username, accountNumber);
            return response;
        } catch (RuntimeException ex) {
            auditService.failure(AuditAction.DEPOSIT, username, accountNumber, null, ex.getMessage());
            log.warn("Deposit failed for username={}, accountNumber={}: {}", username, accountNumber, ex.getMessage());
            throw ex;
        }
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public AccountResponse withdraw(String accountNumber, String username, String pin, BigDecimal amount) {
        try {
            BankAccount account = requireOwnedAccount(accountNumber, username);
            ensureNotLocked(account);
            verifyPin(account, pin);
            BigDecimal withdrawnToday = transactionRepository.sumWithdrawalsToday(
                    accountNumber, LocalDate.now().atStartOfDay());
            account.withdraw(amount, withdrawnToday);
            AccountResponse response = AccountResponse.from(accountRepository.save(account));
            auditService.success(AuditAction.WITHDRAWAL, username, accountNumber, null, "Withdrawal completed.");
            log.info("Withdrawal completed for username={}, accountNumber={}", username, accountNumber);
            return response;
        } catch (RuntimeException ex) {
            auditService.failure(AuditAction.WITHDRAWAL, username, accountNumber, null, ex.getMessage());
            log.warn("Withdrawal failed for username={}, accountNumber={}: {}", username, accountNumber, ex.getMessage());
            throw ex;
        }
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public AccountResponse changePin(String accountNumber, String username, String oldPin, String newPin) {
        try {
            PinPolicy.validateFormat(newPin);
            BankAccount account = requireOwnedAccount(accountNumber, username);
            ensureNotLocked(account);
            verifyPin(account, oldPin);
            account.changePin(passwordEncoder.encode(newPin));
            AccountResponse response = AccountResponse.from(accountRepository.save(account));
            auditService.success(AuditAction.PIN_CHANGE, username, accountNumber, null, "PIN changed.");
            log.info("PIN changed for username={}, accountNumber={}", username, accountNumber);
            return response;
        } catch (RuntimeException ex) {
            auditService.failure(AuditAction.PIN_CHANGE, username, accountNumber, null, ex.getMessage());
            log.warn("PIN change failed for username={}, accountNumber={}: {}", username, accountNumber, ex.getMessage());
            throw ex;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Loads an account that must be owned by {@code username}. Returns 404 for both missing and
     * non-owned accounts — intentional: callers cannot distinguish the two.
     */
    private BankAccount requireOwnedAccount(String accountNumber, String username) {
        return accountRepository.findByAccountNumberAndOwner(accountNumber, username)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private BankAccount load(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
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
