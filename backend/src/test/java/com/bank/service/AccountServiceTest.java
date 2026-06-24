package com.bank.service;

import com.bank.domain.BankAccount;
import com.bank.domain.SavingsAccount;
import com.bank.domain.TransactionType;
import com.bank.dto.AccountResponse;
import com.bank.exception.AccountLockedException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidPinException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccountService}. Repositories are mocked; the real domain object and a
 * real {@link BCryptPasswordEncoder} verify PIN hashing end-to-end. Ownership-enforcing repo
 * methods are stubbed with a fixed username so tests remain Spring-Security-context-free.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String ACCT = "1001001001";
    private static final String USER = "asha";
    private static final String PIN = "1234";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AuditService auditService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, transactionRepository, passwordEncoder,
                auditService);
    }

    private SavingsAccount account(String balance, String limit) {
        return new SavingsAccount(ACCT, passwordEncoder.encode(PIN),
                new BigDecimal(balance), new BigDecimal(limit));
    }

    /** Stubs both the owned-account lookup (customer path) and the plain lookup (admin path). */
    private void givenAccount(BankAccount account) {
        lenient().when(accountRepository.findByAccountNumberAndOwner(ACCT, USER))
                .thenReturn(Optional.of(account));
        lenient().when(accountRepository.findByAccountNumber(ACCT))
                .thenReturn(Optional.of(account));
        lenient().when(accountRepository.save(any(BankAccount.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void depositIncreasesBalanceAndPersists() {
        BankAccount acct = account("100.00", "500.00");
        givenAccount(acct);

        AccountResponse result = accountService.deposit(ACCT, USER, new BigDecimal("50.00"));

        assertThat(result.balance()).isEqualByComparingTo("150.00");
        verify(accountRepository).save(acct);
    }

    @Test
    void withdrawWithCorrectPinSucceeds() {
        BankAccount acct = account("100.00", "500.00");
        givenAccount(acct);
        when(transactionRepository.sumWithdrawalsToday(eq(ACCT), any()))
                .thenReturn(BigDecimal.ZERO);

        AccountResponse result = accountService.withdraw(ACCT, USER, PIN, new BigDecimal("40.00"));

        assertThat(result.balance()).isEqualByComparingTo("60.00");
    }

    @Test
    void withdrawWithWrongPinIsRejectedAndNotPersisted() {
        BankAccount acct = account("100.00", "500.00");
        givenAccount(acct);
        // PIN check precedes the daily-sum query; lenient so Mockito doesn't flag it as unused
        lenient().when(transactionRepository.sumWithdrawalsToday(eq(ACCT), any()))
                .thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> accountService.withdraw(ACCT, USER, "0000", new BigDecimal("40.00")))
                .isInstanceOf(InvalidPinException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void withdrawBeyondBalanceFails() {
        BankAccount acct = account("30.00", "500.00");
        givenAccount(acct);
        when(transactionRepository.sumWithdrawalsToday(eq(ACCT), any()))
                .thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> accountService.withdraw(ACCT, USER, PIN, new BigDecimal("40.00")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void operationsOnLockedAccountAreRejected() {
        BankAccount acct = account("100.00", "500.00");
        acct.lock();
        givenAccount(acct);

        assertThatThrownBy(() -> accountService.deposit(ACCT, USER, new BigDecimal("10.00")))
                .isInstanceOf(AccountLockedException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void changePinVerifiesOldPinHashesNewOneAndRecordsChange() {
        BankAccount acct = account("100.00", "500.00");
        givenAccount(acct);

        accountService.changePin(ACCT, USER, PIN, "9999");

        assertThat(acct.getPinHash()).isNotEqualTo("9999");
        assertThat(passwordEncoder.matches("9999", acct.getPinHash())).isTrue();
        assertThat(acct.getTransactions().get(0).getType()).isEqualTo(TransactionType.PIN_CHANGE);
    }

    @Test
    void changePinRejectsMalformedNewPin() {
        assertThatThrownBy(() -> accountService.changePin(ACCT, USER, PIN, "12"))
                .isInstanceOf(InvalidPinException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void missingAccountThrowsNotFound() {
        when(accountRepository.findByAccountNumber("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance("nope"))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
