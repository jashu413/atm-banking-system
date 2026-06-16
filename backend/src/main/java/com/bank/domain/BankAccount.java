package com.bank.domain;

import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;
import com.bank.exception.InvalidPinException;
import com.bank.exception.WithdrawalLimitExceededException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base for bank accounts. Migrated from the console {@code BankAccount}; the business
 * rules (deposit/withdraw/transfer/PIN validation) are preserved as a rich domain model.
 *
 * <p>Persistence notes:
 * <ul>
 *   <li>Single-table inheritance; the {@code account_type} discriminator distinguishes
 *       {@link SavingsAccount} from {@link CurrentAccount}.</li>
 *   <li>The daily withdrawal limit is computed from today's {@code WITHDRAWAL} transactions
 *       rather than a stored counter (no per-day reset needed).</li>
 *   <li>{@code synchronized} was dropped in favour of DB transactions and {@code @Version}
 *       optimistic locking (enforced by the Phase 2 service layer).</li>
 *   <li>The PIN is stored in plaintext in Phase 1; BCrypt hashing arrives in Phase 3.</li>
 * </ul>
 */
@Entity
@Table(name = "accounts")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "account_type", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(nullable = false, length = 100)
    private String pin;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "daily_withdrawal_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyWithdrawalLimit;

    @Column(nullable = false)
    private boolean locked = false;

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Transaction> transactions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected BankAccount() {
        // for JPA
    }

    protected BankAccount(String accountNumber, String pin, BigDecimal openingBalance,
                          BigDecimal dailyWithdrawalLimit) {
        validatePin(pin);
        validateNonNegative(openingBalance, "Opening balance cannot be negative.");
        validatePositive(dailyWithdrawalLimit, "Daily withdrawal limit must be positive.");
        this.accountNumber = accountNumber;
        this.pin = pin;
        this.balance = openingBalance;
        this.dailyWithdrawalLimit = dailyWithdrawalLimit;
    }

    /** Display label for the account type. Implemented by concrete subclasses. */
    public abstract String getAccountType();

    // --- Accessors ---

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getMaskedAccountNumber() {
        if (accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getDailyWithdrawalLimit() {
        return dailyWithdrawalLimit;
    }

    public boolean isLocked() {
        return locked;
    }

    public Long getVersion() {
        return version;
    }

    public Customer getCustomer() {
        return customer;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Package-private: set by {@link Customer#assignAccount(BankAccount)} to wire both sides. */
    void setCustomer(Customer customer) {
        this.customer = customer;
    }

    // --- Behaviour (preserved from the console application) ---

    public boolean pinMatches(String enteredPin) {
        return pin.equals(enteredPin);
    }

    public void changePin(String newPin) {
        validatePin(newPin);
        this.pin = newPin;
        addTransaction(TransactionType.PIN_CHANGE, BigDecimal.ZERO, "PIN changed successfully.", null);
    }

    public void lock() {
        this.locked = true;
    }

    public void deposit(BigDecimal amount) {
        validatePositive(amount, "Deposit amount must be positive.");
        balance = balance.add(amount);
        addTransaction(TransactionType.DEPOSIT, amount, "Cash deposit.", null);
    }

    public void withdraw(BigDecimal amount) {
        validatePositive(amount, "Withdrawal amount must be positive.");
        // Daily limit is derived from today's withdrawals rather than a stored counter.
        if (totalWithdrawnToday().add(amount).compareTo(dailyWithdrawalLimit) > 0) {
            throw new WithdrawalLimitExceededException(
                    "Daily withdrawal limit exceeded. Limit: $" + dailyWithdrawalLimit);
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient balance for withdrawal.");
        }
        balance = balance.subtract(amount);
        addTransaction(TransactionType.WITHDRAWAL, amount, "Cash withdrawal.", null);
    }

    public void debitForTransfer(BigDecimal amount, String targetAccountNumber) {
        validatePositive(amount, "Transfer amount must be positive.");
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient balance for transfer.");
        }
        balance = balance.subtract(amount);
        addTransaction(TransactionType.TRANSFER_OUT, amount,
                "Transfer to account " + targetAccountNumber + ".", targetAccountNumber);
    }

    public void creditFromTransfer(BigDecimal amount, String sourceAccountNumber) {
        validatePositive(amount, "Transfer amount must be positive.");
        balance = balance.add(amount);
        addTransaction(TransactionType.TRANSFER_IN, amount,
                "Transfer from account " + sourceAccountNumber + ".", sourceAccountNumber);
    }

    public List<Transaction> getTransactions() {
        return List.copyOf(transactions);
    }

    public List<Transaction> getMiniStatement(int count) {
        int fromIndex = Math.max(transactions.size() - count, 0);
        return List.copyOf(transactions.subList(fromIndex, transactions.size()));
    }

    private void addTransaction(TransactionType type, BigDecimal amount, String description,
                                String relatedAccount) {
        transactions.add(new Transaction(this, type, amount, balance, description, relatedAccount));
    }

    private BigDecimal totalWithdrawnToday() {
        LocalDate today = LocalDate.now();
        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.WITHDRAWAL)
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().toLocalDate().equals(today))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // --- Validation (preserved from the console application) ---

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
