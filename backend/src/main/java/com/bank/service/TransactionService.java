package com.bank.service;

import com.bank.domain.Transaction;
import com.bank.exception.AccountNotFoundException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only access to the append-only transaction ledger (full history and mini-statement),
 * replacing the history half of the console {@code ATMService}.
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    /** Full history for an account, most recent first. */
    public List<Transaction> getHistory(String accountNumber) {
        ensureAccountExists(accountNumber);
        return transactionRepository
                .findByAccount_AccountNumberOrderByCreatedAtDescIdDesc(accountNumber);
    }

    /** The most recent {@code count} transactions for an account. */
    public List<Transaction> getMiniStatement(String accountNumber, int count) {
        return getHistory(accountNumber).stream()
                .limit(Math.max(count, 0))
                .toList();
    }

    private void ensureAccountExists(String accountNumber) {
        if (!accountRepository.existsByAccountNumber(accountNumber)) {
            throw new AccountNotFoundException(accountNumber);
        }
    }
}
