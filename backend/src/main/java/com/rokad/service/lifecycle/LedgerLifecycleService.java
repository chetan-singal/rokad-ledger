package com.rokad.service.lifecycle;

import com.rokad.dto.LedgerSetupRequest;
import com.rokad.dto.LedgerSummaryDTO;
import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
import com.rokad.entity.Transaction;
import com.rokad.repository.LedgerRepository;
import com.rokad.repository.TransactionRepository;
import com.rokad.service.calculation.BalanceResult;
import com.rokad.service.calculation.FinancialCalculationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerLifecycleService {

  private final LedgerRepository ledgerRepository;
  private final TransactionRepository transactionRepository;
  private final FinancialCalculationService calculationService;

  public LedgerLifecycleService(
      LedgerRepository ledgerRepository,
      TransactionRepository transactionRepository,
      FinancialCalculationService calculationService) {
    this.ledgerRepository = ledgerRepository;
    this.transactionRepository = transactionRepository;
    this.calculationService = calculationService;
  }

  public Optional<Ledger> getActiveLedger() {
    return ledgerRepository.findFirstByStatus(LedgerStatus.ACTIVE);
  }

  public Ledger getActiveLedgerOrThrow() {
    return getActiveLedger()
        .orElseThrow(
            () -> new IllegalStateException("No active ledger exists. Initial setup has not run."));
  }

  @Transactional
  public Ledger createInitialLedger(LedgerSetupRequest request) {
    if (getActiveLedger().isPresent()) {
      throw new IllegalStateException("An active ledger already exists; cannot create another.");
    }
    if (request.openingBalance() == null
        || request.openingBalance().compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Opening balance must be zero or greater.");
    }

    Ledger ledger = new Ledger(request.startDate(), request.openingBalance());
    ledger.setLedgerName(request.ledgerName());
    return ledgerRepository.save(ledger);
  }

  public LedgerSummaryDTO getLedgerSummary(Ledger ledger) {
    List<Transaction> transactions =
        transactionRepository.findByLedger_LedgerIdOrderByTransactionDateAscTransactionIdAsc(
            ledger.getLedgerId());

    if (ledger.getStatus() == LedgerStatus.CLOSED) {
      return new LedgerSummaryDTO(
          ledger.getLedgerId(),
          ledger.getLedgerName(),
          ledger.getStartDate(),
          ledger.getStatus(),
          ledger.getOpeningBalance(),
          ledger.getClosingBalance(),
          ledger.getTotalDebit(),
          ledger.getTotalCredit(),
          transactions.size());
    }

    BalanceResult result =
        calculationService.calculateBalance(ledger.getOpeningBalance(), transactions);
    return new LedgerSummaryDTO(
        ledger.getLedgerId(),
        ledger.getLedgerName(),
        ledger.getStartDate(),
        ledger.getStatus(),
        ledger.getOpeningBalance(),
        result.closingBalance(),
        result.totalDebit(),
        result.totalCredit(),
        transactions.size());
  }
}
