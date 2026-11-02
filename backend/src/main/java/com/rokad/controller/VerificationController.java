package com.rokad.controller;

import com.rokad.dto.CloseRequest;
import com.rokad.dto.ClosingWindowDTO;
import com.rokad.dto.LedgerSummaryDTO;
import com.rokad.dto.PeriodRequest;
import com.rokad.entity.Ledger;
import com.rokad.service.closure.VerificationClosureService;
import com.rokad.service.closure.VerificationSummary;
import com.rokad.service.lifecycle.LedgerLifecycleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
public class VerificationController {

  private final VerificationClosureService closureService;
  private final LedgerLifecycleService lifecycleService;

  public VerificationController(
      VerificationClosureService closureService, LedgerLifecycleService lifecycleService) {
    this.closureService = closureService;
    this.lifecycleService = lifecycleService;
  }

  @GetMapping("/active/closing-window")
  public ResponseEntity<ClosingWindowDTO> closingWindow() {
    return closureService
        .getClosingWindow()
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/verify")
  public VerificationSummary verify(@RequestBody PeriodRequest request) {
    return closureService.verifyPeriod(
        request.ledgerId(), request.periodStart(), request.periodEnd());
  }

  @PostMapping("/close")
  public LedgerSummaryDTO close(@RequestBody CloseRequest request) {
    Ledger nextLedger =
        closureService.closeLedger(
            request.ledgerId(), request.periodStart(), request.periodEnd(), request.verified());
    return lifecycleService.getLedgerSummary(nextLedger);
  }
}
