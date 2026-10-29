package com.rokad.service.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

import com.rokad.config.ClockConfig;
import com.rokad.dto.LedgerOverviewDTO;
import com.rokad.dto.LedgerSetupRequest;
import com.rokad.dto.TransactionDTO;
import com.rokad.entity.Ledger;
import com.rokad.entity.Transaction;
import com.rokad.entity.TransactionType;
import com.rokad.repository.TransactionRepository;
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
  OverviewService.class,
  LedgerLifecycleService.class,
  FinancialCalculationService.class,
  ClockConfig.class
})
class OverviewServiceTest {

  @Autowired private OverviewService overviewService;
  @Autowired private LedgerLifecycleService lifecycleService;
  @Autowired private TransactionRepository transactionRepository;

  // The service reads the real clock (ClockConfig), so every date below is relative to today.
  private final LocalDate today = LocalDate.now();

  private Ledger activeLedger(String openingBalance) {
    return lifecycleService.createInitialLedger(
        new LedgerSetupRequest(null, today.minusDays(30), new BigDecimal(openingBalance)));
  }

  private void add(
      Ledger ledger, LocalDate date, TransactionType type, String amount, String reason) {
    transactionRepository.save(new Transaction(ledger, date, type, new BigDecimal(amount), reason));
  }

  // No active ledger: there is nothing to show (the endpoint answers 404)
  @Test
  void noOverviewWithoutAnActiveLedger() {
    assertThat(overviewService.getActiveOverview()).isEmpty();
  }

  // SRS 6.3, TC-003, TC-023: an empty ledger shows its opening balance, zero totals, no activity
  @Test
  void emptyLedgerShowsTheOpeningBalanceZeroTotalsAndNoActivity() {
    activeLedger("5000.00");

    LedgerOverviewDTO overview = overviewService.getActiveOverview().orElseThrow();

    assertThat(overview.summary().currentBalance()).isEqualByComparingTo("5000.00");
    assertThat(overview.summary().transactionCount()).isZero();
    assertThat(overview.todayDebit()).isEqualByComparingTo("0");
    assertThat(overview.todayCredit()).isEqualByComparingTo("0");
    assertThat(overview.recentTransactions()).isEmpty();
  }

  // SRS 7.3, TC-025: today's totals count only entries dated today, several on one day included
  @Test
  void todaysTotalsCountOnlyEntriesDatedToday() {
    Ledger ledger = activeLedger("1000.00");
    add(ledger, today, TransactionType.CREDIT, "100.00", "Sale today");
    add(ledger, today, TransactionType.DEBIT, "40.00", "Supplies today");
    add(ledger, today, TransactionType.DEBIT, "10.50", "Tea today");
    add(ledger, today.minusDays(1), TransactionType.CREDIT, "999.00", "Yesterday's sale");
    add(ledger, today.plusDays(1), TransactionType.DEBIT, "777.00", "Dated tomorrow");

    LedgerOverviewDTO overview = overviewService.getActiveOverview().orElseThrow();

    assertThat(overview.today()).isEqualTo(today);
    assertThat(overview.todayCredit()).isEqualByComparingTo("100.00");
    assertThat(overview.todayDebit()).isEqualByComparingTo("50.50");
    // the whole-ledger figures still cover every entry
    assertThat(overview.summary().totalCredit()).isEqualByComparingTo("1099.00");
    assertThat(overview.summary().transactionCount()).isEqualTo(5);
  }

  @Test
  void todaysTotalsAreZeroWhenNothingIsDatedToday() {
    Ledger ledger = activeLedger("1000.00");
    add(ledger, today.minusDays(3), TransactionType.CREDIT, "250.00", "Older credit");

    LedgerOverviewDTO overview = overviewService.getActiveOverview().orElseThrow();

    assertThat(overview.todayDebit()).isEqualByComparingTo("0");
    assertThat(overview.todayCredit()).isEqualByComparingTo("0");
    assertThat(overview.summary().currentBalance()).isEqualByComparingTo("1250.00");
  }

  // SRS 6.2: recent activity is newest first and capped at five; same-day entries: later id first
  @Test
  void recentActivityIsNewestFirstAndCappedAtFive() {
    Ledger ledger = activeLedger("0.00");
    add(ledger, today.minusDays(6), TransactionType.CREDIT, "1.00", "e1 oldest");
    add(ledger, today.minusDays(5), TransactionType.CREDIT, "2.00", "e2");
    add(ledger, today.minusDays(4), TransactionType.DEBIT, "3.00", "e3");
    add(ledger, today.minusDays(2), TransactionType.CREDIT, "4.00", "e4");
    add(ledger, today.minusDays(1), TransactionType.DEBIT, "5.00", "e5");
    add(ledger, today, TransactionType.CREDIT, "6.00", "e6 first today");
    add(ledger, today, TransactionType.DEBIT, "7.00", "e7 second today");

    LedgerOverviewDTO overview = overviewService.getActiveOverview().orElseThrow();

    assertThat(overview.recentTransactions())
        .extracting(TransactionDTO::reason)
        .containsExactly("e7 second today", "e6 first today", "e5", "e4", "e3");
  }

  // FR-006: Home and Dashboard read the very same summary the Current Ledger screen gets
  @Test
  void overviewSummaryMatchesTheLedgerSummary() {
    Ledger ledger = activeLedger("2000.00");
    add(ledger, today, TransactionType.DEBIT, "300.00", "Rent");
    add(ledger, today.minusDays(2), TransactionType.CREDIT, "800.00", "Client payment");

    LedgerOverviewDTO overview = overviewService.getActiveOverview().orElseThrow();

    assertThat(overview.summary()).isEqualTo(lifecycleService.getLedgerSummary(ledger));
  }
}
