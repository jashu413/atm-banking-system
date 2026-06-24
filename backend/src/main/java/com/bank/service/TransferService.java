package com.bank.service;

import com.bank.domain.AuditAction;
import com.bank.domain.BankAccount;
import com.bank.exception.AccountLockedException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InvalidPinException;
import com.bank.exception.InvalidTransferException;
import com.bank.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Moves money between two accounts as a single atomic operation.
 *
 * <p><b>Ownership enforcement (IDOR fix):</b> the source account is looked up via
 * {@link AccountRepository#findByAccountNumberAndOwnerForUpdate}, which joins through
 * {@code Customer → UserAccount} and verifies that the account belongs to {@code username}.
 * A non-owned source account is indistinguishable from a missing one (404). The target account
 * has no ownership restriction — anyone can be the recipient of a transfer.
 *
 * <p><b>Concurrency safety:</b> both accounts are loaded under a pessimistic write lock
 * ({@code SELECT … FOR UPDATE}). Locks are acquired in lexicographic account-number order so that
 * two opposite concurrent transfers can never deadlock.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public TransferService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
                           AuditService auditService) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public void transfer(String sourceAccountNumber, String targetAccountNumber,
                         String username, String pin, BigDecimal amount) {
        try {
            if (sourceAccountNumber.equals(targetAccountNumber)) {
                throw new InvalidTransferException("Cannot transfer to the same account.");
            }

            // Acquire pessimistic locks in a stable lexicographic order to prevent deadlock.
            BankAccount first;
            BankAccount second;
            if (sourceAccountNumber.compareTo(targetAccountNumber) < 0) {
                first = lockWithOwner(sourceAccountNumber, username);
                second = lockTarget(targetAccountNumber);
            } else {
                second = lockTarget(targetAccountNumber);
                first = lockWithOwner(sourceAccountNumber, username);
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
            auditService.success(AuditAction.TRANSFER, username, sourceAccountNumber,
                    targetAccountNumber, "Transfer completed.");
            log.info("Transfer completed for username={}, sourceAccountNumber={}, targetAccountNumber={}",
                    username, sourceAccountNumber, targetAccountNumber);
        } catch (RuntimeException ex) {
            auditService.failure(AuditAction.TRANSFER, username, sourceAccountNumber,
                    targetAccountNumber, ex.getMessage());
            log.warn("Transfer failed for username={}, sourceAccountNumber={}, targetAccountNumber={}: {}",
                    username, sourceAccountNumber, targetAccountNumber, ex.getMessage());
            throw ex;
        }
    }

    /** Source: pessimistic lock + ownership check — prevents IDOR on the debit side. */
    private BankAccount lockWithOwner(String accountNumber, String username) {
        return accountRepository.findByAccountNumberAndOwnerForUpdate(accountNumber, username)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    /** Target: pessimistic lock only — anyone can be a transfer recipient. */
    private BankAccount lockTarget(String accountNumber) {
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
