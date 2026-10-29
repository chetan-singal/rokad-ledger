package com.rokad.controller;

import com.rokad.dto.LedgerOverviewDTO;
import com.rokad.service.overview.OverviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger/active")
public class OverviewController {

  private final OverviewService overviewService;

  public OverviewController(OverviewService overviewService) {
    this.overviewService = overviewService;
  }

  @GetMapping("/overview")
  public ResponseEntity<LedgerOverviewDTO> getOverview() {
    return overviewService
        .getActiveOverview()
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
