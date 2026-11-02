package com.rokad.service.closure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

import com.rokad.config.ClockConfig;
import com.rokad.dto.ClosingWindowDTO;
import com.rokad.dto.LedgerSetupRequest;
import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
import com.rokad.entity.Transaction;
import com.rokad.entity.TransactionType;
import com.rokad.repository.LedgerRepository;
import com.rokad.repository.TransactionRepository;
import com.rokad.service.audit.AuditService;
import com.rokad.service.calculation.FinancialCalculationService;
import com.rokad.service.lifecycle.LedgerLifecycleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Phase 12 closing rules R1-R6. Every rejection happens before anything is changed, so these
// can run inside the usual rolled-back test transaction.
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("test")
@Import({
  VerificationClosureService.class,
  LedgerLifecycleService.class,
  FinancialCalculationService.class,
  AuditService.class,
  ClockConfig.class
})
class ClosureRulesTest {

  @Autowired private VerificationClosureService closureService;
  @Autowired private LedgerLifecycleService lifecycleService;
  @Autowired private LedgerRepository ledgerRepository;
  @Autowired private TransactionRepository transactionRepository;

  private final LocalDate today = LocalDate.now();

  private Ledger activeLedger(LocalDate startDate, String openingBalance) {
    return lifecycleService.createInitialLedger(
        new LedgerSetupRequest(null, startDate, new BigDecimal(openingBalance)));
  }

  private void addEntry(Ledger ledger, LocalDate date, TransactionType type, String amount) {
    transactionRepository.save(
        new Transaction(ledger, date, type, new BigDecimal(amount), "Entry"));
  }

  private VerificationSummary verify(Ledger ledger, LocalDate end) {
    return closureService.verifyPeriod(ledger.getLedgerId(), ledger.getStartDate(), end);
  }

  private Ledger close(Ledger ledger, LocalDate end) {
    return closureService.closeLedger(
        ledger.getLedgerId(), ledger.getStartDate(), end, verify(ledger, end));
  }

