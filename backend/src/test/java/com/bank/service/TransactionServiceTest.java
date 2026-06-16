package com.bank.service;

import com.bank.domain.BankAccount;
import com.bank.domain.SavingsAccount;
import com.bank.domain.Transaction;
import com.bank.exception.AccountNotFoundException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TransactionService}. The ledger is built through real domain behaviour on
 * a {@link BankAccount}, then served via the mocked repository in most-recent-first order.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final String ACCT = "1001001001";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransactionService transactionService;

    /** Builds a ledger and returns it newest-first, matching the repository's ordering. */
    private List<Transaction> ledgerNewestFirst() {
        BankAccount account = new SavingsAccount(ACCT, "1234",
                new BigDecimal("1000.00"), new BigDecimal("5000.00"));
        account.deposit(new BigDecimal("10.00"));
        account.deposit(new BigDecimal("20.00"));
        account.deposit(new BigDecimal("30.00"));
        List<Transaction> chronological = account.getTransactions();
        return List.of(chronological.get(2), chronological.get(1), chronological.get(0));
    }

    @Test
    void getHistoryReturnsAllTransactionsNewestFirst() {
        when(accountRepository.existsByAccountNumber(ACCT)).thenReturn(true);
        when(transactionRepository.findByAccount_AccountNumberOrderByCreatedAtDescIdDesc(ACCT))
                .thenReturn(ledgerNewestFirst());

        List<Transaction> history = transactionService.getHistory(ACCT);

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void miniStatementLimitsToRequestedCount() {
        lenient().when(accountRepository.existsByAccountNumber(ACCT)).thenReturn(true);
        when(transactionRepository.findByAccount_AccountNumberOrderByCreatedAtDescIdDesc(ACCT))
                .thenReturn(ledgerNewestFirst());

        List<Transaction> mini = transactionService.getMiniStatement(ACCT, 2);

        assertThat(mini).hasSize(2);
        assertThat(mini.get(0).getAmount()).isEqualByComparingTo("30.00");
        assertThat(mini.get(1).getAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void historyForMissingAccountThrowsNotFound() {
        when(accountRepository.existsByAccountNumber("nope")).thenReturn(false);

        assertThatThrownBy(() -> transactionService.getHistory("nope"))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
