package com.rokad.service.backup;

import java.util.List;

// The database side of backup and restore. The service decides when to call it and wraps the
// whole of a restore in one transaction, so the store itself never has to undo anything.
public interface BackupStore {

  record Content(
      List<BackupFile.LedgerRow> ledgers,
      List<BackupFile.TransactionRow> transactions,
      List<BackupFile.AuditRow> audit) {}

  /** How much is stored right now. */
  BackupFile.Counts currentCounts();

  /** Everything that is stored, as it is stored. */
  Content read();

  /** Deletes everything and writes exactly this content, keeping ids and timestamps. */
  void replaceAll(Content content);
}
