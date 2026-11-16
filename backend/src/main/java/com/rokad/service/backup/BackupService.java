package com.rokad.service.backup;

import com.rokad.dto.BackupSummaryDTO;
import com.rokad.entity.AuditAction;
import com.rokad.service.audit.AuditService;
import com.rokad.service.calculation.FinancialCalculationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Backup and restore (SRS 11.2, NFR-008). Both sides are guarded the same way: a file is only
// ever written if the data passes the validator, and a file is only ever restored if it does
// too. A restore runs in one transaction and proves what it wrote before it commits, so it
// either replaces everything or changes nothing (TC-047).
@Service
public class BackupService {

  private final BackupStore store;
  private final BackupValidator validator;
  private final FinancialCalculationService calculationService;
  private final AuditService auditService;
  private final Clock clock;
  private final String appVersion;

  public BackupService(
      BackupStore store,
      BackupValidator validator,
      FinancialCalculationService calculationService,
      AuditService auditService,
      Clock clock,
      @Value("${rokad.version:0.1.0}") String appVersion) {
    this.store = store;
    this.validator = validator;
    this.calculationService = calculationService;
    this.auditService = auditService;
    this.clock = clock;
    this.appVersion = appVersion;
  }

  @Transactional
  public BackupFile export() {
    if (store.currentCounts().ledgers() == 0) {
      throw new IllegalStateException(
          "There is nothing to back up yet. Set up your first ledger first.");
    }
    // Logged before the data is read, so the file also records its own creation.
    auditService.log(AuditAction.BACKUP, "BACKUP", null, "Backup exported");
    BackupFile file = seal(store.read(), LocalDateTime.now(clock), appVersion);
    validator
        .findProblem(file)
        .ifPresent(
            problem -> {
              throw new IllegalStateException(
                  "The backup was not made because the data in ROKAD does not pass its own checks: "
                      + problem
                      + ".");
            });
    return file;
  }

  // A dry run: says what restoring this file would do, without doing it.
  @Transactional(readOnly = true)
  public BackupSummaryDTO inspect(BackupFile file) {
    requireValid(file);
    return summarize(file, store.currentCounts());
  }

  @Transactional
  public BackupSummaryDTO restore(BackupFile file) {
    requireValid(file);
    store.replaceAll(new BackupStore.Content(file.ledgers(), file.transactions(), file.audit()));

    // Read back what is now stored and hold it to the file: same checksum, same rules.
    BackupFile written = seal(store.read(), file.createdAt(), file.appVersion());
    if (!written.checksum().equals(file.checksum())) {
      throw new IllegalStateException(
          "The restored data does not match the backup, so nothing was changed.");
    }
    validator
        .findProblem(written)
        .ifPresent(
            problem -> {
              throw new IllegalStateException(
                  "The restored data failed its checks, so nothing was changed: " + problem + ".");
            });

    auditService.log(
        AuditAction.RESTORE,
        "BACKUP",
        null,
        "Restored from the backup made on " + file.createdAt());
    return summarize(written, written.counts());
  }

  private void requireValid(BackupFile file) {
    validator
        .findProblem(file)
        .ifPresent(
            problem -> {
              throw new BackupInvalidException(problem);
            });
  }

  private static BackupFile seal(
      BackupStore.Content content, LocalDateTime createdAt, String appVersion) {
    BackupFile unsigned =
        new BackupFile(
            BackupFile.FORMAT,
            BackupFile.CURRENT_VERSION,
            appVersion,
            createdAt,
            new BackupFile.Counts(
                content.ledgers().size(), content.transactions().size(), content.audit().size()),
            content.ledgers(),
            content.transactions(),
            content.audit(),
            null);
    return new BackupFile(
        unsigned.format(),
        unsigned.formatVersion(),
        unsigned.appVersion(),
        unsigned.createdAt(),
        unsigned.counts(),
        unsigned.ledgers(),
        unsigned.transactions(),
        unsigned.audit(),
        BackupChecksum.compute(unsigned));
  }

  private BackupSummaryDTO summarize(BackupFile file, BackupFile.Counts now) {
    BackupFile.LedgerRow active =
        file.ledgers().stream().filter(l -> "ACTIVE".equals(l.status())).findFirst().orElseThrow();
    LocalDate firstStart =
        file.ledgers().stream()
            .map(BackupFile.LedgerRow::startDate)
            .min(Comparator.naturalOrder())
            .orElseThrow();
    BigDecimal activeBalance =
        calculationService
            .calculateBalance(
                active.openingBalance(), BackupValidator.entriesOf(file, active.ledgerId()))
            .closingBalance();
    int closed = (int) file.ledgers().stream().filter(l -> "CLOSED".equals(l.status())).count();
    return new BackupSummaryDTO(
        file.createdAt(),
        file.appVersion(),
        file.ledgers().size(),
        closed,
        file.transactions().size(),
        firstStart,
        active.startDate(),
        activeBalance,
        now.ledgers(),
        now.transactions());
  }
}
