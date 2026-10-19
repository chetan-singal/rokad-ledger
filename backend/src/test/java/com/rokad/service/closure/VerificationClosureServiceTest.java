package com.rokad.service.closure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

import com.rokad.config.ClockConfig;
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
class VerificationClosureServiceTest {

  @Autowired private VerificationClosureService closureService;
  @Autowired private LedgerLifecycleService lifecycleService;
  @Autowired private LedgerRepository ledgerRepository;
  @Autowired private TransactionRepository transactionRepository;

  private Ledger activeLedger(LocalDate startDate, BigDecimal openingBalance) {
    return lifecycleService.createInitialLedger(
        new LedgerSetupRequest(null, startDate, openingBalance));
  }

  private void addTransaction(
      Ledger ledger, LocalDate date, TransactionType type, BigDecimal amount, String reason) {
    transactionRepository.save(new Transaction(ledger, date, type, amount, reason));
  }

  // TC-027: an invalid date range cannot be verified
  @Test
  void invalidDateRangeCannotBeVerified() {
    Ledger ledger = activeLedger(LocalDate.of(2026, 4, 1), new BigDecimal("50000.00"));

    assertThatThrownBy(
            () ->
                closureService.verifyPeriod(
                    ledger.getLedgerId(), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 5)))
        .isInstanceOf(LedgerVerificationException.class);
  }

  // SRS 8.2: the period must fall within the active ledger's own eligible range
  @Test
  void periodStartBeforeTheLedgersOwnStartIsRejected() {
    Ledger ledger = activeLedger(LocalDate.of(2026, 4, 1), new BigDecimal("1000.00"));

    assertThatThrownBy(
            () ->
                closureService.verifyPeriod(
                    ledger.getLedgerId(), LocalDate.of(2026, 3, 25), LocalDate.of(2026, 4, 5)))
        .isInstanceOf(LedgerVerificationException.class);
  }

  // TC-028: verification shows correct totals and closing balance (the SRS worked example)
  @Test
  void verificationShowsCorrectTotalsAndClosingBalance() {
    Ledger ledger = activeLedger(LocalDate.of(2026, 4, 1), new BigDecimal("50000.00"));
    addTransaction(
        ledger,
        LocalDate.of(2026, 4, 2),
        TransactionType.CREDIT,
        new BigDecimal("10000.00"),
        "Received from Rahul");
    addTransaction(
        ledger,
        LocalDate.of(2026, 4, 3),
        TransactionType.DEBIT,
        new BigDecimal("2500.00"),
        "Purchased material");
    addTransaction(
        ledger,
        LocalDate.of(2026, 4, 4),
        TransactionType.CREDIT,
        new BigDecimal("5000.00"),
        "Payment received");
    addTransaction(
        ledger,
        LocalDate.of(2026, 4, 5),
        TransactionType.DEBIT,
        new BigDecimal("1000.00"),
        "Transport expense");

    VerificationSummary summary =
        closureService.verifyPeriod(
            ledger.getLedgerId(), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 5));

    assertThat(summary.totalCredit()).isEqualByComparingTo("15000.00");
    assertThat(summary.totalDebit()).isEqualByComparingTo("3500.00");
    assertThat(summary.closingBalance()).isEqualByComparingTo("61500.00");
    assertThat(summary.debitCount()).isEqualTo(2);
    assertThat(summary.creditCount()).isEqualTo(2);
  }

  // SRS 8.2 period-boundary rule: a narrower review window still inherits everything before it
  @Test
  void periodBoundaryRuleAdjustsOpeningBalanceForANarrowerRange() {
    Ledger ledger = activeLedger(LocalDate.of(2026, 4, 1), new BigDecimal("50000.00"));
    addTransaction(
        ledger,
        LocalDate.of(2026, 4, 2),
        TransactionType.CREDIT,
        new BigDecimal("10000.00"),
        "Received from Rahul");
    addTransaction(
        ledger,
        LocalDate.of(2026, 4, 10),
        TransactionType.DEBIT,
        new BigDecimal("4000.00"),
        "Later expense");

    VerificationSummary summary =
        closureService.verifyPeriod(
            ledger.getLedgerId(), LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 10));

    assertThat(summary.openingBalance()).isEqualByComparingTo("60000.00");
    assertThat(summary.closingBalance()).isEqualByComparingTo("56000.00");

    Ledger reloaded = ledgerRepository.findById(ledger.getLedgerId()).orElseThrow();
    assertThat(reloaded.getOpeningBalance()).isEqualByComparingTo("50000.00");
  }

  // TC-029, TC-031: verifying alone never closes the ledger or requires no separate confirmation
  @Test
  void verifyingDoesNotChangeOrCloseTheActiveLedger() {
    Ledger ledger = activeLedger(LocalDate.of(2026, 4, 1), new BigDecimal("50000.00"));
    addTransaction(
        ledger,
        LocalDate.of(2026, 4, 2),
        TransactionType.CREDIT,
        new BigDecimal("1000.00"),
        "Credit");

    closureService.verifyPeriod(
        ledger.getLedgerId(), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2));

    Ledger reloaded = ledgerRepository.findById(ledger.getLedgerId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(LedgerStatus.ACTIVE);
    assertThat(reloaded.getEndDate()).isNull();
    assertThat(reloaded.getClosingBalance()).isNull();

    addTransaction(
        ledger,
        LocalDate.of(2026, 4, 3),
        TransactionType.DEBIT,
        new BigDecimal("200.00"),
        "Still editable");
    assertThat(
            transactionRepository.findByLedger_LedgerIdOrderByTransactionDateAscTransactionIdAsc(
                ledger.getLedgerId()))
        .hasSize(2);
  }

  // TC-030: correcting a transaction and re-verifying updates the totals
  @Test
  void correctingATransactionAndReVerifyingUpdatesTheTotals() {
    Ledger ledger = activeLedger(LocalDate.of(2026, 4, 1), new BigDecimal("1000.00"));
    Transaction t =
        transactionRepository.save(
            new Transaction(
                ledger,
                LocalDate.of(2026, 4, 2),
                TransactionType.CREDIT,
                new BigDecimal("200.00"),
                "Sale"));

    VerificationSummary before =
        closureService.verifyPeriod(
            ledger.getLedgerId(), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2));
    assertThat(before.closingBalance()).isEqualByComparingTo("1200.00");

    t.setAmount(new BigDecimal("500.00"));
    transactionRepository.save(t);

    VerificationSummary after =
        closureService.verifyPeriod(
            ledger.getLedgerId(), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2));
    assertThat(after.closingBalance()).isEqualByComparingTo("1500.00");
  }

  // TC-032, TC-034-TC-038: a valid closure saves historical details and starts the next
  // ledger correctly, with nothing copied over. Dates mirror the SRS 9.2 worked example.
  @Test
  void closingAValidLedgerSavesHistoricalDetailsAndStartsTheNextLedger() {
    Ledger ledger = activeLedger(LocalDate.of(2026, 4, 1), new BigDecimal("50000.00"));
    addTransaction(
        ledger,
        LocalDate.of(2026, 4, 2),
        TransactionType.CREDIT,
        new BigDecimal("10000.00"),
        "Received from Rahul");
    addTransaction(
        ledger,
        LocalDate.of(2026, 4, 3),
        TransactionType.DEBIT,
        new BigDecimal("2500.00"),
        "Purchased material");

    Ledger nextLedger =
        closureService.closeLedger(
            ledger.getLedgerId(), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 9, 30));

    // TC-032
    Ledger closed = ledgerRepository.findById(ledger.getLedgerId()).orElseThrow();
    assertThat(closed.getStatus()).isEqualTo(LedgerStatus.CLOSED);
    assertThat(closed.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(closed.getTotalCredit()).isEqualByComparingTo("10000.00");
    assertThat(closed.getTotalDebit()).isEqualByComparingTo("2500.00");
    assertThat(closed.getClosingBalance()).isEqualByComparingTo("57500.00");
    assertThat(closed.getClosedAt()).isNotNull();
    assertThat(closed.getNextLedgerId()).isEqualTo(nextLedger.getLedgerId());

    // TC-034, TC-035, TC-036, TC-037
    assertThat(nextLedger.getLedgerId()).isNotEqualTo(closed.getLedgerId());
    assertThat(nextLedger.getStatus()).isEqualTo(LedgerStatus.ACTIVE);
    assertThat(nextLedger.getOpeningBalance()).isEqualByComparingTo("57500.00");
    assertThat(nextLedger.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(nextLedger.getTotalDebit()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(nextLedger.getTotalCredit()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(nextLedger.getPreviousLedgerId()).isEqualTo(closed.getLedgerId());

    // TC-038
    assertThat(
            transactionRepository.findByLedger_LedgerIdOrderByTransactionDateAscTransactionIdAsc(
                nextLedger.getLedgerId()))
        .isEmpty();

    assertThat(lifecycleService.getActiveLedger().map(Ledger::getLedgerId))
        .contains(nextLedger.getLedgerId());
  }

  // TC-039, TC-040: a repeated/simulated-failed closure request never produces a second
  // active ledger, and the original closed ledger is left untouched.
  @Test
  void repeatedCloseRequestIsRejectedAndLeavesExactlyOneActiveLedger() {
    Ledger ledger = activeLedger(LocalDate.of(2026, 4, 1), new BigDecimal("1000.00"));
    Long originalId = ledger.getLedgerId();

    closureService.closeLedger(originalId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

    assertThatThrownBy(
            () ->
                closureService.closeLedger(
                    originalId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
        .isInstanceOf(LedgerNotActiveException.class);

    Ledger stillClosed = ledgerRepository.findById(originalId).orElseThrow();
    assertThat(stillClosed.getStatus()).isEqualTo(LedgerStatus.CLOSED);
    assertThat(ledgerRepository.findAllByStatusOrderByClosedAtDesc(LedgerStatus.CLOSED)).hasSize(1);
    assertThat(lifecycleService.getActiveLedger()).isPresent();
    assertThat(ledgerRepository.count()).isEqualTo(2);
  }
}
