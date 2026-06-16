package com.bank.service;

import com.bank.domain.BankAccount;
import com.bank.domain.SavingsAccount;
import com.bank.domain.TransactionType;
import com.bank.exception.AccountLockedException;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidPinException;
import com.bank.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Unit tests for {@link AccountService}. The repository is mocked; the rich {@link BankAccount}
 * domain object is real, so these tests exercise the service's orchestration (lookup, lock/PIN
 * guards, persistence) on top of genuine domain behaviour.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String ACCT = "1001001001";

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private SavingsAccount account(String balance, String limit) {
        return new SavingsAccount(ACCT, "1234", new BigDecimal(balance), new BigDecimal(limit));
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

        accountService.withdraw(ACCT, "1234", new BigDecimal("40.00"));

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

        assertThatThrownBy(() -> accountService.withdraw(ACCT, "1234", new BigDecimal("40.00")))
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
    void changePinVerifiesOldPinAndRecordsChange() {
        BankAccount acct = account("100.00", "500.00");
        givenAccount(acct);

        accountService.changePin(ACCT, "1234", "9999");

        assertThat(acct.pinMatches("9999")).isTrue();
        assertThat(acct.getTransactions().get(0).getType()).isEqualTo(TransactionType.PIN_CHANGE);
    }

    @Test
    void missingAccountThrowsNotFound() {
        when(accountRepository.findByAccountNumber("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance("nope"))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
