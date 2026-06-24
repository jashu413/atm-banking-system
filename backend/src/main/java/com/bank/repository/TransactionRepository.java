package com.bank.repository;

import com.bank.domain.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Spring Data repository for the append-only {@link Transaction} ledger. */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** Most-recent-first history for an account, suitable for statement queries. */
    List<Transaction> findByAccount_AccountNumberOrderByCreatedAtDescIdDesc(String accountNumber);

    /**
     * Most-recent-first slice of the ledger capped by the supplied {@link Pageable}. Used by the
     * mini-statement endpoint to avoid loading the full transaction history into Java memory.
     */
    @Query("SELECT t FROM Transaction t WHERE t.account.accountNumber = :accountNumber " +
           "ORDER BY t.createdAt DESC, t.id DESC")
    List<Transaction> findRecentByAccountNumber(
            @Param("accountNumber") String accountNumber, Pageable pageable);

    /**
     * Aggregates the total amount withdrawn from an account since the start of the current day.
     * Using {@code createdAt >= :startOfDay} is database-portable (works with H2 and MySQL) and
     * avoids loading the entire transaction collection into memory.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.account.accountNumber = :accountNumber " +
           "AND t.type = com.bank.domain.TransactionType.WITHDRAWAL " +
           "AND t.createdAt >= :startOfDay")
    BigDecimal sumWithdrawalsToday(@Param("accountNumber") String accountNumber,
                                   @Param("startOfDay") LocalDateTime startOfDay);
}
