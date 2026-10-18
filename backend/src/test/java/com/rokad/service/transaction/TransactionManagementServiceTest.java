package com.rokad.service.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

import com.rokad.config.ClockConfig;
import com.rokad.dto.LedgerSetupRequest;
import com.rokad.dto.TransactionRequestDTO;
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
  TransactionManagementService.class,
  LedgerLifecycleService.class,
  FinancialCalculationService.class,
  AuditService.class,
  ClockConfig.class
})
class TransactionManagementServiceTest {

  @Autowired private TransactionManagementService transactionService;
  @Autowired private LedgerLifecycleService lifecycleService;
  @Autowired private LedgerRepository ledgerRepository;
  @Autowired private TransactionRepository transactionRepository;

  private Ledger activeLedger() {
    return lifecycleService.createInitialLedger(
        new LedgerSetupRequest(null, LocalDate.of(2026, 10, 1), new BigDecimal("1000.00")));
  }

  // TC-005: a Credit increases the balance by the exact amount
  @Test
  void creditIncreasesBalanceByExactAmount() {
    Ledger ledger = activeLedger();

    transactionService.addTransaction(
        new TransactionRequestDTO(
            LocalDate.of(2026, 10, 2), TransactionType.CREDIT, new BigDecimal("250.00"), "Sale"));

    Ledger updated = ledgerRepository.findById(ledger.getLedgerId()).orElseThrow();
    assertThat(updated.getTotalCredit()).isEqualByComparingTo("250.00");
    assertThat(lifecycleService.getLedgerSummary(updated).currentBalance())
        .isEqualByComparingTo("1250.00");
  }

  // TC-006: a Debit decreases the balance by the exact amount
  @Test
  void debitDecreasesBalanceByExactAmount() {
    Ledger ledger = activeLedger();

    transactionService.addTransaction(
        new TransactionRequestDTO(
            LocalDate.of(2026, 10, 2),
            TransactionType.DEBIT,
            new BigDecimal("300.00"),
            "Supplies"));

    Ledger updated = ledgerRepository.findById(ledger.getLedgerId()).orElseThrow();
    assertThat(updated.getTotalDebit()).isEqualByComparingTo("300.00");
    assertThat(lifecycleService.getLedgerSummary(updated).currentBalance())
        .isEqualByComparingTo("700.00");
  }

  // TC-007 / Section 16: empty reason is rejected
  @Test
  void blankReasonIsRejected() {
    activeLedger();

    assertThatThrownBy(
            () ->
                transactionService.addTransaction(
                    new TransactionRequestDTO(
                        LocalDate.of(2026, 10, 2),
                        TransactionType.CREDIT,
                        new BigDecimal("100.00"),
                        "   ")))
        .isInstanceOf(TransactionValidationException.class)
        .extracting("field")
        .isEqualTo("reason");
  }

  // TC-008 / Section 16: zero or negative amount is rejected
  @Test
  void zeroAmountIsRejected() {
    activeLedger();

    assertThatThrownBy(
            () ->
                transactionService.addTransaction(
                    new TransactionRequestDTO(
                        LocalDate.of(2026, 10, 2),
                        TransactionType.DEBIT,
                        BigDecimal.ZERO,
                        "Nothing")))
        .isInstanceOf(TransactionValidationException.class)
        .extracting("field")
        .isEqualTo("amount");
  }

  // Section 16: duplicate save attempt is prevented
  @Test
  void immediateIdenticalResubmitIsRejected() {
    activeLedger();
    TransactionRequestDTO request =
        new TransactionRequestDTO(
            LocalDate.of(2026, 10, 2), TransactionType.CREDIT, new BigDecimal("100.00"), "Sale");
    transactionService.addTransaction(request);

    assertThatThrownBy(() -> transactionService.addTransaction(request))
        .isInstanceOf(TransactionValidationException.class)
        .extracting("field")
        .isEqualTo("duplicate");
  }

  // TC-011: editing recalculates balance and totals
  @Test
  void editingRecalculatesBalanceAndTotals() {
    Ledger ledger = activeLedger();
    Transaction saved =
        transactionService.addTransaction(
            new TransactionRequestDTO(
                LocalDate.of(2026, 10, 2),
                TransactionType.CREDIT,
                new BigDecimal("200.00"),
                "Sale"));

    transactionService.editTransaction(
        saved.getTransactionId(),
        new TransactionRequestDTO(
            LocalDate.of(2026, 10, 2),
            TransactionType.CREDIT,
            new BigDecimal("500.00"),
            "Corrected"));

    Ledger updated = ledgerRepository.findById(ledger.getLedgerId()).orElseThrow();
    assertThat(updated.getTotalCredit()).isEqualByComparingTo("500.00");
  }

  // TC-012: changing a transaction's type moves it to the other side
  @Test
  void changingTypeMovesTransactionToTheOtherSide() {
    Ledger ledger = activeLedger();
    Transaction saved =
        transactionService.addTransaction(
            new TransactionRequestDTO(
                LocalDate.of(2026, 10, 2),
                TransactionType.CREDIT,
                new BigDecimal("200.00"),
                "Sale"));

    transactionService.editTransaction(
        saved.getTransactionId(),
        new TransactionRequestDTO(
            LocalDate.of(2026, 10, 2),
            TransactionType.DEBIT,
            new BigDecimal("200.00"),
            "Refund instead"));

    Ledger updated = ledgerRepository.findById(ledger.getLedgerId()).orElseThrow();
    assertThat(updated.getTotalCredit()).isEqualByComparingTo("0.00");
    assertThat(updated.getTotalDebit()).isEqualByComparingTo("200.00");
  }

  // TC-013: deleting updates totals and balance
  @Test
  void deletingRecalculatesBalanceAndTotals() {
    Ledger ledger = activeLedger();
    Transaction saved =
        transactionService.addTransaction(
            new TransactionRequestDTO(
                LocalDate.of(2026, 10, 2),
                TransactionType.CREDIT,
                new BigDecimal("400.00"),
                "Sale"));

    transactionService.deleteTransaction(saved.getTransactionId());

    Ledger updated = ledgerRepository.findById(ledger.getLedgerId()).orElseThrow();
    assertThat(updated.getTotalCredit()).isEqualByComparingTo("0.00");
    assertThat(transactionRepository.count()).isZero();
  }

  // BR-010 / Section 16: closed ledger edit attempt is rejected
  @Test
  void editingATransactionOnAClosedLedgerIsRejected() {
    Ledger ledger = activeLedger();
    Transaction saved =
        transactionService.addTransaction(
            new TransactionRequestDTO(
                LocalDate.of(2026, 10, 2),
                TransactionType.CREDIT,
                new BigDecimal("200.00"),
                "Sale"));
    ledger.setStatus(LedgerStatus.CLOSED);
    ledgerRepository.save(ledger);

    assertThatThrownBy(
            () ->
                transactionService.editTransaction(
                    saved.getTransactionId(),
                    new TransactionRequestDTO(
                        LocalDate.of(2026, 10, 2),
                        TransactionType.CREDIT,
                        new BigDecimal("999.00"),
                        "Should not apply")))
        .isInstanceOf(ClosedLedgerModificationException.class);
  }
}
