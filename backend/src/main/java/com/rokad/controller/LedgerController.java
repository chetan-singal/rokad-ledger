package com.rokad.controller;

import com.rokad.dto.LedgerSetupRequest;
import com.rokad.dto.LedgerSummaryDTO;
import com.rokad.dto.SavedLedgerDetailDTO;
import com.rokad.entity.Ledger;
import com.rokad.service.lifecycle.LedgerLifecycleService;
import com.rokad.service.saved.SavedLedgerService;
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
  private final SavedLedgerService savedLedgerService;

  public LedgerController(
      LedgerLifecycleService lifecycleService, SavedLedgerService savedLedgerService) {
    this.lifecycleService = lifecycleService;
    this.savedLedgerService = savedLedgerService;
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
    return savedLedgerService.listSaved();
  }

  @GetMapping("/ledgers/{id}")
  public SavedLedgerDetailDTO getSavedDetail(@PathVariable Long id) {
    return savedLedgerService.getDetail(id);
  }
}
