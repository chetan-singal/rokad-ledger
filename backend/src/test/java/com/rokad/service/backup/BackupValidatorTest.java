package com.rokad.service.backup;

import static com.rokad.service.backup.BackupTestData.activeLedger;
import static com.rokad.service.backup.BackupTestData.audit;
import static com.rokad.service.backup.BackupTestData.closedLedger;
import static com.rokad.service.backup.BackupTestData.entries;
import static com.rokad.service.backup.BackupTestData.money;
import static com.rokad.service.backup.BackupTestData.seal;
import static com.rokad.service.backup.BackupTestData.withChecksum;
import static org.assertj.core.api.Assertions.assertThat;

import com.rokad.service.calculation.FinancialCalculationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// TC-047 and TC-072..TC-089: a backup that is incomplete, damaged or inconsistent is refused,
// each for its own reason. Every test starts from the valid file in BackupTestData and changes
// one thing, then seals it again so the checksum does not hide the rule being tested.
class BackupValidatorTest {

  private final BackupValidator validator = new BackupValidator(new FinancialCalculationService());

  private String problem(BackupFile file) {
    return validator.findProblem(file).orElse("(no problem)");
  }

  private static BackupFile.LedgerRow ledger(
      BackupFile.LedgerRow l,
      LocalDate start,
      LocalDate end,
      BigDecimal opening,
      BigDecimal closing,
      String status,
      Long previous,
      Long next) {
    return new BackupFile.LedgerRow(
        l.ledgerId(),
        l.ledgerName(),
        start,
        end,
        opening,
        l.totalDebit(),
        l.totalCredit(),
        closing,
        status,
        l.createdAt(),
        l.closedAt(),
        previous,
        next);
  }

  private static BackupFile.TransactionRow entryWith(
      BackupFile.TransactionRow t,
      Long ledgerId,
      LocalDate date,
      String type,
      BigDecimal amount,
      String reason) {
    return new BackupFile.TransactionRow(
        t.transactionId(), ledgerId, date, type, amount, reason, t.createdAt(), t.updatedAt());
  }

  private static List<BackupFile.TransactionRow> entriesWith(
      int index, BackupFile.TransactionRow replacement) {
    List<BackupFile.TransactionRow> list = new ArrayList<>(entries());
    list.set(index, replacement);
    return list;
  }

  private static BackupFile sealWithLedgers(
      BackupFile.LedgerRow closed, BackupFile.LedgerRow active) {
    return seal(List.of(closed, active), entries(), audit());
  }

  @Test
  void aValidBackupHasNoProblem() {
    assertThat(problem(BackupTestData.valid())).isEqualTo("(no problem)");
  }

  @Test
  void aFileWithTheWrongFormatMarkerIsRefused() {
    BackupFile valid = BackupTestData.valid();
    BackupFile wrong =
        new BackupFile(
            "something-else",
            1,
            "0.1.0",
            valid.createdAt(),
            valid.counts(),
            valid.ledgers(),
            valid.transactions(),
            valid.audit(),
            valid.checksum());

    assertThat(problem(wrong)).contains("not a ROKAD backup file");
  }

  @Test
  void aNewerFormatVersionIsRefused() {
    BackupFile valid = BackupTestData.valid();
    BackupFile newer =
        new BackupFile(
            BackupFile.FORMAT,
            2,
            "9.0.0",
            valid.createdAt(),
            valid.counts(),
            valid.ledgers(),
            valid.transactions(),
            valid.audit(),
            valid.checksum());

    assertThat(problem(newer)).contains("format version 2");
  }

  @Test
  void aMissingSectionIsRefused() {
    BackupFile valid = BackupTestData.valid();
    BackupFile cut =
        new BackupFile(
            BackupFile.FORMAT,
            1,
            "0.1.0",
            valid.createdAt(),
            valid.counts(),
            valid.ledgers(),
            null,
            valid.audit(),
            valid.checksum());

    assertThat(problem(cut)).contains("section of the file is missing");
  }

  // TC-047: an incomplete file (entries cut off) is recognised by its counts
  @Test
  void countsThatDoNotMatchTheContentAreRefused() {
    BackupFile valid = BackupTestData.valid();
    BackupFile cut =
        new BackupFile(
            BackupFile.FORMAT,
            1,
            "0.1.0",
            valid.createdAt(),
            valid.counts(),
            valid.ledgers(),
            entries().subList(0, 2),
            valid.audit(),
            valid.checksum());

    assertThat(problem(cut)).contains("cut short");
  }

