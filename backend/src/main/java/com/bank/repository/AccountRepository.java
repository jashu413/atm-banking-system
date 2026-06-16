package com.bank.repository;

import com.bank.domain.BankAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data repository for {@link BankAccount} (and its subclasses via single-table
 * inheritance). {@code findByAccountNumber} is the primary lookup, replacing the console's
 * in-memory account-number map.
 */
public interface AccountRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Loads an account under a pessimistic write lock ({@code SELECT ... FOR UPDATE}). Used by
     * {@code TransferService} to serialise the debit+credit critical section and prevent the
     * lost-update / double-spend race that the console application avoided with
     * {@code synchronized}. Callers must lock accounts in a deterministic order to avoid deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccount a where a.accountNumber = :accountNumber")
    Optional<BankAccount> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}
