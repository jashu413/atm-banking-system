package com.bank.domain;

import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;
import com.bank.exception.InvalidPinException;
import com.bank.exception.WithdrawalLimitExceededException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests for the domain rules (no persistence). Ported from the console
 * application's {@code BankAccountTest}, with extra coverage for the mini statement and
 * the transfer primitives.
 */
class BankAccountTest {

    private SavingsAccount newAccount(String balance, String limit) {
        return new SavingsAccount("1001", "1234", new BigDecimal(balance), new BigDecimal(limit));
    }

    @Test
    void depositAddsMoneyAndRecordsTransaction() {
        BankAccount account = newAccount("100.00", "500.00");

        account.deposit(new BigDecimal("50.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("150.00");
        assertThat(account.getTransactions()).hasSize(1);
        assertThat(account.getTransactions().get(0).getType()).isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    void depositMustBePositive() {
        BankAccount account = newAccount("100.00", "500.00");

        assertThrows(InvalidAmountException.class, () -> account.deposit(BigDecimal.ZERO));
    }

    @Test
    void withdrawalFailsWhenBalanceIsInsufficient() {
        BankAccount account = newAccount("100.00", "500.00");

        assertThrows(InsufficientFundsException.class, () -> account.withdraw(new BigDecimal("150.00")));
    }

    @Test
    void withdrawalFailsWhenDailyLimitIsExceeded() {
        BankAccount account = newAccount("1000.00", "500.00");

        account.withdraw(new BigDecimal("300.00"));

        assertThrows(WithdrawalLimitExceededException.class,
                () -> account.withdraw(new BigDecimal("250.00")));
    }

    @Test
    void pinMustHaveExactlyFourDigits() {
        BankAccount account = newAccount("100.00", "500.00");

        assertThrows(InvalidPinException.class, () -> account.changePin("12345"));
    }

    @Test
    void changePinSucceedsAndIsRecorded() {
        BankAccount account = newAccount("100.00", "500.00");

        account.changePin("9999");

        assertThat(account.pinMatches("9999")).isTrue();
        assertThat(account.getTransactions()).hasSize(1);
        assertThat(account.getTransactions().get(0).getType()).isEqualTo(TransactionType.PIN_CHANGE);
    }

    @Test
    void miniStatementReturnsMostRecentTransactions() {
        BankAccount account = newAccount("1000.00", "5000.00");
        account.deposit(new BigDecimal("10.00"));
        account.deposit(new BigDecimal("20.00"));
        account.deposit(new BigDecimal("30.00"));
        account.deposit(new BigDecimal("40.00"));

        List<Transaction> last2 = account.getMiniStatement(2);

        assertThat(last2).hasSize(2);
        assertThat(last2.get(0).getAmount()).isEqualByComparingTo("30.00");
        assertThat(last2.get(1).getAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void transferPrimitivesMoveMoneyAndRecordBothSides() {
        BankAccount source = new SavingsAccount("1001", "1234", new BigDecimal("1000.00"), new BigDecimal("5000.00"));
        BankAccount target = new CurrentAccount("2002", "2345", new BigDecimal("500.00"), new BigDecimal("5000.00"));

        source.debitForTransfer(new BigDecimal("200.00"), target.getAccountNumber());
        target.creditFromTransfer(new BigDecimal("200.00"), source.getAccountNumber());

        assertThat(source.getBalance()).isEqualByComparingTo("800.00");
        assertThat(target.getBalance()).isEqualByComparingTo("700.00");
        assertThat(source.getTransactions().get(0).getType()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(source.getTransactions().get(0).getRelatedAccount()).isEqualTo("2002");
        assertThat(target.getTransactions().get(0).getType()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(target.getTransactions().get(0).getRelatedAccount()).isEqualTo("1001");
    }
}
