package com.bank.service;

import com.bank.domain.AuditAction;
import com.bank.domain.BankAccount;
import com.bank.dto.AccountResponse;
import com.bank.exception.AccountNotFoundException;
import com.bank.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Administrative operations over bank accounts. All methods require the {@code ADMIN} role.
 * Unlike {@link AccountService}, these operations carry no ownership restriction and are scoped
 * to back-office use cases (listing accounts, locking/unlocking).
 */
@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public AdminService(AccountRepository accountRepository, AuditService auditService) {
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAllAccounts() {
        return accountRepository.findAll().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountNumber) {
        return AccountResponse.from(load(accountNumber));
    }

    public AccountResponse lockAccount(String accountNumber) {
        try {
            BankAccount account = load(accountNumber);
            account.lock();
            AccountResponse response = AccountResponse.from(accountRepository.save(account));
            auditService.success(AuditAction.ACCOUNT_LOCK, null, accountNumber, null, "Account locked.");
            log.info("Account locked: accountNumber={}", accountNumber);
            return response;
        } catch (RuntimeException ex) {
            auditService.failure(AuditAction.ACCOUNT_LOCK, null, accountNumber, null, ex.getMessage());
            log.warn("Account lock failed: accountNumber={}, reason={}", accountNumber, ex.getMessage());
            throw ex;
        }
    }

    public AccountResponse unlockAccount(String accountNumber) {
        try {
            BankAccount account = load(accountNumber);
            account.unlock();
            AccountResponse response = AccountResponse.from(accountRepository.save(account));
            auditService.success(AuditAction.ACCOUNT_UNLOCK, null, accountNumber, null, "Account unlocked.");
            log.info("Account unlocked: accountNumber={}", accountNumber);
            return response;
        } catch (RuntimeException ex) {
            auditService.failure(AuditAction.ACCOUNT_UNLOCK, null, accountNumber, null, ex.getMessage());
            log.warn("Account unlock failed: accountNumber={}, reason={}", accountNumber, ex.getMessage());
            throw ex;
        }
    }

    private BankAccount load(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }
}