  // TC-047: a file edited after it was made is recognised by its checksum
  @Test
  void aFileEditedAfterItWasMadeIsRefused() {
    BackupFile valid = BackupTestData.valid();
    BackupFile edited =
        withChecksum(
            seal(
                valid.ledgers(),
                entriesWith(0, entryWith(entries().get(0), money("999.00"))),
                valid.audit()),
            valid.checksum());

    assertThat(problem(edited)).contains("checksum does not match");
  }

  private static BackupFile.TransactionRow entryWith(
      BackupFile.TransactionRow t, BigDecimal amount) {
    return entryWith(t, t.ledgerId(), t.transactionDate(), t.transactionType(), amount, t.reason());
  }

  @Test
  void aBackupWithoutLedgersIsRefused() {
    assertThat(problem(seal(List.of(), List.of(), List.of()))).contains("no ledgers");
  }

  @Test
  void twoActiveLedgersAreRefused() {
    BackupFile.LedgerRow secondActive =
        new BackupFile.LedgerRow(
            1L,
            "October",
            LocalDate.of(2026, 10, 1),
            null,
            money("1000.00"),
            money("0.00"),
            money("0.00"),
            null,
            "ACTIVE",
            closedLedger().createdAt(),
            null,
            null,
            null);

    assertThat(problem(seal(List.of(secondActive, activeLedger()), List.of(), List.of())))
        .contains("exactly one active ledger");
  }

  @Test
  void anOpeningBalanceThatIsNotThePreviousClosingBalanceIsRefused() {
    BackupFile.LedgerRow active = activeLedger();
    BackupFile.LedgerRow broken =
        ledger(active, active.startDate(), null, money("1301.00"), null, "ACTIVE", 1L, null);

    assertThat(problem(sealWithLedgers(closedLedger(), broken)))
        .contains("opening balance of ledger 2");
  }

  @Test
  void aLedgerThatDoesNotStartTheDayAfterThePreviousOneEndedIsRefused() {
    BackupFile.LedgerRow active = activeLedger();
    BackupFile.LedgerRow gap =
        ledger(
            active,
            LocalDate.of(2026, 11, 2),
            null,
            active.openingBalance(),
            null,
            "ACTIVE",
            1L,
            null);

    assertThat(problem(sealWithLedgers(closedLedger(), gap)))
        .contains("does not start the day after");
  }

  @Test
  void ledgersThatPointAtTheWrongNeighbourAreRefused() {
    BackupFile.LedgerRow active = activeLedger();
    BackupFile.LedgerRow unlinked =
        ledger(active, active.startDate(), null, active.openingBalance(), null, "ACTIVE", 7L, null);

    assertThat(problem(sealWithLedgers(closedLedger(), unlinked)))
        .contains("not linked to each other");
  }

  @Test
  void aClosedLedgerWithoutAClosingBalanceIsRefused() {
    BackupFile.LedgerRow closed = closedLedger();
    BackupFile.LedgerRow broken =
        ledger(
            closed,
            closed.startDate(),
            closed.endDate(),
            closed.openingBalance(),
            null,
            "CLOSED",
            null,
            2L);

    assertThat(problem(sealWithLedgers(broken, activeLedger())))
        .contains("no valid closing balance");
  }

  @Test
  void anEntryForAnUnknownLedgerIsRefused() {
    BackupFile.TransactionRow t = entries().get(2);
    BackupFile file =
        seal(
            List.of(closedLedger(), activeLedger()),
            entriesWith(
                2,
                entryWith(
                    t, 99L, t.transactionDate(), t.transactionType(), t.amount(), t.reason())),
            audit());

    assertThat(problem(file)).contains("unknown ledger");
  }

  @Test
  void anEntryDatedOutsideItsClosedLedgerIsRefused() {
    BackupFile.TransactionRow t = entries().get(0);
    BackupFile file =
        seal(
            List.of(closedLedger(), activeLedger()),
            entriesWith(
                0,
                entryWith(
                    t, 1L, LocalDate.of(2026, 11, 3), t.transactionType(), t.amount(), t.reason())),
            audit());

    assertThat(problem(file)).contains("dated outside the period of ledger 1");
  }

  @Test
  void aZeroOrNegativeAmountIsRefused() {
    BackupFile.TransactionRow t = entries().get(0);

    assertThat(
            problem(
                seal(
                    List.of(closedLedger(), activeLedger()),
                    entriesWith(0, entryWith(t, money("0.00"))),
                    audit())))
        .contains("above zero");
    assertThat(
            problem(
                seal(
                    List.of(closedLedger(), activeLedger()),
                    entriesWith(0, entryWith(t, money("-5.00"))),
                    audit())))
        .contains("above zero");
  }

