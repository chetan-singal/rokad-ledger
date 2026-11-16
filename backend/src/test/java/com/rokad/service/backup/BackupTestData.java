package com.rokad.service.backup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// A small valid backup that the tests change one thing at a time. Ledger 1 is closed (opening
// 1000.00, credit 500.00, debit 200.00, closing 1300.00); ledger 2 is active and opens with
// 1300.00 and holds one credit of 100.00.
final class BackupTestData {

  static final LocalDateTime AT = LocalDateTime.of(2026, 11, 5, 10, 0, 0);

  private BackupTestData() {}

  static BigDecimal money(String value) {
    return new BigDecimal(value);
  }

  static BackupFile.LedgerRow closedLedger() {
    return new BackupFile.LedgerRow(
        1L,
        "October",
        LocalDate.of(2026, 10, 1),
        LocalDate.of(2026, 10, 31),
        money("1000.00"),
        money("200.00"),
        money("500.00"),
        money("1300.00"),
        "CLOSED",
        AT.minusDays(40),
        AT.minusDays(4),
        null,
        2L);
  }

  static BackupFile.LedgerRow activeLedger() {
    return new BackupFile.LedgerRow(
        2L,
        null,
        LocalDate.of(2026, 11, 1),
        null,
        money("1300.00"),
        money("0.00"),
        money("100.00"),
        null,
        "ACTIVE",
        AT.minusDays(4),
        null,
        1L,
        null);
  }

  static BackupFile.TransactionRow entry(
      long id, long ledgerId, String date, String type, String amount) {
    return new BackupFile.TransactionRow(
        id, ledgerId, LocalDate.parse(date), type, money(amount), "Entry " + id, AT, AT);
  }

  static List<BackupFile.TransactionRow> entries() {
    return List.of(
        entry(1, 1, "2026-10-02", "CREDIT", "500.00"),
        entry(2, 1, "2026-10-03", "DEBIT", "200.00"),
        entry(3, 2, "2026-11-02", "CREDIT", "100.00"));
  }

  static List<BackupFile.AuditRow> audit() {
    return List.of(
        new BackupFile.AuditRow(1L, "TRANSACTION_CREATE", "TRANSACTION", 1L, AT, "first"),
        new BackupFile.AuditRow(2L, "LEDGER_CLOSE", "LEDGER", 1L, AT, null));
  }

  /** Counts and checksum are worked out here, so a test only has to supply what it changes. */
  static BackupFile seal(
      List<BackupFile.LedgerRow> ledgers,
      List<BackupFile.TransactionRow> transactions,
      List<BackupFile.AuditRow> audit) {
    BackupFile unsigned =
        new BackupFile(
            BackupFile.FORMAT,
            BackupFile.CURRENT_VERSION,
            "0.1.0",
            AT,
            new BackupFile.Counts(ledgers.size(), transactions.size(), audit.size()),
            ledgers,
            transactions,
            audit,
            null);
    return withChecksum(unsigned, BackupChecksum.compute(unsigned));
  }

  static BackupFile valid() {
    return seal(List.of(closedLedger(), activeLedger()), entries(), audit());
  }

  static BackupFile withChecksum(BackupFile file, String checksum) {
    return new BackupFile(
        file.format(),
        file.formatVersion(),
        file.appVersion(),
        file.createdAt(),
        file.counts(),
        file.ledgers(),
        file.transactions(),
        file.audit(),
        checksum);
  }
}
