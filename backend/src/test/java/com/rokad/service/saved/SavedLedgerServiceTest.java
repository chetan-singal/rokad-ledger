package com.rokad.service.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

import com.rokad.dto.LedgerSummaryDTO;
import com.rokad.dto.SavedLedgerDetailDTO;
import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
import com.rokad.entity.Transaction;
import com.rokad.entity.TransactionType;
import com.rokad.repository.LedgerRepository;
import com.rokad.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("test")
@Import(SavedLedgerService.class)
class SavedLedgerServiceTest {

  @Autowired private SavedLedgerService savedLedgerService;
  @Autowired private LedgerRepository ledgerRepository;
  @Autowired private TransactionRepository transactionRepository;

  private Ledger closedLedger(
      String start,
      String end,
      String opening,
      String closing,
      String debit,
      String credit,
      LocalDateTime closedAt) {
    Ledger ledger = new Ledger(LocalDate.parse(start), new BigDecimal(opening));
    ledger.setStatus(LedgerStatus.CLOSED);
    ledger.setEndDate(LocalDate.parse(end));
    ledger.setClosingBalance(new BigDecimal(closing));
    ledger.setTotalDebit(new BigDecimal(debit));
    ledger.setTotalCredit(new BigDecimal(credit));
    ledger.setClosedAt(closedAt);
    return ledgerRepository.save(ledger);
  }

  private void entry(Ledger ledger, String date, TransactionType type, String amount) {
    transactionRepository.save(
        new Transaction(ledger, LocalDate.parse(date), type, new BigDecimal(amount), "Entry"));
  }

  // TC-065: the archive lists closed ledgers only, most recently closed first
  @Test
  void listsClosedLedgersMostRecentlyClosedFirstAndNeverTheActiveOne() {
    Ledger older =
        closedLedger(
            "2026-01-01",
            "2026-03-31",
            "0.00",
            "0.00",
            "0.00",
            "0.00",
            LocalDateTime.of(2026, 4, 1, 9, 0));
    Ledger newer =
        closedLedger(
            "2026-04-01",
            "2026-06-30",
            "0.00",
            "0.00",
            "0.00",
            "0.00",
            LocalDateTime.of(2026, 7, 1, 9, 0));
    ledgerRepository.save(new Ledger(LocalDate.of(2026, 7, 1), BigDecimal.ZERO));

    List<LedgerSummaryDTO> list = savedLedgerService.listSaved();

    assertThat(list.stream().map(LedgerSummaryDTO::ledgerId).toList())
        .containsExactly(newer.getLedgerId(), older.getLedgerId());
  }

  // TC-066: every row carries the stored figures, the end date, the closing time and its count
  @Test
  void eachRowShowsTheStoredFiguresAndItsOwnEntryCount() {
    LocalDateTime closedAt = LocalDateTime.of(2026, 10, 1, 18, 30);
    Ledger withEntries =
        closedLedger(
            "2026-04-01", "2026-09-30", "50000.00", "90000.00", "80000.00", "120000.00", closedAt);
    entry(withEntries, "2026-04-02", TransactionType.CREDIT, "120000.00");
    entry(withEntries, "2026-04-03", TransactionType.DEBIT, "80000.00");
    entry(withEntries, "2026-04-04", TransactionType.DEBIT, "0.01");
    Ledger empty =
        closedLedger(
            "2026-03-01", "2026-03-31", "10.00", "10.00", "0.00", "0.00", closedAt.minusDays(200));

    List<LedgerSummaryDTO> list = savedLedgerService.listSaved();

    LedgerSummaryDTO first = list.get(0);
    assertThat(first.ledgerId()).isEqualTo(withEntries.getLedgerId());
    assertThat(first.startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(first.endDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(first.openingBalance()).isEqualByComparingTo("50000.00");
    assertThat(first.totalCredit()).isEqualByComparingTo("120000.00");
    assertThat(first.totalDebit()).isEqualByComparingTo("80000.00");
    assertThat(first.closingBalance()).isEqualByComparingTo("90000.00");
    assertThat(first.closedAt()).isEqualTo(closedAt);
    assertThat(first.status()).isEqualTo(LedgerStatus.CLOSED);
    assertThat(first.transactionCount()).isEqualTo(3);
    assertThat(list.get(1).ledgerId()).isEqualTo(empty.getLedgerId());
    assertThat(list.get(1).transactionCount()).isEqualTo(0);
  }

  // TC-066: two closures in the same second keep a stable order
  @Test
  void closuresInTheSameSecondAreOrderedByIdNewestFirst() {
    LocalDateTime same = LocalDateTime.of(2026, 5, 1, 12, 0);
    Ledger first = closedLedger("2026-01-01", "2026-01-31", "0.00", "0.00", "0.00", "0.00", same);
    Ledger second = closedLedger("2026-02-01", "2026-02-28", "0.00", "0.00", "0.00", "0.00", same);

    List<LedgerSummaryDTO> list = savedLedgerService.listSaved();

    assertThat(list.stream().map(LedgerSummaryDTO::ledgerId).toList())
        .containsExactly(second.getLedgerId(), first.getLedgerId());
  }

  // TC-042: the detail shows the stored totals, not a recalculation from the entries
  @Test
  void theDetailReportsTheStoredTotalsAndListsTheEntries() {
    Ledger ledger =
        closedLedger(
            "2026-04-01",
            "2026-04-30",
            "100.00",
            "900.00",
            "999.00",
            "1799.00",
            LocalDateTime.of(2026, 5, 1, 10, 0));
    entry(ledger, "2026-04-02", TransactionType.DEBIT, "1.00");

    SavedLedgerDetailDTO detail = savedLedgerService.getDetail(ledger.getLedgerId());

    assertThat(detail.summary().totalDebit()).isEqualByComparingTo("999.00");
    assertThat(detail.summary().totalCredit()).isEqualByComparingTo("1799.00");
    assertThat(detail.summary().closingBalance()).isEqualByComparingTo("900.00");
    assertThat(detail.transactions()).hasSize(1);
  }

  // TC-068: only a closed ledger is an archive entry
  @Test
  void theActiveLedgerAndUnknownIdsAreNotFound() {
    Ledger active = ledgerRepository.save(new Ledger(LocalDate.of(2026, 7, 1), BigDecimal.ZERO));

    assertThatThrownBy(() -> savedLedgerService.getDetail(active.getLedgerId()))
        .isInstanceOf(SavedLedgerNotFoundException.class);
    assertThatThrownBy(() -> savedLedgerService.getDetail(987654L))
        .isInstanceOf(SavedLedgerNotFoundException.class)
        .hasMessageContaining("987654");
  }

  // TC-043: looking at the archive changes nothing
  @Test
  void viewingTheArchiveLeavesTheActiveLedgerUntouched() {
    Ledger closed =
        closedLedger(
            "2026-01-01",
            "2026-03-31",
            "0.00",
            "0.00",
            "0.00",
            "0.00",
            LocalDateTime.of(2026, 4, 1, 9, 0));
    Ledger active =
        ledgerRepository.save(new Ledger(LocalDate.of(2026, 4, 1), new BigDecimal("500.00")));

    savedLedgerService.listSaved();
    savedLedgerService.getDetail(closed.getLedgerId());

    Ledger reloaded = ledgerRepository.findById(active.getLedgerId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(LedgerStatus.ACTIVE);
    assertThat(reloaded.getOpeningBalance()).isEqualByComparingTo("500.00");
    assertThat(reloaded.getEndDate()).isNull();
    assertThat(ledgerRepository.count()).isEqualTo(2);
  }
}
