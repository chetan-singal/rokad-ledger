package com.rokad.controller;

import com.rokad.dto.TransactionDTO;
import com.rokad.dto.TransactionRequestDTO;
import com.rokad.entity.Ledger;
import com.rokad.entity.Transaction;
import com.rokad.repository.TransactionRepository;
import com.rokad.service.lifecycle.LedgerLifecycleService;
import com.rokad.service.transaction.TransactionManagementService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

  private final TransactionManagementService transactionService;
  private final LedgerLifecycleService lifecycleService;
  private final TransactionRepository transactionRepository;

  public TransactionController(
      TransactionManagementService transactionService,
      LedgerLifecycleService lifecycleService,
      TransactionRepository transactionRepository) {
    this.transactionService = transactionService;
    this.lifecycleService = lifecycleService;
    this.transactionRepository = transactionRepository;
  }

  @PostMapping
  public ResponseEntity<TransactionDTO> add(@RequestBody TransactionRequestDTO request) {
    Transaction saved = transactionService.addTransaction(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(TransactionDTO.from(saved));
  }

  @PutMapping("/{id}")
  public TransactionDTO edit(@PathVariable Long id, @RequestBody TransactionRequestDTO request) {
    return TransactionDTO.from(transactionService.editTransaction(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    transactionService.deleteTransaction(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping
  public List<TransactionDTO> list(
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate) {
    Ledger active = lifecycleService.getActiveLedgerOrThrow();
    List<Transaction> transactions =
        (startDate != null && endDate != null)
            ? transactionRepository.findByLedgerAndDateRange(
                active.getLedgerId(), startDate, endDate)
            : transactionRepository.findByLedger_LedgerIdOrderByTransactionDateAscTransactionIdAsc(
                active.getLedgerId());
    return transactions.stream().map(TransactionDTO::from).toList();
  }
}
