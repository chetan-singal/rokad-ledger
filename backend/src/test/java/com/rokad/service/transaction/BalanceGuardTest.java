package com.rokad.service.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rokad.dto.LedgerSetupRequest;
import com.rokad.dto.TransactionRequestDTO;
import com.rokad.entity.Ledger;
import com.rokad.entity.Transaction;
import com.rokad.entity.TransactionType;
import com.rokad.repository.LedgerRepository;
import com.rokad.repository.TransactionRepository;
import com.rokad.service.lifecycle.LedgerLifecycleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Rule R7. No @Transactional on purpose: every service call really commits or rolls back.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class BalanceGuardTest {

  @Autowired private TransactionManagementService transactionService;
  @Autowired private LedgerLifecycleService lifecycleService;
  @Autowired private LedgerRepository ledgerRepository;
  @Autowired private TransactionRepository transactionRepository;

  private final LocalDate today = LocalDate.now();

  @BeforeEach
  @AfterEach
  void cleanSlate() {
    transactionRepository.deleteAll();
    List<Ledger> ledgers = ledgerRepository.findAll();
    ledgers.forEach(
        l -> {
          l.setPreviousLedgerId(null);
          l.setNextLedgerId(null);
        });
    ledgerRepository.saveAll(ledgers);
    ledgerRepository.deleteAll();
  }

  private void activeLedger(String openingBalance) {
    lifecycleService.createInitialLedger(
        new LedgerSetupRequest(null, today.minusDays(10), new BigDecimal(openingBalance)));
  }

  private TransactionRequestDTO request(TransactionType type, String amount, String reason) {
    return new TransactionRequestDTO(today.minusDays(1), type, new BigDecimal(amount), reason);
  }

  private BigDecimal currentBalance() {
    return lifecycleService
        .getLedgerSummary(lifecycleService.getActiveLedgerOrThrow())
        .currentBalance();
  }

  // TC-059: a debit larger than the balance is refused and nothing is saved
  @Test
  void aDebitLargerThanTheBalanceIsRejectedAndNothingIsSaved() {
    activeLedger("1000.00");

    assertThatThrownBy(
            () ->
                transactionService.addTransaction(
                    request(TransactionType.DEBIT, "1500.00", "Too much")))
        .isInstanceOf(TransactionValidationException.class)
        .hasMessageContaining("cannot go below zero");

    assertThat(transactionRepository.count()).isEqualTo(0);
    assertThat(currentBalance()).isEqualByComparingTo("1000.00");
  }

  // TC-060: spending exactly the whole balance is allowed
  @Test
  void aDebitEqualToTheBalanceIsAllowed() {
    activeLedger("1000.00");

    transactionService.addTransaction(request(TransactionType.DEBIT, "1000.00", "Everything"));

    assertThat(currentBalance()).isEqualByComparingTo("0.00");
  }

  // TC-061: an edit that would take the balance below zero is refused; the entry is unchanged
  @Test
  void anEditThatWouldTakeTheBalanceBelowZeroIsRejectedAndTheEntryIsUnchanged() {
    activeLedger("0.00");
    Transaction credit =
        transactionService.addTransaction(request(TransactionType.CREDIT, "500.00", "Sale"));
    transactionService.addTransaction(request(TransactionType.DEBIT, "400.00", "Purchase"));

    assertThatThrownBy(
            () ->
                transactionService.editTransaction(
                    credit.getTransactionId(),
                    request(TransactionType.CREDIT, "100.00", "Sale, corrected")))
        .isInstanceOf(TransactionValidationException.class)
        .hasMessageContaining("cannot go below zero");

    Transaction reloaded = transactionRepository.findById(credit.getTransactionId()).orElseThrow();
    assertThat(reloaded.getAmount()).isEqualByComparingTo("500.00");
    assertThat(reloaded.getReason()).isEqualTo("Sale");
    assertThat(currentBalance()).isEqualByComparingTo("100.00");
  }

  // TC-062: deleting a credit that other entries depend on is refused
  @Test
  void deletingACreditThatFundsADebitIsRejected() {
    activeLedger("0.00");
    Transaction credit =
        transactionService.addTransaction(request(TransactionType.CREDIT, "500.00", "Sale"));
    transactionService.addTransaction(request(TransactionType.DEBIT, "400.00", "Purchase"));

    assertThatThrownBy(() -> transactionService.deleteTransaction(credit.getTransactionId()))
        .isInstanceOf(TransactionValidationException.class)
        .hasMessageContaining("cannot be deleted");

    assertThat(transactionRepository.count()).isEqualTo(2);
  }

  // TC-063: the audit text of an edit stays within its column, however long the reason is
  @Test
  void anEntryWithAVeryLongReasonCanStillBeEdited() {
    activeLedger("1000.00");
    String longReason = "r".repeat(500);
    Transaction added =
        transactionService.addTransaction(request(TransactionType.CREDIT, "10.00", longReason));

    transactionService.editTransaction(
        added.getTransactionId(), request(TransactionType.CREDIT, "20.00", longReason));

    Transaction reloaded = transactionRepository.findById(added.getTransactionId()).orElseThrow();
    assertThat(reloaded.getAmount()).isEqualByComparingTo("20.00");
  }
}