  private void assertStillActive(Ledger ledger) {
    Ledger reloaded = ledgerRepository.findById(ledger.getLedgerId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(LedgerStatus.ACTIVE);
    assertThat(reloaded.getEndDate()).isNull();
    assertThat(ledgerRepository.count()).isEqualTo(1);
  }

  // TC-048 (R1): closing covers the whole ledger, so it must start on the ledger's start date
  @Test
  void closingMustStartOnTheLedgersOwnStartDate() {
    Ledger ledger = activeLedger(today.minusDays(30), "1000.00");
    LocalDate laterStart = today.minusDays(10);
    VerificationSummary review =
        closureService.verifyPeriod(ledger.getLedgerId(), laterStart, today);

    assertThatThrownBy(
            () -> closureService.closeLedger(ledger.getLedgerId(), laterStart, today, review))
        .isInstanceOf(LedgerVerificationException.class)
        .hasMessageContaining("start date must be the ledger's own start date");
    assertStillActive(ledger);
  }

  // TC-049 (R2): no entry may be left outside the closed period
  @Test
  void closingBeforeTheLatestEntryIsRejectedAndNothingChanges() {
    Ledger ledger = activeLedger(today.minusDays(30), "1000.00");
    addEntry(ledger, today.minusDays(5), TransactionType.CREDIT, "100.00");
    LocalDate tooEarly = today.minusDays(10);
    VerificationSummary verified = verify(ledger, tooEarly);

    assertThatThrownBy(
            () ->
                closureService.closeLedger(
                    ledger.getLedgerId(), ledger.getStartDate(), tooEarly, verified))
        .isInstanceOf(LedgerVerificationException.class)
        .hasMessageContaining("latest entry");
    assertStillActive(ledger);
  }

  // TC-050 (R2): the latest entry's own date is a valid end date; the next ledger follows it
  @Test
  void closingOnTheLatestEntryDateIsAllowed() {
    Ledger ledger = activeLedger(today.minusDays(30), "1000.00");
    LocalDate lastEntry = today.minusDays(5);
    addEntry(ledger, lastEntry, TransactionType.CREDIT, "250.00");

    Ledger next = close(ledger, lastEntry);

    assertThat(next.getStartDate()).isEqualTo(lastEntry.plusDays(1));
    assertThat(next.getOpeningBalance()).isEqualByComparingTo("1250.00");
  }

  // TC-051 (R3): ending today is allowed; the next ledger then starts tomorrow (BR-013)
  @Test
  void closingOnTodayStartsTheNextLedgerTomorrow() {
    Ledger ledger = activeLedger(today.minusDays(3), "500.00");
    addEntry(ledger, today, TransactionType.CREDIT, "100.00");

    Ledger next = close(ledger, today);

    assertThat(next.getStartDate()).isEqualTo(today.plusDays(1));
    assertThat(next.getStatus()).isEqualTo(LedgerStatus.ACTIVE);
    assertThat(next.getOpeningBalance()).isEqualByComparingTo("600.00");
  }

  // TC-052 (R4): a ledger without entries can be closed
  @Test
  void anEmptyLedgerCanBeClosed() {
    Ledger ledger = activeLedger(today.minusDays(3), "500.00");

    Ledger next = close(ledger, ledger.getStartDate());

    assertThat(next.getStartDate()).isEqualTo(ledger.getStartDate().plusDays(1));
    assertThat(next.getOpeningBalance()).isEqualByComparingTo("500.00");
    assertThat(ledgerRepository.findById(ledger.getLedgerId()).orElseThrow().getStatus())
        .isEqualTo(LedgerStatus.CLOSED);
  }

  // TC-053 (R6): a negative closing balance never reaches the database constraint
  @Test
  void aNegativeClosingBalanceIsRefusedWithAClearMessage() {
    Ledger ledger = activeLedger(today.minusDays(10), "100.00");
    addEntry(ledger, today.minusDays(2), TransactionType.DEBIT, "500.00");
    LocalDate end = today.minusDays(1);
    VerificationSummary verified = verify(ledger, end);
    assertThat(verified.closingBalance()).isEqualByComparingTo("-400.00");

    assertThatThrownBy(
            () ->
                closureService.closeLedger(
                    ledger.getLedgerId(), ledger.getStartDate(), end, verified))
        .isInstanceOf(LedgerVerificationException.class)
        .hasMessageContaining("negative");
    assertStillActive(ledger);
  }

  // TC-054 (R5): figures confirmed on screen must still be true when the ledger closes
  @Test
  void closingIsRefusedWhenTheLedgerChangedAfterVerification() {
    Ledger ledger = activeLedger(today.minusDays(10), "1000.00");
    addEntry(ledger, today.minusDays(3), TransactionType.CREDIT, "100.00");
    LocalDate end = today.minusDays(1);
    VerificationSummary verified = verify(ledger, end);
    addEntry(ledger, today.minusDays(2), TransactionType.DEBIT, "50.00");

    assertThatThrownBy(
            () ->
                closureService.closeLedger(
                    ledger.getLedgerId(), ledger.getStartDate(), end, verified))
        .isInstanceOf(StaleVerificationException.class);
    assertStillActive(ledger);
  }

  // TC-064 (R5): the browser sends plain numbers, so 1250 and 1250.00 must count as equal
  @Test
  void confirmedFiguresMatchEvenWhenTheyArriveWithoutTrailingZeros() {
    Ledger ledger = activeLedger(today.minusDays(10), "1000.00");
    addEntry(ledger, today.minusDays(3), TransactionType.CREDIT, "250.00");
    LocalDate end = today.minusDays(1);
    VerificationSummary exact = verify(ledger, end);
    VerificationSummary plain =
        new VerificationSummary(
            exact.ledgerId(),
            exact.periodStart(),
            exact.periodEnd(),
            exact.openingBalance().stripTrailingZeros(),
            exact.totalDebit().stripTrailingZeros(),
            exact.totalCredit().stripTrailingZeros(),
            exact.closingBalance().stripTrailingZeros(),
            exact.debitCount(),
            exact.creditCount(),
            exact.nextLedgerStartDate());

    Ledger next =
        closureService.closeLedger(ledger.getLedgerId(), ledger.getStartDate(), end, plain);

    assertThat(next.getOpeningBalance()).isEqualByComparingTo("1250.00");
  }

  // TC-055 (R5): a closure request without a verified summary is refused
  @Test
  void closingRequiresAVerifiedSummary() {
    Ledger ledger = activeLedger(today.minusDays(10), "1000.00");

    assertThatThrownBy(
            () ->
                closureService.closeLedger(
                    ledger.getLedgerId(), ledger.getStartDate(), today, null))
        .isInstanceOf(LedgerVerificationException.class)
        .hasMessageContaining("Verify the period");
    assertStillActive(ledger);
  }

  // TC-056: the summary names the day the next ledger would start (BR-013)
  @Test
  void verificationNamesTheNextLedgersStartDate() {
    Ledger ledger = activeLedger(today.minusDays(10), "1000.00");

    VerificationSummary summary = verify(ledger, today.minusDays(2));

    assertThat(summary.nextLedgerStartDate()).isEqualTo(today.minusDays(1));
  }

  // TC-057: the closing window runs from the latest entry to today
  @Test
  void theClosingWindowRunsFromTheLatestEntryToToday() {
    Ledger ledger = activeLedger(today.minusDays(30), "1000.00");
    addEntry(ledger, today.minusDays(5), TransactionType.CREDIT, "100.00");
    addEntry(ledger, today.minusDays(9), TransactionType.DEBIT, "10.00");

    ClosingWindowDTO window = closureService.getClosingWindow().orElseThrow();

    assertThat(window.ledgerId()).isEqualTo(ledger.getLedgerId());
    assertThat(window.startDate()).isEqualTo(ledger.getStartDate());
    assertThat(window.lastEntryDate()).isEqualTo(today.minusDays(5));
    assertThat(window.earliestEndDate()).isEqualTo(today.minusDays(5));
    assertThat(window.latestEndDate()).isEqualTo(today);
    assertThat(window.closable()).isTrue();
  }

  // TC-057: without entries the window starts at the ledger's own start date
  @Test
  void theClosingWindowOfALedgerWithoutEntriesStartsAtItsStartDate() {
    Ledger ledger = activeLedger(today.minusDays(30), "1000.00");

    ClosingWindowDTO window = closureService.getClosingWindow().orElseThrow();

    assertThat(window.lastEntryDate()).isNull();
    assertThat(window.earliestEndDate()).isEqualTo(ledger.getStartDate());
    assertThat(window.closable()).isTrue();
  }

  // TC-057: a ledger that starts after today has nothing to close yet
  @Test
  void aLedgerThatStartsTomorrowIsNotClosableYet() {
    activeLedger(today.plusDays(1), "1000.00");

    ClosingWindowDTO window = closureService.getClosingWindow().orElseThrow();

    assertThat(window.earliestEndDate()).isEqualTo(today.plusDays(1));
    assertThat(window.latestEndDate()).isEqualTo(today);
    assertThat(window.closable()).isFalse();
  }
}
