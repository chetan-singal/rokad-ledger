package com.rokad.dto;

import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// endDate, closingBalance and closedAt are empty while a ledger is active (SRS 13.1).
public record LedgerSummaryDTO(
    Long ledgerId,
    String ledgerName,
    LocalDate startDate,
    LocalDate endDate,
    LedgerStatus status,
    BigDecimal openingBalance,
    BigDecimal currentBalance,
    BigDecimal totalDebit,
    BigDecimal totalCredit,
    BigDecimal closingBalance,
    long transactionCount,
    LocalDateTime closedAt) {

  // A closed ledger is described by the figures stored when it was closed. They are never
  // recalculated from its entries (SRS 10.3).
  public static LedgerSummaryDTO closed(Ledger ledger, long transactionCount) {
    return new LedgerSummaryDTO(
        ledger.getLedgerId(),
        ledger.getLedgerName(),
        ledger.getStartDate(),
        ledger.getEndDate(),
        ledger.getStatus(),
        ledger.getOpeningBalance(),
        ledger.getClosingBalance(),
        ledger.getTotalDebit(),
        ledger.getTotalCredit(),
        ledger.getClosingBalance(),
        transactionCount,
        ledger.getClosedAt());
  }
}
