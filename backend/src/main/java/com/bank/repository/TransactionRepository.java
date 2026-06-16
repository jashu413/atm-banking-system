package com.bank.repository;

import com.bank.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for the append-only {@link Transaction} ledger. */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** Most-recent-first history for an account, suitable for statement queries. */
    List<Transaction> findByAccount_AccountNumberOrderByCreatedAtDescIdDesc(String accountNumber);
}
