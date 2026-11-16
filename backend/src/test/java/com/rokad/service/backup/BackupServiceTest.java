package com.rokad.service.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.rokad.dto.AppInfoDTO;
import com.rokad.dto.BackupSummaryDTO;
import com.rokad.dto.LedgerSetupRequest;
import com.rokad.dto.TransactionRequestDTO;
import com.rokad.entity.AuditAction;
import com.rokad.entity.Ledger;
import com.rokad.entity.TransactionType;
import com.rokad.repository.AuditRepository;
import com.rokad.repository.LedgerRepository;
import com.rokad.repository.TransactionRepository;
import com.rokad.service.appinfo.AppInfoService;
import com.rokad.service.closure.VerificationClosureService;
import com.rokad.service.closure.VerificationSummary;
import com.rokad.service.lifecycle.LedgerLifecycleService;
import com.rokad.service.transaction.TransactionManagementService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

// TC-045, TC-046, TC-047. No @Transactional on purpose: every call really commits or rolls back,
// against the real test database.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class BackupServiceTest {

  @Autowired private BackupService backupService;
  @Autowired private BackupValidator validator;
  @Autowired private AppInfoService appInfoService;
  @Autowired private LedgerLifecycleService lifecycleService;
  @Autowired private TransactionManagementService transactionService;
  @Autowired private VerificationClosureService closureService;
  @Autowired private LedgerRepository ledgerRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private AuditRepository auditRepository;
  @SpyBean private BackupStore backupStore;

  private final LocalDate today = LocalDate.now();

  @BeforeEach
  @AfterEach
  void cleanSlate() {
    Mockito.reset(backupStore);
    transactionRepository.deleteAll();
    auditRepository.deleteAll();
    List<Ledger> ledgers = ledgerRepository.findAll();
    ledgers.forEach(
        l -> {
          l.setPreviousLedgerId(null);
          l.setNextLedgerId(null);
        });
    ledgerRepository.saveAll(ledgers);
    ledgerRepository.deleteAll();
  }

  // Ledger 1 is closed (opening 1000.00, credit 500.00, debit 200.00, closing 1300.00); ledger 2
  // is active, opens with 1300.00 and holds one credit of 100.00.
  private void twoLedgers() {
    Ledger first =
        lifecycleService.createInitialLedger(
            new LedgerSetupRequest("October", today.minusDays(30), new BigDecimal("1000.00")));
    add(today.minusDays(20), TransactionType.CREDIT, "500.00", "Sale");
    add(today.minusDays(10), TransactionType.DEBIT, "200.00", "Rent");
    LocalDate end = today.minusDays(5);
    VerificationSummary review =
        closureService.verifyPeriod(first.getLedgerId(), first.getStartDate(), end);
    closureService.closeLedger(first.getLedgerId(), first.getStartDate(), end, review);
    add(today.minusDays(1), TransactionType.CREDIT, "100.00", "Fresh sale");
  }

  private void add(LocalDate date, TransactionType type, String amount, String reason) {
    transactionService.addTransaction(
        new TransactionRequestDTO(date, type, new BigDecimal(amount), reason));
  }

  private BigDecimal currentBalance() {
    return lifecycleService
        .getLedgerSummary(lifecycleService.getActiveLedgerOrThrow())
        .currentBalance();
  }

  // TC-045: a backup holds the active and the closed ledger with their entries
  @Test
  void theExportHoldsTheActiveAndTheClosedLedgerWithTheirEntries() {
    twoLedgers();

    BackupFile file = backupService.export();

    assertThat(file.format()).isEqualTo(BackupFile.FORMAT);
    assertThat(file.counts().ledgers()).isEqualTo(2);
    assertThat(file.counts().transactions()).isEqualTo(3);
    assertThat(file.ledgers().stream().map(BackupFile.LedgerRow::status).toList())
        .containsExactly("CLOSED", "ACTIVE");
    assertThat(file.ledgers().get(0).closingBalance()).isEqualByComparingTo("1300.00");
    assertThat(file.ledgers().get(1).openingBalance()).isEqualByComparingTo("1300.00");
    assertThat(validator.findProblem(file)).isEmpty();
    assertThat(file.audit().stream().anyMatch(a -> "BACKUP".equals(a.action()))).isTrue();
  }

  // TC-045: nothing to back up is said plainly instead of writing an empty file
  @Test
  void exportingWithNothingToBackUpIsRefused() {
    assertThatThrownBy(() -> backupService.export())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nothing to back up");
  }

  // TC-046: restoring a backup brings back the balances, the ledgers and the history
  @Test
  void restoringABackupBringsBackTheBalancesAndTheHistory() {
    twoLedgers();
    BackupFile file = backupService.export();
    BigDecimal balanceThen = currentBalance();
    add(today, TransactionType.CREDIT, "50.00", "Added after the backup");
    assertThat(transactionRepository.count()).isEqualTo(4);

    BackupSummaryDTO summary = backupService.restore(file);

    assertThat(transactionRepository.count()).isEqualTo(3);
    assertThat(ledgerRepository.count()).isEqualTo(2);
    assertThat(currentBalance()).isEqualByComparingTo(balanceThen);
    assertThat(summary.ledgerCount()).isEqualTo(2);
    assertThat(summary.closedLedgerCount()).isEqualTo(1);
    assertThat(summary.entryCount()).isEqualTo(3);
    assertThat(summary.activeBalance()).isEqualByComparingTo(balanceThen);
    Ledger closed = ledgerRepository.findById(file.ledgers().get(0).ledgerId()).orElseThrow();
    assertThat(closed.getClosingBalance()).isEqualByComparingTo("1300.00");
    assertThat(auditRepository.findFirstByActionOrderByAuditIdDesc(AuditAction.RESTORE))
        .isPresent();
  }

  // TC-046: a backup restores into an empty database, as after a reinstall, and the app goes on
  @Test
  void aBackupRestoresIntoAnEmptyDatabaseAndTheAppCarriesOn() {
    twoLedgers();
    BackupFile file = backupService.export();
    cleanSlate();
    assertThat(ledgerRepository.count()).isEqualTo(0);

    backupService.restore(file);

    assertThat(ledgerRepository.count()).isEqualTo(2);
    assertThat(lifecycleService.getActiveLedger()).isPresent();
    assertThat(currentBalance()).isEqualByComparingTo("1400.00");
    add(today, TransactionType.DEBIT, "400.00", "First entry after the restore");
    assertThat(currentBalance()).isEqualByComparingTo("1000.00");
    assertThat(transactionRepository.count()).isEqualTo(4);
  }

  // TC-047: a damaged or incomplete file is refused and the data is not touched
  @Test
  void aDamagedOrIncompleteFileNeverTouchesTheData() {
    twoLedgers();
    BackupFile file = backupService.export();
    long ledgersBefore = ledgerRepository.count();
    long entriesBefore = transactionRepository.count();

    BackupFile wrongChecksum =
        new BackupFile(
            file.format(),
            file.formatVersion(),
            file.appVersion(),
            file.createdAt(),
            file.counts(),
            file.ledgers(),
            file.transactions(),
            file.audit(),
            "sha256:0000");
    BackupFile cutShort =
        new BackupFile(
            file.format(),
            file.formatVersion(),
            file.appVersion(),
            file.createdAt(),
            file.counts(),
            file.ledgers(),
            file.transactions().subList(0, 1),
            file.audit(),
            file.checksum());

    assertThatThrownBy(() -> backupService.restore(wrongChecksum))
        .isInstanceOf(BackupInvalidException.class)
        .hasMessageContaining("checksum");
    assertThatThrownBy(() -> backupService.restore(cutShort))
        .isInstanceOf(BackupInvalidException.class)
        .hasMessageContaining("cut short");
    assertThatThrownBy(() -> backupService.inspect(wrongChecksum))
        .isInstanceOf(BackupInvalidException.class);

    assertThat(ledgerRepository.count()).isEqualTo(ledgersBefore);
    assertThat(transactionRepository.count()).isEqualTo(entriesBefore);
    assertThat(auditRepository.findFirstByActionOrderByAuditIdDesc(AuditAction.RESTORE)).isEmpty();
  }

  // TC-047: a restore that fails half way, after the old data was already deleted, changes nothing
  @Test
  void aRestoreThatFailsHalfWayChangesNothing() {
    twoLedgers();
    BackupFile file = backupService.export();
    add(today, TransactionType.CREDIT, "50.00", "Would be lost by a successful restore");
    long entriesBefore = transactionRepository.count();
    doThrow(new IllegalStateException("simulated failure")).when(backupStore).read();

    assertThatThrownBy(() -> backupService.restore(file))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("simulated failure");
    Mockito.reset(backupStore);

    assertThat(transactionRepository.count()).isEqualTo(entriesBefore);
    assertThat(ledgerRepository.count()).isEqualTo(2);
    assertThat(auditRepository.findFirstByActionOrderByAuditIdDesc(AuditAction.RESTORE)).isEmpty();
  }

  // TC-090: Settings can tell when the last backup was made and how much is stored
  @Test
  void theStorageInformationShowsTheLastBackupAndWhatIsStored() {
    twoLedgers();
    assertThat(appInfoService.getInfo().lastBackupAt()).isNull();

    backupService.export();
    AppInfoDTO info = appInfoService.getInfo();

    assertThat(info.lastBackupAt()).isNotNull();
    assertThat(info.ledgerCount()).isEqualTo(2);
    assertThat(info.closedLedgerCount()).isEqualTo(1);
    assertThat(info.entryCount()).isEqualTo(3);
    assertThat(info.databaseName()).isEqualTo("rokad_db_test");
  }
}
