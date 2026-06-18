package com.bank.service;

import com.bank.domain.BankAccount;
import com.bank.domain.CurrentAccount;
import com.bank.domain.SavingsAccount;
import com.bank.domain.TransactionType;
import com.bank.exception.AccountLockedException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidPinException;
import com.bank.exception.InvalidTransferException;
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
 * Unit tests for {@link TransferService}. Both accounts are real domain objects returned by a
 * mocked repository under the pessimistic-lock lookup; a real {@link BCryptPasswordEncoder} backs
 * PIN verification. Tests cover atomic money movement, both ledger legs, and the guard conditions.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final String SOURCE = "1001001001";
    private static final String TARGET = "1001001002";
    private static final String SOURCE_PIN = "1234";

    @Mock
    private AccountRepository accountRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(accountRepository, passwordEncoder);
    }

    private void givenAccounts(BankAccount source, BankAccount target) {
        lenient().when(accountRepository.findByAccountNumberForUpdate(SOURCE))
                .thenReturn(Optional.of(source));
        lenient().when(accountRepository.findByAccountNumberForUpdate(TARGET))
                .thenReturn(Optional.of(target));
    }

    private SavingsAccount savings(String number, String balance) {
        return new SavingsAccount(number, passwordEncoder.encode(SOURCE_PIN),
                new BigDecimal(balance), new BigDecimal("25000.00"));
    }

    private CurrentAccount current(String number, String balance) {
        return new CurrentAccount(number, passwordEncoder.encode("2345"),
                new BigDecimal(balance), new BigDecimal("25000.00"));
    }

    @Test
    void transferMovesMoneyAndRecordsBothLegs() {
        BankAccount source = savings(SOURCE, "1000.00");
        BankAccount target = current(TARGET, "500.00");
        givenAccounts(source, target);

        transferService.transfer(SOURCE, TARGET, SOURCE_PIN, new BigDecimal("300.00"));

        assertThat(source.getBalance()).isEqualByComparingTo("700.00");
        assertThat(target.getBalance()).isEqualByComparingTo("800.00");
        assertThat(source.getTransactions().get(0).getType()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(target.getTransactions().get(0).getType()).isEqualTo(TransactionType.TRANSFER_IN);
        verify(accountRepository).save(source);
        verify(accountRepository).save(target);
    }

    @Test
    void transferToSameAccountIsRejected() {
        assertThatThrownBy(() -> transferService.transfer(SOURCE, SOURCE, SOURCE_PIN, new BigDecimal("10.00")))
                .isInstanceOf(InvalidTransferException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void transferWithInsufficientFundsLeavesBothBalancesUnchanged() {
        BankAccount source = savings(SOURCE, "100.00");
        BankAccount target = current(TARGET, "500.00");
        givenAccounts(source, target);

        assertThatThrownBy(() -> transferService.transfer(SOURCE, TARGET, SOURCE_PIN, new BigDecimal("300.00")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        assertThat(target.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void transferWithWrongPinIsRejected() {
        BankAccount source = savings(SOURCE, "1000.00");
        BankAccount target = current(TARGET, "500.00");
        givenAccounts(source, target);

        assertThatThrownBy(() -> transferService.transfer(SOURCE, TARGET, "0000", new BigDecimal("100.00")))
                .isInstanceOf(InvalidPinException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void transferFromLockedAccountIsRejected() {
        BankAccount source = savings(SOURCE, "1000.00");
        source.lock();
        BankAccount target = current(TARGET, "500.00");
        givenAccounts(source, target);

        assertThatThrownBy(() -> transferService.transfer(SOURCE, TARGET, SOURCE_PIN, new BigDecimal("100.00")))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void transferToMissingTargetThrowsNotFound() {
        BankAccount source = savings(SOURCE, "1000.00");
        when(accountRepository.findByAccountNumberForUpdate(SOURCE)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumberForUpdate(TARGET)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(SOURCE, TARGET, SOURCE_PIN, new BigDecimal("100.00")))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
