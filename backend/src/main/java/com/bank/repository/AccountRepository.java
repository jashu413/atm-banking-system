package com.bank.repository;

import com.bank.domain.BankAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link BankAccount} (and its subclasses via single-table
 * inheritance). {@code findByAccountNumber} is the primary lookup for admin operations; customer
 * operations use {@code findByAccountNumberAndOwner} which enforces ownership by joining through
 * the Customer → UserAccount relationship.
 */
public interface AccountRepository extends JpaRepository<BankAccount, Long> {

    /** Admin / internal lookup — no ownership restriction. */
    Optional<BankAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    /** Returns all accounts (admin use: list all customers). */
    List<BankAccount> findAll();

    /**
     * Customer-facing lookup: returns the account only when it belongs to the authenticated user,
     * preventing IDOR. The JOIN through {@code customer.user.username} is resolved entirely in SQL
     * so the ownership check does not require the collection to be loaded in Java.
     */
    @Query("SELECT a FROM BankAccount a " +
           "JOIN a.customer c " +
           "JOIN c.user u " +
           "WHERE a.accountNumber = :accountNumber AND u.username = :username")
    Optional<BankAccount> findByAccountNumberAndOwner(
            @Param("accountNumber") String accountNumber,
            @Param("username") String username);

    /**
     * Loads an account under a pessimistic write lock ({@code SELECT ... FOR UPDATE}) — used by
     * {@link com.bank.service.TransferService} for the target account (no ownership required;
     * anyone can receive a transfer). Callers must acquire locks in a deterministic order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BankAccount a WHERE a.accountNumber = :accountNumber")
    Optional<BankAccount> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    /**
     * Pessimistic write lock with ownership enforcement — used for the <em>source</em> account in
     * a transfer. Combining the lock and the ownership check into a single query prevents a window
     * between the two operations where another thread could modify the account.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BankAccount a " +
           "JOIN a.customer c " +
           "JOIN c.user u " +
           "WHERE a.accountNumber = :accountNumber AND u.username = :username")
    Optional<BankAccount> findByAccountNumberAndOwnerForUpdate(
            @Param("accountNumber") String accountNumber,
            @Param("username") String username);
}
