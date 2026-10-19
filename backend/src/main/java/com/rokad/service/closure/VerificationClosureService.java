package com.rokad.service.closure;

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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationClosureService {

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
  public Ledger closeLedger(Long ledgerId, LocalDate periodStart, LocalDate periodEnd) {
    Ledger ledger = requireMatchesActiveLedger(ledgerId);
    validatePeriod(ledger, periodStart, periodEnd);

    // Recalculate from the latest saved data at confirmation time -- never trust a
    // summary computed earlier, since transactions may have changed since verifyPeriod.
    VerificationSummary summary = buildSummary(ledger, periodStart, periodEnd);

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
        creditCount);
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
