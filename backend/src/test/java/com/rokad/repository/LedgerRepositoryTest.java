package com.rokad.repository;

import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("test")
class LedgerRepositoryTest {

    @Autowired private LedgerRepository ledgerRepository;

    @Test
    void findFirstByStatusReturnsTheActiveLedger() {
        Ledger ledger = ledgerRepository.save(
            new Ledger(LocalDate.of(2026, 10, 1), BigDecimal.ZERO));

        Optional<Ledger> result = ledgerRepository.findFirstByStatus(LedgerStatus.ACTIVE);

        assertThat(result).isPresent();
        assertThat(result.get().getLedgerId()).isEqualTo(ledger.getLedgerId());
    }

    @Test
    void findFirstByStatusReturnsEmptyWhenAllClosed() {
        Ledger ledger = new Ledger(LocalDate.of(2026, 10, 1), BigDecimal.ZERO);
        ledger.setStatus(LedgerStatus.CLOSED);
        ledger.setEndDate(LocalDate.of(2026, 10, 10));
        ledger.setClosedAt(LocalDateTime.now());
        ledgerRepository.save(ledger);

        Optional<Ledger> result = ledgerRepository.findFirstByStatus(LedgerStatus.ACTIVE);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByStatusOrderByClosedAtDescReturnsClosedLedgersMostRecentFirst() {
        Ledger older = new Ledger(LocalDate.of(2026, 1, 1), BigDecimal.ZERO);
        older.setStatus(LedgerStatus.CLOSED);
        older.setEndDate(LocalDate.of(2026, 3, 31));
        older.setClosedAt(LocalDateTime.of(2026, 4, 1, 9, 0));
        ledgerRepository.save(older);

        Ledger newer = new Ledger(LocalDate.of(2026, 4, 1), BigDecimal.ZERO);
        newer.setStatus(LedgerStatus.CLOSED);
        newer.setEndDate(LocalDate.of(2026, 6, 30));
        newer.setClosedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        ledgerRepository.save(newer);

        List<Ledger> result = ledgerRepository.findAllByStatusOrderByClosedAtDesc(LedgerStatus.CLOSED);

        assertThat(result).extracting(Ledger::getLedgerId)
            .containsExactly(newer.getLedgerId(), older.getLedgerId());
    }
}