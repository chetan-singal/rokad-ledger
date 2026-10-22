package com.rokad.service.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

import com.rokad.dto.LedgerSetupRequest;
import com.rokad.dto.LedgerSummaryDTO;
import com.rokad.entity.Ledger;
import com.rokad.repository.LedgerRepository;
import com.rokad.service.calculation.FinancialCalculationService;
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
@Import({LedgerLifecycleService.class, FinancialCalculationService.class})
class LedgerLifecycleServiceTest {

  @Autowired private LedgerLifecycleService lifecycleService;
  @Autowired private LedgerRepository ledgerRepository;

  // TC-001: nothing to load before setup has run
  @Test
  void noActiveLedgerBeforeSetup() {
    assertThat(lifecycleService.getActiveLedger()).isEmpty();
  }

  // TC-002: zero opening balance accepted
  @Test
  void zeroOpeningBalanceIsAccepted() {
    Ledger ledger =
        lifecycleService.createInitialLedger(
            new LedgerSetupRequest(null, LocalDate.of(2026, 10, 20), BigDecimal.ZERO));

    assertThat(ledger.getLedgerId()).isNotNull();
    assertThat(ledger.getOpeningBalance()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // TC-003: opening balance is the current balance before any transactions
  @Test
  void openingBalanceIsCurrentBalanceWithNoTransactionsYet() {
    Ledger ledger =
        lifecycleService.createInitialLedger(
            new LedgerSetupRequest(
                "October", LocalDate.of(2026, 10, 1), new BigDecimal("5000.00")));

    LedgerSummaryDTO summary = lifecycleService.getLedgerSummary(ledger);

    assertThat(summary.currentBalance()).isEqualByComparingTo("5000.00");
    assertThat(summary.transactionCount()).isZero();
  }

  // TC-004: restarting restores the existing ledger instead of duplicating it
  @Test
  void secondInitialLedgerIsRejectedOnceOneExists() {
    lifecycleService.createInitialLedger(
        new LedgerSetupRequest(null, LocalDate.of(2026, 10, 1), BigDecimal.ZERO));

    assertThatThrownBy(
            () ->
                lifecycleService.createInitialLedger(
                    new LedgerSetupRequest(
                        null, LocalDate.of(2026, 10, 2), new BigDecimal("100.00"))))
        .isInstanceOf(IllegalStateException.class);

    assertThat(ledgerRepository.count()).isEqualTo(1);
  }

  // FR-001: a setup request without a start date is a clean 400, not a database error
  @Test
  void missingStartDateIsRejected() {
    assertThatThrownBy(
            () ->
                lifecycleService.createInitialLedger(
                    new LedgerSetupRequest(null, null, new BigDecimal("100.00"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Start date is required.");

    assertThat(ledgerRepository.count()).isZero();
  }
}
