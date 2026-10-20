package com.rokad.controller;

import com.rokad.dto.LedgerSetupRequest;
import com.rokad.dto.LedgerSummaryDTO;
import com.rokad.dto.SavedLedgerDetailDTO;
import com.rokad.dto.TransactionDTO;
import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
import com.rokad.repository.LedgerRepository;
import com.rokad.repository.TransactionRepository;
import com.rokad.service.lifecycle.LedgerLifecycleService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LedgerController {

  private final LedgerLifecycleService lifecycleService;
  private final LedgerRepository ledgerRepository;
  private final TransactionRepository transactionRepository;

  public LedgerController(
      LedgerLifecycleService lifecycleService,
      LedgerRepository ledgerRepository,
      TransactionRepository transactionRepository) {
    this.lifecycleService = lifecycleService;
    this.ledgerRepository = ledgerRepository;
    this.transactionRepository = transactionRepository;
  }

  @PostMapping("/ledger/setup")
  public ResponseEntity<LedgerSummaryDTO> setup(@RequestBody LedgerSetupRequest request) {
    Ledger ledger = lifecycleService.createInitialLedger(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(lifecycleService.getLedgerSummary(ledger));
  }

  @GetMapping("/ledger/active")
  public ResponseEntity<LedgerSummaryDTO> getActive() {
    return lifecycleService
        .getActiveLedger()
        .map(lifecycleService::getLedgerSummary)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/ledgers")
  public List<LedgerSummaryDTO> listSaved() {
    return ledgerRepository.findAllByStatusOrderByClosedAtDesc(LedgerStatus.CLOSED).stream()
        .map(lifecycleService::getLedgerSummary)
        .toList();
  }

  @GetMapping("/ledgers/{id}")
  public SavedLedgerDetailDTO getSavedDetail(@PathVariable Long id) {
    Ledger ledger =
        ledgerRepository.findById(id).orElseThrow(() -> new LedgerNotFoundException(id));

    LedgerSummaryDTO summary = lifecycleService.getLedgerSummary(ledger);
    List<TransactionDTO> transactions =
        transactionRepository
            .findByLedger_LedgerIdOrderByTransactionDateAscTransactionIdAsc(id)
            .stream()
            .map(TransactionDTO::from)
            .toList();
    return new SavedLedgerDetailDTO(summary, transactions);
  }
}
