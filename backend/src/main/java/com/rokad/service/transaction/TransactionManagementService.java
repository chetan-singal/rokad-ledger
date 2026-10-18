package com.rokad.service.transaction;

import com.rokad.dto.TransactionRequestDTO;
import com.rokad.entity.AuditAction;
import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
import com.rokad.entity.Transaction;
import com.rokad.repository.TransactionRepository;
import com.rokad.service.audit.AuditService;
import com.rokad.service.calculation.BalanceResult;
import com.rokad.service.calculation.FinancialCalculationService;
import com.rokad.service.lifecycle.LedgerLifecycleService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionManagementService {

  private static final Duration DUPLICATE_WINDOW = Duration.ofSeconds(2);

  private final TransactionRepository transactionRepository;
  private final LedgerLifecycleService lifecycleService;
  private final FinancialCalculationService calculationService;
  private final AuditService auditService;
  private final Clock clock;

  public TransactionManagementService(
      TransactionRepository transactionRepository,
      LedgerLifecycleService lifecycleService,
      FinancialCalculationService calculationService,
      AuditService auditService,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.lifecycleService = lifecycleService;
    this.calculationService = calculationService;
    this.auditService = auditService;
    this.clock = clock;
  }

  @Transactional
  public Transaction addTransaction(TransactionRequestDTO request) {
    Ledger ledger = lifecycleService.getActiveLedgerOrThrow();
    validate(request, ledger);
    guardAgainstDuplicateSave(ledger, request);

    Transaction transaction =
        new Transaction(
            ledger,
            request.transactionDate(),
            request.transactionType(),
            request.amount(),
            request.reason());
    Transaction saved = transactionRepository.save(transaction);

    recalculateAndPersist(ledger);
    auditService.log(
        AuditAction.TRANSACTION_CREATE,
        "TRANSACTION",
        saved.getTransactionId(),
        describeTransaction(saved));
    return saved;
  }

  @Transactional
  public Transaction editTransaction(Long transactionId, TransactionRequestDTO request) {
    Transaction transaction =
        transactionRepository
            .findById(transactionId)
            .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    Ledger ledger = transaction.getLedger();
    requireActiveLedger(ledger);
    validate(request, ledger);
    String before = describeTransaction(transaction);

    transaction.setTransactionDate(request.transactionDate());
    transaction.setTransactionType(request.transactionType());
    transaction.setAmount(request.amount());
    transaction.setReason(request.reason());
    Transaction saved = transactionRepository.save(transaction);

    recalculateAndPersist(ledger);
    auditService.log(
        AuditAction.TRANSACTION_UPDATE,
        "TRANSACTION",
        saved.getTransactionId(),
        "before: " + before + " | after: " + describeTransaction(saved));
    return saved;
  }

  @Transactional
  public void deleteTransaction(Long transactionId) {
    Transaction transaction =
        transactionRepository
            .findById(transactionId)
            .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    Ledger ledger = transaction.getLedger();
    requireActiveLedger(ledger);
    String description = describeTransaction(transaction);

    transactionRepository.delete(transaction);

    recalculateAndPersist(ledger);
    auditService.log(AuditAction.TRANSACTION_DELETE, "TRANSACTION", transactionId, description);
  }

  private void requireActiveLedger(Ledger ledger) {
    if (ledger.getStatus() != LedgerStatus.ACTIVE) {
      throw new ClosedLedgerModificationException(ledger.getLedgerId());
    }
  }

  private void validate(TransactionRequestDTO request, Ledger ledger) {
    if (request.transactionType() == null) {
      throw new TransactionValidationException("transactionType", "Transaction type is required.");
    }
    if (request.transactionDate() == null) {
      throw new TransactionValidationException("transactionDate", "Transaction date is required.");
    }
    if (request.transactionDate().isBefore(ledger.getStartDate())) {
      throw new TransactionValidationException(
          "transactionDate", "Transaction date cannot be before the ledger's start date.");
    }
    if (request.transactionDate().isAfter(LocalDate.now(clock))) {
      throw new TransactionValidationException(
          "transactionDate", "Future-dated transactions are not allowed.");
    }
    if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new TransactionValidationException("amount", "Amount must be greater than zero.");
    }
    if (request.reason() == null || request.reason().isBlank()) {
      throw new TransactionValidationException("reason", "Reason cannot be empty.");
    }
  }

  private void guardAgainstDuplicateSave(Ledger ledger, TransactionRequestDTO request) {
    LocalDateTime cutoff = LocalDateTime.now(clock).minus(DUPLICATE_WINDOW);
    boolean duplicate =
        transactionRepository.existsRecentDuplicate(
            ledger.getLedgerId(),
            request.transactionDate(),
            request.transactionType(),
            request.amount(),
            request.reason(),
            cutoff);
    if (duplicate) {
      throw new TransactionValidationException(
          "duplicate",
          "An identical transaction was just saved. Refresh before saving again if this is intentional.");
    }
  }

  private void recalculateAndPersist(Ledger ledger) {
    List<Transaction> transactions =
        transactionRepository.findByLedger_LedgerIdOrderByTransactionDateAscTransactionIdAsc(
            ledger.getLedgerId());
    BalanceResult result =
        calculationService.calculateBalance(ledger.getOpeningBalance(), transactions);
    ledger.setTotalDebit(result.totalDebit());
    ledger.setTotalCredit(result.totalCredit());
  }

  private String describeTransaction(Transaction t) {
    return "date=%s, type=%s, amount=%s, reason=%s"
        .formatted(t.getTransactionDate(), t.getTransactionType(), t.getAmount(), t.getReason());
  }
}
