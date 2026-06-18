package com.bank.service;

import com.bank.domain.BankAccount;
import com.bank.domain.SavingsAccount;
import com.bank.domain.TransactionType;
import com.bank.exception.AccountLockedException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidPinException;
import com.bank.repository.AccountRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccountService}. The repository is mocked and the rich {@link BankAccount}
 * domain object is real; a real {@link BCryptPasswordEncoder} verifies that the service hashes and
 * checks PINs correctly (PINs are stored hashed, never in plaintext).
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String ACCT = "1001001001";
    private static final String PIN = "1234";

    @Mock
    private AccountRepository accountRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, passwordEncoder);
    }

    private SavingsAccount account(String balance, String limit) {
        return new SavingsAccount(ACCT, passwordEncoder.encode(PIN),
                new BigDecimal(balance), new BigDecimal(limit));
    }

    private void givenAccount(BankAccount account) {
        when(accountRepository.findByAccountNumber(ACCT)).thenReturn(Optional.of(account));
        lenient().when(accountRepository.save(any(BankAccount.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void depositIncreasesBalanceAndPersists() {
        BankAccount acct = account("100.00", "500.00");
        givenAccount(acct);

        BankAccount result = accountService.deposit(ACCT, new BigDecimal("50.00"));

        assertThat(result.getBalance()).isEqualByComparingTo("150.00");
        verify(accountRepository).save(acct);
    }

    @Test
    void withdrawWithCorrectPinSucceeds() {
        BankAccount acct = account("100.00", "500.00");
        givenAccount(acct);

        accountService.withdraw(ACCT, PIN, new BigDecimal("40.00"));

        assertThat(acct.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void withdrawWithWrongPinIsRejectedAndNotPersisted() {
        BankAccount acct = account("100.00", "500.00");
        givenAccount(acct);

        assertThatThrownBy(() -> accountService.withdraw(ACCT, "0000", new BigDecimal("40.00")))
                .isInstanceOf(InvalidPinException.class);

        assertThat(acct.getBalance()).isEqualByComparingTo("100.00");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void withdrawBeyondBalanceFails() {
        BankAccount acct = account("30.00", "500.00");
        givenAccount(acct);

        assertThatThrownBy(() -> accountService.withdraw(ACCT, PIN, new BigDecimal("40.00")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void operationsOnLockedAccountAreRejected() {
        BankAccount acct = account("100.00", "500.00");
        acct.lock();
        givenAccount(acct);

        assertThatThrownBy(() -> accountService.deposit(ACCT, new BigDecimal("10.00")))
                .isInstanceOf(AccountLockedException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void changePinVerifiesOldPinHashesNewOneAndRecordsChange() {
        BankAccount acct = account("100.00", "500.00");
        givenAccount(acct);

        accountService.changePin(ACCT, PIN, "9999");

        // New PIN is stored hashed (not plaintext) and verifiable via the encoder.
        assertThat(acct.getPinHash()).isNotEqualTo("9999");
        assertThat(passwordEncoder.matches("9999", acct.getPinHash())).isTrue();
        assertThat(acct.getTransactions().get(0).getType()).isEqualTo(TransactionType.PIN_CHANGE);
    }

    @Test
    void changePinRejectsMalformedNewPin() {
        // Format is validated before any account lookup.
        assertThatThrownBy(() -> accountService.changePin(ACCT, PIN, "12"))
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
