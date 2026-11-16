package com.rokad.service.backup;

import com.rokad.entity.AuditAction;
import com.rokad.entity.Transaction;
import com.rokad.entity.TransactionType;
import com.rokad.service.calculation.BalanceResult;
import com.rokad.service.calculation.FinancialCalculationService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

// Decides whether a backup file can replace the data. It is strict on purpose: a file that
// fails any rule is refused as a whole, before anything is written (TC-047). Every total and
// balance is recomputed with the one FinancialCalculationService and compared with what the
// file claims; nothing is taken on trust.
@Component
public class BackupValidator {

  private static final int NAME_MAX = 255;
  private static final int REASON_MAX = 500;
  private static final int ENTITY_TYPE_MAX = 50;
  private static final int DETAILS_MAX = 1000;
  private static final int AMOUNT_INTEGER_DIGITS = 13;

  private final FinancialCalculationService calculationService;

  public BackupValidator(FinancialCalculationService calculationService) {
    this.calculationService = calculationService;
  }

  /** The first thing wrong with the file, or empty when it can be restored. */
  public Optional<String> findProblem(BackupFile file) {
    String problem = checkHeader(file);
    if (problem == null) problem = checkLedgers(file);
    if (problem == null) problem = checkTransactions(file);
    if (problem == null) problem = checkAudit(file);
    if (problem == null) problem = checkTotals(file);
    return Optional.ofNullable(problem);
  }

  private String checkHeader(BackupFile file) {
    if (file == null) return "the file is empty";
    if (!BackupFile.FORMAT.equals(file.format())) return "it is not a ROKAD backup file";
    if (file.formatVersion() != BackupFile.CURRENT_VERSION) {
      return "it uses backup format version "
          + file.formatVersion()
          + ", which this version of ROKAD cannot read";
    }
    if (file.counts() == null
        || file.ledgers() == null
        || file.transactions() == null
        || file.audit() == null) {
      return "a section of the file is missing";
    }
    if (file.counts().ledgers() != file.ledgers().size()
        || file.counts().transactions() != file.transactions().size()
        || file.counts().audit() != file.audit().size()) {
      return "its counts do not match its content, so it may have been cut short";
    }
    if (file.checksum() == null || !file.checksum().equals(BackupChecksum.compute(file))) {
      return "its checksum does not match, so it was changed or damaged after it was made";
    }
    return null;
  }

  private String checkLedgers(BackupFile file) {
    List<BackupFile.LedgerRow> ledgers = file.ledgers();
    if (ledgers.isEmpty()) return "it contains no ledgers";

    Set<Long> ids = new HashSet<>();
    int active = 0;
    for (BackupFile.LedgerRow l : ledgers) {
      if (l == null || l.ledgerId() == null) return "a ledger has no id";
      String label = "ledger " + l.ledgerId();
      if (!ids.add(l.ledgerId())) return label + " appears twice";
      if (l.startDate() == null) return label + " has no start date";
      if (l.createdAt() == null) return label + " has no creation time";
      if (l.ledgerName() != null && l.ledgerName().length() > NAME_MAX) {
        return label + " has a name longer than " + NAME_MAX + " characters";
      }
      if (!isAmount(l.openingBalance()) || l.openingBalance().signum() < 0) {
        return label + " has an invalid opening balance";
      }
      if (!isAmount(l.totalDebit()) || !isAmount(l.totalCredit())) {
        return label + " has invalid totals";
      }
      if ("ACTIVE".equals(l.status())) {
        active++;
        if (l.endDate() != null
            || l.closingBalance() != null
            || l.closedAt() != null
            || l.nextLedgerId() != null) {
          return label + " is active but has closing details";
        }
      } else if ("CLOSED".equals(l.status())) {
        if (l.endDate() == null || l.endDate().isBefore(l.startDate())) {
          return label + " is closed but has no valid end date";
        }
        if (!isAmount(l.closingBalance()) || l.closingBalance().signum() < 0) {
          return label + " is closed but has no valid closing balance";
        }
        if (l.closedAt() == null) return label + " is closed but has no closing time";
        if (l.nextLedgerId() == null) {
          return label + " is closed but no ledger follows it";
        }
      } else {
        return label + " has an unknown status";
      }
    }
    if (active != 1) {
      return "it must contain exactly one active ledger, but contains " + active;
    }

    List<BackupFile.LedgerRow> chain =
        ledgers.stream()
            .sorted(
                Comparator.comparing(BackupFile.LedgerRow::startDate)
                    .thenComparing(BackupFile.LedgerRow::ledgerId))
            .toList();
    for (int i = 0; i < chain.size(); i++) {
      BackupFile.LedgerRow current = chain.get(i);
      if (i == 0) {
        if (current.previousLedgerId() != null) {
          return "the first ledger points to a ledger before it that is not in the file";
        }
      } else {
        BackupFile.LedgerRow before = chain.get(i - 1);
        if (!before.ledgerId().equals(current.previousLedgerId())
            || !current.ledgerId().equals(before.nextLedgerId())) {
          return "ledger "
              + before.ledgerId()
              + " and ledger "
              + current.ledgerId()
              + " are not linked to each other";
        }
        if (current.openingBalance().compareTo(before.closingBalance()) != 0) {
          return "the opening balance of ledger "
              + current.ledgerId()
              + " is not the closing balance of ledger "
              + before.ledgerId();
        }
        if (!current.startDate().equals(before.endDate().plusDays(1))) {
          return "ledger "
              + current.ledgerId()
              + " does not start the day after ledger "
              + before.ledgerId()
              + " ended";
        }
      }
      if (i == chain.size() - 1 && current.nextLedgerId() != null) {
        return "the last ledger points to a ledger that is not in the file";
      }
    }
    return null;
  }

