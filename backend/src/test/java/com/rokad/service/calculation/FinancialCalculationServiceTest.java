package com.rokad.service.calculation;

import com.rokad.entity.Ledger;
import com.rokad.entity.Transaction;
import com.rokad.entity.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialCalculationServiceTest {

    private final FinancialCalculationService service = new FinancialCalculationService();
    private final Ledger ledger = new Ledger(LocalDate.of(2026, 4, 1), new BigDecimal("50000.00"));

    // TC-022: the worked example from SRS Section 4.6
    @Test
    void worksTheSrsExample() {
        List<Transaction> transactions = List.of(
            new Transaction(ledger, LocalDate.of(2026, 4, 2), TransactionType.CREDIT,
                new BigDecimal("10000.00"), "Received from Rahul"),
            new Transaction(ledger, LocalDate.of(2026, 4, 3), TransactionType.DEBIT,
                new BigDecimal("2500.00"), "Purchased material"),
            new Transaction(ledger, LocalDate.of(2026, 4, 4), TransactionType.CREDIT,
                new BigDecimal("5000.00"), "Payment received"),
            new Transaction(ledger, LocalDate.of(2026, 4, 5), TransactionType.DEBIT,
                new BigDecimal("1000.00"), "Transport expense"));

        BalanceResult result = service.calculateBalance(new BigDecimal("50000.00"), transactions);

        assertThat(result.totalCredit()).isEqualByComparingTo("15000.00");
        assertThat(result.totalDebit()).isEqualByComparingTo("3500.00");
        assertThat(result.closingBalance()).isEqualByComparingTo("61500.00");
        assertThat(result.runningBalances()).extracting(
                BalanceResult.RunningBalanceEntry::balanceAfter)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("60000.00"), new BigDecimal("57500.00"),
                new BigDecimal("62500.00"), new BigDecimal("61500.00"));
    }

    // TC-023: no transactions
    @Test
    void noTransactionsLeavesBalanceAtOpening() {
        BalanceResult result = service.calculateBalance(new BigDecimal("50000.00"), List.of());

        assertThat(result.closingBalance()).isEqualByComparingTo("50000.00");
        assertThat(result.runningBalances()).isEmpty();
    }

    // TC-024: equal debit and credit totals
    @Test
    void equalDebitAndCreditLeavesBalanceUnchanged() {
        List<Transaction> transactions = List.of(
            new Transaction(ledger, LocalDate.of(2026, 4, 2), TransactionType.CREDIT,
                new BigDecimal("4000.00"), "Credit"),
            new Transaction(ledger, LocalDate.of(2026, 4, 3), TransactionType.DEBIT,
                new BigDecimal("4000.00"), "Debit"));

        BalanceResult result = service.calculateBalance(new BigDecimal("20000.00"), transactions);

        assertThat(result.closingBalance()).isEqualByComparingTo("20000.00");
    }

    // TC-025: multiple same-day transactions
    @Test
    void sameDayTransactionsAllCountTowardTotals() {
        LocalDate sameDay = LocalDate.of(2026, 4, 2);
        List<Transaction> transactions = List.of(
            new Transaction(ledger, sameDay, TransactionType.CREDIT, new BigDecimal("1000.00"), "Credit A"),
            new Transaction(ledger, sameDay, TransactionType.CREDIT, new BigDecimal("2000.00"), "Credit B"),
            new Transaction(ledger, sameDay, TransactionType.DEBIT, new BigDecimal("500.00"), "Debit A"));

        BalanceResult result = service.calculateBalance(new BigDecimal("1000.00"), transactions);

        assertThat(result.totalCredit()).isEqualByComparingTo("3000.00");
        assertThat(result.totalDebit()).isEqualByComparingTo("500.00");
        assertThat(result.closingBalance()).isEqualByComparingTo("3500.00");
    }

    // TC-026: running balance follows chronological order, not input (list) order
    @Test
    void runningBalanceFollowsChronologicalOrderNotInputOrder() {
        Transaction day3 = new Transaction(ledger, LocalDate.of(2026, 4, 3), TransactionType.DEBIT,
            new BigDecimal("500.00"), "Day 3");
        Transaction day1 = new Transaction(ledger, LocalDate.of(2026, 4, 1), TransactionType.CREDIT,
            new BigDecimal("1000.00"), "Day 1");
        Transaction day2 = new Transaction(ledger, LocalDate.of(2026, 4, 2), TransactionType.CREDIT,
            new BigDecimal("2000.00"), "Day 2");

        // Deliberately handed out of order: day3, day1, day2
        BalanceResult result = service.calculateBalance(
            new BigDecimal("1000.00"), List.of(day3, day1, day2));

        assertThat(result.runningBalances()).extracting(
                BalanceResult.RunningBalanceEntry::transactionDate)
            .containsExactly(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3));
        assertThat(result.closingBalance()).isEqualByComparingTo("3500.00");
    }
}