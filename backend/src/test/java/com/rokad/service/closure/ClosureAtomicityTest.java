package com.rokad.service.closure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.rokad.dto.LedgerSetupRequest;
import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

// No @Transactional here on purpose: closeLedger's own transaction really commits or rolls back.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ClosureAtomicityTest {

  @Autowired private VerificationClosureService closureService;
  @Autowired private LedgerLifecycleService lifecycleService;
  @Autowired private TransactionRepository transactionRepository;
  @SpyBean private LedgerRepository ledgerRepository;

  @BeforeEach
  @AfterEach
  void cleanSlate() {
    Mockito.reset(ledgerRepository);
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

  // TC-058 (proves TC-040 and BR-015 for real): a failure while starting the next ledger
  // undoes the whole closure
  @Test
  void aFailureWhileStartingTheNextLedgerRollsBackTheWholeClosure() {
    LocalDate start = LocalDate.now().minusDays(20);
    LocalDate end = LocalDate.now().minusDays(1);
    Ledger ledger =
        lifecycleService.createInitialLedger(
            new LedgerSetupRequest(null, start, new BigDecimal("1000.00")));
    Long id = ledger.getLedgerId();
    transactionRepository.save(
        new Transaction(
            ledger, start.plusDays(1), TransactionType.CREDIT, new BigDecimal("250.00"), "Sale"));
    VerificationSummary verified = closureService.verifyPeriod(id, start, end);

    // Fail only the insert of the next ledger (the one that has no id yet), which happens
    // after the old ledger has already been marked CLOSED and flushed.
    doThrow(new IllegalStateException("simulated failure"))
        .when(ledgerRepository)
        .save(argThat((Ledger l) -> l.getLedgerId() == null));

    assertThatThrownBy(() -> closureService.closeLedger(id, start, end, verified))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("simulated failure");

    Mockito.reset(ledgerRepository);
    Ledger after = ledgerRepository.findById(id).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(LedgerStatus.ACTIVE);
    assertThat(after.getEndDate()).isNull();
    assertThat(after.getClosingBalance()).isNull();
    assertThat(after.getClosedAt()).isNull();
    assertThat(after.getNextLedgerId()).isNull();
    assertThat(ledgerRepository.count()).isEqualTo(1);
    assertThat(lifecycleService.getActiveLedger().map(Ledger::getLedgerId)).contains(id);
  }
}
