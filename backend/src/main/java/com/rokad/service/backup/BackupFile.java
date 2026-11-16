package com.rokad.service.backup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// The backup file (SRS 11.2, NFR-008): every ledger, every entry and the audit trail, plus a
// checksum, so a cut-short or hand-edited file is recognised before it can replace anything.
// Statuses, types and actions stay plain text here on purpose: a wrong value is then reported by
// the validator in words, instead of failing somewhere inside the JSON reader.
public record BackupFile(
    String format,
    int formatVersion,
    String appVersion,
    LocalDateTime createdAt,
    Counts counts,
    List<LedgerRow> ledgers,
    List<TransactionRow> transactions,
    List<AuditRow> audit,
    String checksum) {

  public static final String FORMAT = "rokad-backup";
  public static final int CURRENT_VERSION = 1;

  public record Counts(int ledgers, int transactions, int audit) {}

  public record LedgerRow(
      Long ledgerId,
      String ledgerName,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal openingBalance,
      BigDecimal totalDebit,
      BigDecimal totalCredit,
      BigDecimal closingBalance,
      String status,
      LocalDateTime createdAt,
      LocalDateTime closedAt,
      Long previousLedgerId,
      Long nextLedgerId) {}

  public record TransactionRow(
      Long transactionId,
      Long ledgerId,
      LocalDate transactionDate,
      String transactionType,
      BigDecimal amount,
      String reason,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {}

  public record AuditRow(
      Long auditId,
      String action,
      String entityType,
      Long entityId,
      LocalDateTime performedAt,
      String details) {}
}
