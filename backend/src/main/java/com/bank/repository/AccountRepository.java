package com.bank.repository;

import com.bank.domain.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link BankAccount} (and its subclasses via single-table
 * inheritance). {@code findByAccountNumber} is the primary lookup, replacing the console's
 * in-memory account-number map.
 */
public interface AccountRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);
}
