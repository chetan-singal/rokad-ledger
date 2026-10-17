package com.rokad.repository;

import com.rokad.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("test")
class TransactionRepositoryTest {

    @Autowired private LedgerRepository ledgerRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void sameDayTransactionsComeBackInInsertOrder() {
        Ledger ledger = ledgerRepository.save(
            new Ledger(LocalDate.of(2026, 10, 1), BigDecimal.ZERO));

        Transaction first = transactionRepository.save(new Transaction(
            ledger, LocalDate.of(2026, 10, 5), TransactionType.DEBIT,
            new BigDecimal("500.00"), "Office supplies"));
        Transaction second = transactionRepository.save(new Transaction(
            ledger, LocalDate.of(2026, 10, 5), TransactionType.CREDIT,
            new BigDecimal("1200.00"), "Client payment"));

        List<Transaction> result = transactionRepository
            .findByLedger_LedgerIdOrderByTransactionDateAscTransactionIdAsc(ledger.getLedgerId());

        assertThat(result).extracting(Transaction::getTransactionId)
            .containsExactly(first.getTransactionId(), second.getTransactionId());
    }
        @Test
    void dateRangeIncludesBoundariesAndExcludesOutside() {
        Ledger ledger = ledgerRepository.save(
            new Ledger(LocalDate.of(2026, 10, 1), BigDecimal.ZERO));

        transactionRepository.save(new Transaction(
            ledger, LocalDate.of(2026, 10, 4), TransactionType.DEBIT,
            new BigDecimal("100.00"), "Before range"));
        Transaction startBoundary = transactionRepository.save(new Transaction(
            ledger, LocalDate.of(2026, 10, 5), TransactionType.DEBIT,
            new BigDecimal("200.00"), "Start boundary"));
        Transaction endBoundary = transactionRepository.save(new Transaction(
            ledger, LocalDate.of(2026, 10, 10), TransactionType.CREDIT,
            new BigDecimal("300.00"), "End boundary"));
        transactionRepository.save(new Transaction(
            ledger, LocalDate.of(2026, 10, 11), TransactionType.CREDIT,
            new BigDecimal("400.00"), "After range"));

        List<Transaction> result = transactionRepository.findByLedgerAndDateRange(
            ledger.getLedgerId(), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10));

        assertThat(result).extracting(Transaction::getTransactionId)
            .containsExactly(startBoundary.getTransactionId(), endBoundary.getTransactionId());
    }
}