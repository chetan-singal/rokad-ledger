package com.rokad.repository;

import com.rokad.entity.Transaction;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  List<Transaction> findByLedger_LedgerIdOrderByTransactionDateAscTransactionIdAsc(Long ledgerId);

  @Query(
      "SELECT t FROM Transaction t "
          + "WHERE t.ledger.ledgerId = :ledgerId "
          + "AND t.transactionDate BETWEEN :startDate AND :endDate "
          + "ORDER BY t.transactionDate ASC, t.transactionId ASC")
  List<Transaction> findByLedgerAndDateRange(
      @Param("ledgerId") Long ledgerId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);
}
