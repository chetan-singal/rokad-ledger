package com.rokad.service.saved;

import com.rokad.dto.LedgerSummaryDTO;
import com.rokad.dto.SavedLedgerDetailDTO;
import com.rokad.dto.TransactionDTO;
import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
import com.rokad.repository.LedgerRepository;
import com.rokad.repository.TransactionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The archive of closed ledgers (SRS 10). It reports what was stored when each ledger was
// closed and never recalculates it from the entries (SRS 10.3). Nothing here writes.
@Service
public class SavedLedgerService {

  private final LedgerRepository ledgerRepository;
  private final TransactionRepository transactionRepository;

  public SavedLedgerService(
      LedgerRepository ledgerRepository, TransactionRepository transactionRepository) {
    this.ledgerRepository = ledgerRepository;
    this.transactionRepository = transactionRepository;
  }

  // Most recently closed first. Entry counts come from one grouped query, so the cost does not
  // grow with the number of entries.
  @Transactional(readOnly = true)
  public List<LedgerSummaryDTO> listSaved() {
    Map<Long, Long> entryCounts = new HashMap<>();
    for (Object[] row : transactionRepository.countEntriesPerLedger()) {
      entryCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }
    return ledgerRepository
        .findAllByStatusOrderByClosedAtDescLedgerIdDesc(LedgerStatus.CLOSED)
        .stream()
        .map(
            ledger ->
                LedgerSummaryDTO.closed(ledger, entryCounts.getOrDefault(ledger.getLedgerId(), 0L)))
        .toList();
  }

  // Only a closed ledger is an archive entry; the active ledger is not found here.
  @Transactional(readOnly = true)
  public SavedLedgerDetailDTO getDetail(Long ledgerId) {
    Ledger ledger =
        ledgerRepository
            .findById(ledgerId)
            .filter(found -> found.getStatus() == LedgerStatus.CLOSED)
            .orElseThrow(() -> new SavedLedgerNotFoundException(ledgerId));
    List<TransactionDTO> transactions =
        transactionRepository
            .findByLedger_LedgerIdOrderByTransactionDateAscTransactionIdAsc(ledgerId)
            .stream()
            .map(TransactionDTO::from)
            .toList();
    return new SavedLedgerDetailDTO(
        LedgerSummaryDTO.closed(ledger, transactions.size()), transactions);
  }
}
