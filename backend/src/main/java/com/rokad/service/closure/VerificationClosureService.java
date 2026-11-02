package com.rokad.service.closure;

import com.rokad.dto.ClosingWindowDTO;
import com.rokad.entity.AuditAction;
import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
import com.rokad.entity.Transaction;
import com.rokad.entity.TransactionType;
import com.rokad.repository.LedgerRepository;
import com.rokad.repository.TransactionRepository;
import com.rokad.service.audit.AuditService;
import com.rokad.service.calculation.BalanceResult;
import com.rokad.service.calculation.FinancialCalculationService;
import com.rokad.service.lifecycle.LedgerLifecycleService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationClosureService {

  private static final DateTimeFormatter MESSAGE_DATE =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

  private final LedgerRepository ledgerRepository;
  private final TransactionRepository transactionRepository;
  private final FinancialCalculationService calculationService;
  private final LedgerLifecycleService lifecycleService;
  private final AuditService auditService;
  private final Clock clock;

  public VerificationClosureService(
      LedgerRepository ledgerRepository,
      TransactionRepository transactionRepository,
      FinancialCalculationService calculationService,
      LedgerLifecycleService lifecycleService,
      AuditService auditService,
      Clock clock) {
    this.ledgerRepository = ledgerRepository;
    this.transactionRepository = transactionRepository;
    this.calculationService = calculationService;
    this.lifecycleService = lifecycleService;
    this.auditService = auditService;
    this.clock = clock;
  }

  // The end dates the active ledger can be closed on: from its latest entry (or its start
  // date while it has no entries) up to today. closeLedger enforces exactly this window.
  @Transactional(readOnly = true)
  public Optional<ClosingWindowDTO> getClosingWindow() {
    return lifecycleService.getActiveLedger().map(this::buildClosingWindow);
  }

  // Verification stays flexible: any period inside the ledger can be reviewed, including a
  // shorter one (SRS 8.2 period-boundary rule). Only closeLedger is strict.
  @Transactional
  public VerificationSummary verifyPeriod(
      Long ledgerId, LocalDate periodStart, LocalDate periodEnd) {
    Ledger ledger = requireMatchesActiveLedger(ledgerId);
    validatePeriod(ledger, periodStart, periodEnd);

    VerificationSummary summary = buildSummary(ledger, periodStart, periodEnd);

    auditService.log(
        AuditAction.LEDGER_VERIFY, "LEDGER", ledger.getLedgerId(), describeSummary(summary));
    return summary;
  }

  @Transactional
  public Ledger closeLedger(
      Long ledgerId, LocalDate periodStart, LocalDate periodEnd, VerificationSummary verified) {
    Ledger ledger = requireMatchesActiveLedger(ledgerId);
    if (verified == null) {
      throw new LedgerVerificationException("verified", "Verify the period before closing it.");
    }
    validatePeriod(ledger, periodStart, periodEnd);
    validateWholeLedgerPeriod(ledger, periodStart, periodEnd);

    // Recalculate from the latest saved data at confirmation time -- never trust a
    // summary computed earlier, since transactions may have changed since verifyPeriod.
    VerificationSummary summary = buildSummary(ledger, periodStart, periodEnd);
    requireSameFigures(verified, summary);
    if (summary.closingBalance().signum() < 0) {
      throw new LedgerVerificationException(
          "closingBalance",
          "The closing balance would be negative, so this ledger cannot be closed. "
              + "Correct the entries first.");
    }

    ledger.setEndDate(periodEnd);
    ledger.setTotalDebit(summary.totalDebit());
    ledger.setTotalCredit(summary.totalCredit());
    ledger.setClosingBalance(summary.closingBalance());
    ledger.setStatus(LedgerStatus.CLOSED);
    ledger.setClosedAt(LocalDateTime.now(clock));
    // Flush the old ledger's CLOSED status before inserting the new ACTIVE one: both
    // rows are covered by the same uq_ledger_one_active constraint, so the old row
    // must stop counting as active before the new row is allowed to start.
    ledgerRepository.saveAndFlush(ledger);

    Ledger nextLedger = new Ledger(periodEnd.plusDays(1), summary.closingBalance());
    nextLedger.setPreviousLedgerId(ledger.getLedgerId());
    Ledger savedNextLedger = ledgerRepository.save(nextLedger);

    ledger.setNextLedgerId(savedNextLedger.getLedgerId());

    auditService.log(
        AuditAction.LEDGER_CLOSE,
        "LEDGER",
        ledger.getLedgerId(),
        describeSummary(summary) + ", nextLedgerId=" + savedNextLedger.getLedgerId());

    return savedNextLedger;
  }

  private ClosingWindowDTO buildClosingWindow(Ledger ledger) {
    LocalDate lastEntryDate =
        transactionRepository.findLatestTransactionDate(ledger.getLedgerId()).orElse(null);
    LocalDate earliestEnd = ledger.getStartDate();
    if (lastEntryDate != null && lastEntryDate.isAfter(earliestEnd)) {
      earliestEnd = lastEntryDate;
    }
    LocalDate latestEnd = LocalDate.now(clock);
    return new ClosingWindowDTO(
        ledger.getLedgerId(),
        ledger.getStartDate(),
        lastEntryDate,
        earliestEnd,
        latestEnd,
        !earliestEnd.isAfter(latestEnd));
  }

  private VerificationSummary buildSummary(
      Ledger ledger, LocalDate periodStart, LocalDate periodEnd) {
    BigDecimal periodOpeningBalance = resolvePeriodOpeningBalance(ledger, periodStart);
    List<Transaction> periodTransactions =
        transactionRepository.findByLedgerAndDateRange(
            ledger.getLedgerId(), periodStart, periodEnd);

    BalanceResult result =
        calculationService.calculateBalance(periodOpeningBalance, periodTransactions);

    long debitCount =
        periodTransactions.stream()
            .filter(t -> t.getTransactionType() == TransactionType.DEBIT)
            .count();
    long creditCount = periodTransactions.size() - debitCount;

    return new VerificationSummary(
        ledger.getLedgerId(),
        periodStart,
        periodEnd,
        periodOpeningBalance,
        result.totalDebit(),
        result.totalCredit(),
        result.closingBalance(),
        debitCount,
        creditCount,
        periodEnd.plusDays(1));
  }

  // SRS Section 8.2 period-boundary rule: if the selected period starts after the
  // ledger's own start date, the opening balance for that period must still reflect
  // everything before it on this ledger -- the ledger's own opening_balance column is
  // never rewritten to make this work, it's purely a calculation-time adjustment.
  private BigDecimal resolvePeriodOpeningBalance(Ledger ledger, LocalDate periodStart) {
    if (!periodStart.isAfter(ledger.getStartDate())) {
      return ledger.getOpeningBalance();
    }
    List<Transaction> before =
        transactionRepository.findByLedgerAndDateRange(
            ledger.getLedgerId(), ledger.getStartDate(), periodStart.minusDays(1));
    return calculationService.calculateBalance(ledger.getOpeningBalance(), before).closingBalance();
  }

  private Ledger requireMatchesActiveLedger(Long ledgerId) {
    Ledger active = lifecycleService.getActiveLedgerOrThrow();
    if (!active.getLedgerId().equals(ledgerId)) {
      throw new LedgerNotActiveException(ledgerId);
    }
    return active;
  }

  private void validatePeriod(Ledger ledger, LocalDate periodStart, LocalDate periodEnd) {
    if (periodStart == null) {
      throw new LedgerVerificationException("periodStart", "Start date is required.");
    }
    if (periodEnd == null) {
      throw new LedgerVerificationException("periodEnd", "End date is required.");
    }
    if (periodEnd.isBefore(periodStart)) {
      throw new LedgerVerificationException("periodEnd", "End date cannot be before start date.");
    }
    if (periodStart.isBefore(ledger.getStartDate())) {
      throw new LedgerVerificationException(
          "periodStart", "Start date cannot be before the ledger's own start date.");
    }
    if (periodEnd.isAfter(LocalDate.now(clock))) {
      throw new LedgerVerificationException("periodEnd", "End date cannot be in the future.");
    }
  }

  // Rule R1 and R2: a ledger is closed as a whole. It starts on its own start date and runs to
  // a day on or after its latest entry, so no entry is ever left outside the closed period or
  // missing from the balance carried forward.
  private void validateWholeLedgerPeriod(
      Ledger ledger, LocalDate periodStart, LocalDate periodEnd) {
    if (!periodStart.equals(ledger.getStartDate())) {
      throw new LedgerVerificationException(
          "periodStart",
          "A ledger is closed as a whole, so the start date must be the ledger's own start date ("
              + MESSAGE_DATE.format(ledger.getStartDate())
              + ").");
    }
    transactionRepository
        .findLatestTransactionDate(ledger.getLedgerId())
        .filter(periodEnd::isBefore)
        .ifPresent(
            latest -> {
              throw new LedgerVerificationException(
                  "periodEnd",
                  "The end date cannot be earlier than your latest entry ("
                      + MESSAGE_DATE.format(latest)
                      + "). Entries after the end date would be left out of the closed ledger.");
            });
  }

  // Rule R5: the user confirmed the figures on screen. If the saved entries no longer give
  // exactly those figures, closing would save something they never saw (SRS 8.5 and 17.2).
  private void requireSameFigures(VerificationSummary verified, VerificationSummary actual) {
    boolean same =
        sameAmount(verified.totalDebit(), actual.totalDebit())
            && sameAmount(verified.totalCredit(), actual.totalCredit())
            && sameAmount(verified.closingBalance(), actual.closingBalance())
            && verified.debitCount() == actual.debitCount()
            && verified.creditCount() == actual.creditCount();
    if (!same) {
      throw new StaleVerificationException();
    }
  }

  // compareTo, not equals: 100 and 100.00 are the same amount (the browser sends plain numbers).
  private static boolean sameAmount(BigDecimal confirmed, BigDecimal actual) {
    return confirmed != null && confirmed.compareTo(actual) == 0;
  }

  private String describeSummary(VerificationSummary s) {
    return "period=%s to %s, opening=%s, totalCredit=%s, totalDebit=%s, closing=%s"
        .formatted(
            s.periodStart(),
            s.periodEnd(),
            s.openingBalance(),
            s.totalCredit(),
            s.totalDebit(),
            s.closingBalance());
  }
}
