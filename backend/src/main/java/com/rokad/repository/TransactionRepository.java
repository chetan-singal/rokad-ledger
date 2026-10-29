package com.rokad.repository;

import com.rokad.entity.Transaction;
import com.rokad.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  List<Transaction> findByLedger_LedgerIdOrderByTransactionDateAscTransactionIdAsc(Long ledgerId);

  // Newest first; the Pageable only supplies the limit (Home's "recent activity").
  List<Transaction> findByLedger_LedgerIdOrderByTransactionDateDescTransactionIdDesc(
      Long ledgerId, Pageable pageable);

  @Query(
      "SELECT t FROM Transaction t "
          + "WHERE t.ledger.ledgerId = :ledgerId "
          + "AND t.transactionDate BETWEEN :startDate AND :endDate "
          + "ORDER BY t.transactionDate ASC, t.transactionId ASC")
  List<Transaction> findByLedgerAndDateRange(
      @Param("ledgerId") Long ledgerId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  @Query(
      "SELECT COUNT(t) > 0 FROM Transaction t WHERE t.ledger.ledgerId = :ledgerId "
          + "AND t.transactionDate = :transactionDate AND t.transactionType = :transactionType "
          + "AND t.amount = :amount AND t.reason = :reason AND t.createdAt > :cutoff")
  boolean existsRecentDuplicate(
      @Param("ledgerId") Long ledgerId,
      @Param("transactionDate") LocalDate transactionDate,
      @Param("transactionType") TransactionType transactionType,
      @Param("amount") BigDecimal amount,
      @Param("reason") String reason,
      @Param("cutoff") LocalDateTime cutoff);
}