  private String checkTransactions(BackupFile file) {
    Map<Long, BackupFile.LedgerRow> ledgers = new HashMap<>();
    file.ledgers().forEach(l -> ledgers.put(l.ledgerId(), l));

    Set<Long> ids = new HashSet<>();
    for (BackupFile.TransactionRow t : file.transactions()) {
      if (t == null || t.transactionId() == null) return "an entry has no id";
      String label = "entry " + t.transactionId();
      if (!ids.add(t.transactionId())) return label + " appears twice";
      BackupFile.LedgerRow ledger = ledgers.get(t.ledgerId());
      if (ledger == null) return label + " belongs to an unknown ledger";
      if (!"DEBIT".equals(t.transactionType()) && !"CREDIT".equals(t.transactionType())) {
        return label + " has an unknown type";
      }
      if (t.transactionDate() == null) return label + " has no date";
      if (!isAmount(t.amount()) || t.amount().signum() <= 0) {
        return label + " has an amount that is not above zero with at most two decimal places";
      }
      if (t.reason() == null || t.reason().isBlank()) return label + " has no reason";
      if (t.reason().length() > REASON_MAX) {
        return label + " has a reason longer than " + REASON_MAX + " characters";
      }
      if (t.createdAt() == null || t.updatedAt() == null) {
        return label + " has no creation time";
      }
      if (t.transactionDate().isBefore(ledger.startDate())
          || (ledger.endDate() != null && t.transactionDate().isAfter(ledger.endDate()))) {
        return label + " is dated outside the period of ledger " + ledger.ledgerId();
      }
    }
    return null;
  }

  private String checkAudit(BackupFile file) {
    Set<Long> ids = new HashSet<>();
    Set<String> actions = new HashSet<>();
    for (AuditAction action : AuditAction.values()) actions.add(action.name());
    for (BackupFile.AuditRow a : file.audit()) {
      if (a == null || a.auditId() == null) return "an audit row has no id";
      String label = "audit row " + a.auditId();
      if (!ids.add(a.auditId())) return label + " appears twice";
      if (!actions.contains(a.action())) return label + " has an unknown action";
      if (a.entityType() == null
          || a.entityType().isBlank()
          || a.entityType().length() > ENTITY_TYPE_MAX) {
        return label + " has an invalid entity type";
      }
      if (a.details() != null && a.details().length() > DETAILS_MAX) {
        return label + " has details longer than " + DETAILS_MAX + " characters";
      }
      if (a.performedAt() == null) return label + " has no time";
    }
    return null;
  }

  // The stored totals and closing balance of every ledger must be what its own entries add up
  // to, and no balance may be below zero (the same rule the live ledger enforces).
  private String checkTotals(BackupFile file) {
    for (BackupFile.LedgerRow l : file.ledgers()) {
      BalanceResult result =
          calculationService.calculateBalance(l.openingBalance(), entriesOf(file, l.ledgerId()));
      if (result.totalDebit().compareTo(l.totalDebit()) != 0
          || result.totalCredit().compareTo(l.totalCredit()) != 0) {
        return "the stored totals of ledger " + l.ledgerId() + " do not match its entries";
      }
      if (result.closingBalance().signum() < 0) {
        return "the balance of ledger " + l.ledgerId() + " would be below zero";
      }
      if ("CLOSED".equals(l.status())
          && result.closingBalance().compareTo(l.closingBalance()) != 0) {
        return "the closing balance of ledger " + l.ledgerId() + " does not match its entries";
      }
    }
    return null;
  }

  // The entries of one ledger as domain objects, ready for the one calculation service.
  static List<Transaction> entriesOf(BackupFile file, Long ledgerId) {
    List<Transaction> entries = new ArrayList<>();
    for (BackupFile.TransactionRow t : file.transactions()) {
      if (ledgerId.equals(t.ledgerId())) {
        entries.add(
            new Transaction(
                null,
                t.transactionDate(),
                TransactionType.valueOf(t.transactionType()),
                t.amount(),
                t.reason()));
      }
    }
    return entries;
  }

  // Money fits DECIMAL(15,2): at most two decimals and thirteen digits before the point.
  private static boolean isAmount(BigDecimal value) {
    if (value == null) return false;
    BigDecimal trimmed = value.stripTrailingZeros();
    return trimmed.scale() <= 2 && trimmed.precision() - trimmed.scale() <= AMOUNT_INTEGER_DIGITS;
  }
}
