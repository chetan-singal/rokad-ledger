package com.rokad.service.backup;

import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerAudit;
import com.rokad.entity.Transaction;
import com.rokad.repository.AuditRepository;
import com.rokad.repository.LedgerRepository;
import com.rokad.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Reads through the repositories and writes with plain SQL, because a restore must keep every
// id, timestamp and link exactly as the file has them. The statements run on the connection of
// the surrounding transaction, so a failure anywhere rolls all of them back together.
@Component
public class JpaBackupStore implements BackupStore {

  private final LedgerRepository ledgerRepository;
  private final TransactionRepository transactionRepository;
  private final AuditRepository auditRepository;
  private final JdbcTemplate jdbc;
  private final EntityManager entityManager;

  public JpaBackupStore(
      LedgerRepository ledgerRepository,
      TransactionRepository transactionRepository,
      AuditRepository auditRepository,
      JdbcTemplate jdbc,
      EntityManager entityManager) {
    this.ledgerRepository = ledgerRepository;
    this.transactionRepository = transactionRepository;
    this.auditRepository = auditRepository;
    this.jdbc = jdbc;
    this.entityManager = entityManager;
  }

  @Override
  public BackupFile.Counts currentCounts() {
    return new BackupFile.Counts(
        (int) ledgerRepository.count(),
        (int) transactionRepository.count(),
        (int) auditRepository.count());
  }

  @Override
  public Content read() {
    // Start from what the database holds, not from entities already in memory: an audit row
    // saved a moment ago has no timestamp until it is read back.
    entityManager.flush();
    entityManager.clear();
    List<BackupFile.LedgerRow> ledgers =
        ledgerRepository.findAll().stream()
            .sorted(Comparator.comparing(Ledger::getLedgerId))
            .map(
                l ->
                    new BackupFile.LedgerRow(
                        l.getLedgerId(),
                        l.getLedgerName(),
                        l.getStartDate(),
                        l.getEndDate(),
                        l.getOpeningBalance(),
                        l.getTotalDebit(),
                        l.getTotalCredit(),
                        l.getClosingBalance(),
                        l.getStatus().name(),
                        l.getCreatedAt(),
                        l.getClosedAt(),
                        l.getPreviousLedgerId(),
                        l.getNextLedgerId()))
            .toList();
    List<BackupFile.TransactionRow> transactions =
        transactionRepository.findAll().stream()
            .sorted(Comparator.comparing(Transaction::getTransactionId))
            .map(
                t ->
                    new BackupFile.TransactionRow(
                        t.getTransactionId(),
                        t.getLedger().getLedgerId(),
                        t.getTransactionDate(),
                        t.getTransactionType().name(),
                        t.getAmount(),
                        t.getReason(),
                        t.getCreatedAt(),
                        t.getUpdatedAt()))
            .toList();
    List<BackupFile.AuditRow> audit =
        auditRepository.findAll().stream()
            .sorted(Comparator.comparing(LedgerAudit::getAuditId))
            .map(
                a ->
                    new BackupFile.AuditRow(
                        a.getAuditId(),
                        a.getAction().name(),
                        a.getEntityType(),
                        a.getEntityId(),
                        a.getPerformedAt(),
                        a.getDetails()))
            .toList();
    return new Content(ledgers, transactions, audit);
  }

  @Override
  public void replaceAll(Content content) {
    entityManager.flush();

    jdbc.update("DELETE FROM ledger_transaction");
    jdbc.update("DELETE FROM ledger_audit");
    // Ledgers point at each other, so the links go first, then the rows.
    jdbc.update("UPDATE ledger SET previous_ledger_id = NULL, next_ledger_id = NULL");
    jdbc.update("DELETE FROM ledger");

    jdbc.batchUpdate(
        "INSERT INTO ledger (ledger_id, ledger_name, start_date, end_date, opening_balance,"
            + " total_debit, total_credit, closing_balance, status, created_at, closed_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        content.ledgers().stream()
            .map(
                l ->
                    new Object[] {
                      l.ledgerId(),
                      l.ledgerName(),
                      l.startDate(),
                      l.endDate(),
                      l.openingBalance(),
                      l.totalDebit(),
                      l.totalCredit(),
                      l.closingBalance(),
                      l.status(),
                      l.createdAt(),
                      l.closedAt()
                    })
            .toList());
    jdbc.batchUpdate(
        "UPDATE ledger SET previous_ledger_id = ?, next_ledger_id = ? WHERE ledger_id = ?",
        content.ledgers().stream()
            .map(l -> new Object[] {l.previousLedgerId(), l.nextLedgerId(), l.ledgerId()})
            .toList());
    jdbc.batchUpdate(
        "INSERT INTO ledger_transaction (transaction_id, ledger_id, transaction_date,"
            + " transaction_type, amount, reason, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        content.transactions().stream()
            .map(
                t ->
                    new Object[] {
                      t.transactionId(),
                      t.ledgerId(),
                      t.transactionDate(),
                      t.transactionType(),
                      t.amount(),
                      t.reason(),
                      t.createdAt(),
                      t.updatedAt()
                    })
            .toList());
    jdbc.batchUpdate(
        "INSERT INTO ledger_audit (audit_id, action, entity_type, entity_id, performed_at,"
            + " details) VALUES (?, ?, ?, ?, ?, ?)",
        content.audit().stream()
            .map(
                a ->
                    new Object[] {
                      a.auditId(),
                      a.action(),
                      a.entityType(),
                      a.entityId(),
                      a.performedAt(),
                      a.details()
                    })
            .toList());

    // Nothing loaded before the restore may survive it.
    entityManager.clear();
  }
}
