package com.atm.model;

import com.atm.exception.InsufficientFundsException;
import com.atm.exception.InvalidAmountException;
import com.atm.exception.InvalidPinException;
import com.atm.exception.WithdrawalLimitExceededException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BankAccount {
    private final String accountNumber;
    private String pin;
    private BigDecimal balance;
    private final BigDecimal dailyWithdrawalLimit;
    private BigDecimal withdrawnToday = BigDecimal.ZERO;
    private LocalDate withdrawalDate = LocalDate.now();
    private boolean locked;
    private final List<Transaction> transactions = new ArrayList<>();

    protected BankAccount(String accountNumber, String pin, BigDecimal openingBalance, BigDecimal dailyWithdrawalLimit) {
        validatePin(pin);
        validateNonNegative(openingBalance, "Opening balance cannot be negative.");
        validatePositive(dailyWithdrawalLimit, "Daily withdrawal limit must be positive.");
        this.accountNumber = accountNumber;
        this.pin = pin;
        this.balance = openingBalance;
        this.dailyWithdrawalLimit = dailyWithdrawalLimit;
    }

    public abstract String getAccountType();

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getMaskedAccountNumber() {
        if (accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    public boolean pinMatches(String enteredPin) {
        return pin.equals(enteredPin);
    }

    public synchronized void changePin(String newPin) {
        validatePin(newPin);
        this.pin = newPin;
        addTransaction(TransactionType.PIN_CHANGE, BigDecimal.ZERO, "PIN changed successfully.");
    }

    public synchronized BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getDailyWithdrawalLimit() {
        return dailyWithdrawalLimit;
    }

    public boolean isLocked() {
        return locked;
    }

    public void lock() {
        this.locked = true;
    }

    public synchronized void deposit(BigDecimal amount) {
        validatePositive(amount, "Deposit amount must be positive.");
        balance = balance.add(amount);
        addTransaction(TransactionType.DEPOSIT, amount, "Cash deposit.");
    }

    public synchronized void withdraw(BigDecimal amount) {
        validatePositive(amount, "Withdrawal amount must be positive.");
        // The limit is tracked per calendar day and reset automatically when the date changes.
        resetDailyWithdrawalTrackerIfNeeded();
        if (withdrawnToday.add(amount).compareTo(dailyWithdrawalLimit) > 0) {
            throw new WithdrawalLimitExceededException("Daily withdrawal limit exceeded. Limit: $" + dailyWithdrawalLimit);
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient balance for withdrawal.");
        }
        balance = balance.subtract(amount);
        withdrawnToday = withdrawnToday.add(amount);
        addTransaction(TransactionType.WITHDRAWAL, amount, "Cash withdrawal.");
    }

    public synchronized void debitForTransfer(BigDecimal amount, String targetAccountNumber) {
        validatePositive(amount, "Transfer amount must be positive.");
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient balance for transfer.");
        }
        balance = balance.subtract(amount);
        addTransaction(TransactionType.TRANSFER_OUT, amount, "Transfer to account " + targetAccountNumber + ".");
    }

    public synchronized void creditFromTransfer(BigDecimal amount, String sourceAccountNumber) {
        validatePositive(amount, "Transfer amount must be positive.");
        balance = balance.add(amount);
        addTransaction(TransactionType.TRANSFER_IN, amount, "Transfer from account " + sourceAccountNumber + ".");
    }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public List<Transaction> getMiniStatement(int count) {
        int fromIndex = Math.max(transactions.size() - count, 0);
        return Collections.unmodifiableList(transactions.subList(fromIndex, transactions.size()));
    }

    private void addTransaction(TransactionType type, BigDecimal amount, String description) {
        transactions.add(new Transaction(type, amount, description, balance));
    }

    private void resetDailyWithdrawalTrackerIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(withdrawalDate)) {
            withdrawalDate = today;
            withdrawnToday = BigDecimal.ZERO;
        }
    }

    private static void validatePin(String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new InvalidPinException("PIN must be exactly 4 digits.");
        }
    }

    private static void validatePositive(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(message);
        }
    }

    private static void validateNonNegative(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAmountException(message);
        }
    }
}
