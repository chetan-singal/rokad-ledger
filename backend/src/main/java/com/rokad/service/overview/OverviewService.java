package com.rokad.service.overview;

import com.rokad.dto.LedgerOverviewDTO;
import com.rokad.dto.LedgerSummaryDTO;
import com.rokad.dto.TransactionDTO;
import com.rokad.entity.Ledger;
import com.rokad.entity.Transaction;
import com.rokad.repository.TransactionRepository;
import com.rokad.service.calculation.BalanceResult;
import com.rokad.service.calculation.FinancialCalculationService;
import com.rokad.service.lifecycle.LedgerLifecycleService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OverviewService {

  // How many entries Home's "recent activity" lists (SRS 6.2).
  public static final int RECENT_ACTIVITY_LIMIT = 5;

  private final LedgerLifecycleService lifecycleService;
  private final TransactionRepository transactionRepository;
  private final FinancialCalculationService calculationService;
  private final Clock clock;

  public OverviewService(
      LedgerLifecycleService lifecycleService,
      TransactionRepository transactionRepository,
      FinancialCalculationService calculationService,
      Clock clock) {
    this.lifecycleService = lifecycleService;
    this.transactionRepository = transactionRepository;
    this.calculationService = calculationService;
    this.clock = clock;
  }

  // One read-only transaction, so the summary, today's totals and the recent list are
  // all taken from the same moment and can never disagree with each other.
  @Transactional(readOnly = true)
  public Optional<LedgerOverviewDTO> getActiveOverview() {
    return lifecycleService.getActiveLedger().map(this::buildOverview);
  }

  private LedgerOverviewDTO buildOverview(Ledger ledger) {
    LocalDate today = LocalDate.now(clock);
    LedgerSummaryDTO summary = lifecycleService.getLedgerSummary(ledger);

    // Today's totals come from the same engine as every other number (FR-006): the
    // entries dated today go through calculateBalance, and nothing is summed here.
    List<Transaction> datedToday =
        transactionRepository.findByLedgerAndDateRange(ledger.getLedgerId(), today, today);
    BalanceResult todays = calculationService.calculateBalance(BigDecimal.ZERO, datedToday);

    List<TransactionDTO> recent =
        transactionRepository
            .findByLedger_LedgerIdOrderByTransactionDateDescTransactionIdDesc(
                ledger.getLedgerId(), PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
            .stream()
            .map(TransactionDTO::from)
            .toList();

    return new LedgerOverviewDTO(summary, today, todays.totalDebit(), todays.totalCredit(), recent);
  }
}