  @Test
  void anAmountWithMoreThanTwoDecimalsIsRefused() {
    BackupFile.TransactionRow t = entries().get(0);

    assertThat(
            problem(
                seal(
                    List.of(closedLedger(), activeLedger()),
                    entriesWith(0, entryWith(t, money("500.001"))),
                    audit())))
        .contains("two decimal places");
  }

  @Test
  void aBlankReasonIsRefused() {
    BackupFile.TransactionRow t = entries().get(0);
    BackupFile file =
        seal(
            List.of(closedLedger(), activeLedger()),
            entriesWith(
                0,
                entryWith(
                    t, t.ledgerId(), t.transactionDate(), t.transactionType(), t.amount(), "   ")),
            audit());

    assertThat(problem(file)).contains("has no reason");
  }

  @Test
  void anUnknownEntryTypeIsRefused() {
    BackupFile.TransactionRow t = entries().get(0);
    BackupFile file =
        seal(
            List.of(closedLedger(), activeLedger()),
            entriesWith(
                0,
                entryWith(t, t.ledgerId(), t.transactionDate(), "REFUND", t.amount(), t.reason())),
            audit());

    assertThat(problem(file)).contains("unknown type");
  }

  @Test
  void theSameEntryTwiceIsRefused() {
    List<BackupFile.TransactionRow> doubled = new ArrayList<>(entries());
    doubled.add(entries().get(0));

    assertThat(problem(seal(List.of(closedLedger(), activeLedger()), doubled, audit())))
        .contains("appears twice");
  }

  // TC-046: totals are recomputed from the entries, never taken from the file
  @Test
  void storedTotalsThatDoNotMatchTheEntriesAreRefused() {
    BackupFile.LedgerRow closed = closedLedger();
    BackupFile.LedgerRow wrongTotals =
        new BackupFile.LedgerRow(
            closed.ledgerId(),
            closed.ledgerName(),
            closed.startDate(),
            closed.endDate(),
            closed.openingBalance(),
            money("201.00"),
            closed.totalCredit(),
            closed.closingBalance(),
            closed.status(),
            closed.createdAt(),
            closed.closedAt(),
            closed.previousLedgerId(),
            closed.nextLedgerId());

    assertThat(problem(sealWithLedgers(wrongTotals, activeLedger())))
        .contains("stored totals of ledger 1");
  }

  @Test
  void aClosingBalanceThatDoesNotMatchTheEntriesIsRefused() {
    BackupFile.LedgerRow closed = closedLedger();
    BackupFile.LedgerRow wrong =
        new BackupFile.LedgerRow(
            closed.ledgerId(),
            closed.ledgerName(),
            closed.startDate(),
            closed.endDate(),
            closed.openingBalance(),
            closed.totalDebit(),
            closed.totalCredit(),
            money("1301.00"),
            closed.status(),
            closed.createdAt(),
            closed.closedAt(),
            closed.previousLedgerId(),
            closed.nextLedgerId());
    BackupFile.LedgerRow active = activeLedger();
    BackupFile.LedgerRow follow =
        ledger(active, active.startDate(), null, money("1301.00"), null, "ACTIVE", 1L, null);

    assertThat(problem(sealWithLedgers(wrong, follow))).contains("closing balance of ledger 1");
  }

  @Test
  void aBalanceBelowZeroIsRefused() {
    BackupFile.LedgerRow active = activeLedger();
    BackupFile.LedgerRow overdrawn =
        new BackupFile.LedgerRow(
            active.ledgerId(),
            active.ledgerName(),
            active.startDate(),
            null,
            active.openingBalance(),
            money("1500.00"),
            money("100.00"),
            null,
            "ACTIVE",
            active.createdAt(),
            null,
            1L,
            null);
    List<BackupFile.TransactionRow> withBigDebit = new ArrayList<>(entries());
    withBigDebit.add(entry(4, 2, "2026-11-03", "DEBIT", "1500.00"));

    assertThat(problem(seal(List.of(closedLedger(), overdrawn), withBigDebit, audit())))
        .contains("below zero");
  }

  @Test
  void anUnknownAuditActionIsRefused() {
    List<BackupFile.AuditRow> rows = new ArrayList<>(audit());
    BackupFile.AuditRow first = rows.get(0);
    rows.set(
        0,
        new BackupFile.AuditRow(
            first.auditId(),
            "SOMETHING_ELSE",
            first.entityType(),
            first.entityId(),
            first.performedAt(),
            first.details()));

    assertThat(problem(seal(List.of(closedLedger(), activeLedger()), entries(), rows)))
        .contains("unknown action");
  }

  private static BackupFile.TransactionRow entry(
      long id, long ledgerId, String date, String type, String amount) {
    return BackupTestData.entry(id, ledgerId, date, type, amount);
  }
}
