package com.atm;

import com.atm.exception.InsufficientFundsException;
import com.atm.exception.InvalidAmountException;
import com.atm.exception.InvalidPinException;
import com.atm.exception.WithdrawalLimitExceededException;
import com.atm.model.BankAccount;
import com.atm.model.SavingsAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BankAccountTest {
    @Test
    void depositAddsMoneyAndRecordsTransaction() {
        BankAccount account = new SavingsAccount("1001", "1234", new BigDecimal("100.00"), new BigDecimal("500.00"));

        account.deposit(new BigDecimal("50.00"));

        assertEquals(new BigDecimal("150.00"), account.getBalance());
        assertEquals(1, account.getTransactions().size());
    }

    @Test
    void depositMustBePositive() {
        BankAccount account = new SavingsAccount("1001", "1234", new BigDecimal("100.00"), new BigDecimal("500.00"));

        assertThrows(InvalidAmountException.class, () -> account.deposit(BigDecimal.ZERO));
    }

    @Test
    void withdrawalFailsWhenBalanceIsInsufficient() {
        BankAccount account = new SavingsAccount("1001", "1234", new BigDecimal("100.00"), new BigDecimal("500.00"));

        assertThrows(InsufficientFundsException.class, () -> account.withdraw(new BigDecimal("150.00")));
    }

    @Test
    void withdrawalFailsWhenDailyLimitIsExceeded() {
        BankAccount account = new SavingsAccount("1001", "1234", new BigDecimal("1000.00"), new BigDecimal("500.00"));

        account.withdraw(new BigDecimal("300.00"));

        assertThrows(WithdrawalLimitExceededException.class, () -> account.withdraw(new BigDecimal("250.00")));
    }

    @Test
    void pinMustHaveExactlyFourDigits() {
        BankAccount account = new SavingsAccount("1001", "1234", new BigDecimal("100.00"), new BigDecimal("500.00"));

        assertThrows(InvalidPinException.class, () -> account.changePin("12345"));
    }
}
