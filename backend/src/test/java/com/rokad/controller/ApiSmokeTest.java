package com.rokad.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.rokad.dto.LedgerSetupRequest;
import com.rokad.dto.LedgerSummaryDTO;
import com.rokad.dto.PeriodRequest;
import com.rokad.dto.SavedLedgerDetailDTO;
import com.rokad.dto.TransactionDTO;
import com.rokad.dto.TransactionRequestDTO;
import com.rokad.entity.Ledger;
import com.rokad.entity.TransactionType;
import com.rokad.repository.LedgerRepository;
import com.rokad.repository.TransactionRepository;
import com.rokad.service.closure.VerificationSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiSmokeTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private LedgerRepository ledgerRepository;
  @Autowired private TransactionRepository transactionRepository;

  @BeforeEach
  @AfterEach
  void cleanSlate() {
    transactionRepository.deleteAll();
    List<Ledger> ledgers = ledgerRepository.findAll();
    ledgers.forEach(
        l -> {
          l.setPreviousLedgerId(null);
          l.setNextLedgerId(null);
        });
    ledgerRepository.saveAll(ledgers);
    ledgerRepository.deleteAll();
  }

  @Test
  void everyEndpointRespondsAcrossAFullLedgerLifecycle() {
    ResponseEntity<LedgerSummaryDTO> beforeSetup =
        rest.getForEntity("/api/ledger/active", LedgerSummaryDTO.class);
    assertThat(beforeSetup.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    LedgerSetupRequest setupRequest =
        new LedgerSetupRequest(
            "Smoke Test Ledger", LocalDate.of(2026, 4, 1), new BigDecimal("1000.00"));
    ResponseEntity<LedgerSummaryDTO> setup =
        rest.postForEntity("/api/ledger/setup", setupRequest, LedgerSummaryDTO.class);
    assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long ledgerId = setup.getBody().ledgerId();

    ResponseEntity<LedgerSummaryDTO> active =
        rest.getForEntity("/api/ledger/active", LedgerSummaryDTO.class);
    assertThat(active.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(active.getBody().ledgerId()).isEqualTo(ledgerId);

    TransactionRequestDTO addRequest =
        new TransactionRequestDTO(
            LocalDate.of(2026, 4, 2),
            TransactionType.CREDIT,
            new BigDecimal("500.00"),
            "Smoke credit");
    ResponseEntity<TransactionDTO> added =
        rest.postForEntity("/api/transactions", addRequest, TransactionDTO.class);
    assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long transactionId = added.getBody().transactionId();

    ResponseEntity<TransactionDTO[]> listed =
        rest.getForEntity("/api/transactions", TransactionDTO[].class);
    assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listed.getBody()).hasSize(1);

    TransactionRequestDTO editRequest =
        new TransactionRequestDTO(
            LocalDate.of(2026, 4, 2),
            TransactionType.CREDIT,
            new BigDecimal("600.00"),
            "Smoke credit, corrected");
    rest.put("/api/transactions/" + transactionId, editRequest);
    ResponseEntity<TransactionDTO[]> afterEdit =
        rest.getForEntity("/api/transactions", TransactionDTO[].class);
    assertThat(afterEdit.getBody()[0].amount()).isEqualByComparingTo("600.00");

    PeriodRequest periodRequest =
        new PeriodRequest(ledgerId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
    ResponseEntity<VerificationSummary> verified =
        rest.postForEntity("/api/ledger/verify", periodRequest, VerificationSummary.class);
    assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(verified.getBody().closingBalance()).isEqualByComparingTo("1600.00");

    TransactionRequestDTO toDelete =
        new TransactionRequestDTO(
            LocalDate.of(2026, 4, 3),
            TransactionType.DEBIT,
            new BigDecimal("100.00"),
            "Will be deleted");
    ResponseEntity<TransactionDTO> addedToDelete =
        rest.postForEntity("/api/transactions", toDelete, TransactionDTO.class);
    rest.delete("/api/transactions/" + addedToDelete.getBody().transactionId());

    ResponseEntity<LedgerSummaryDTO> closed =
        rest.postForEntity("/api/ledger/close", periodRequest, LedgerSummaryDTO.class);
    assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long nextLedgerId = closed.getBody().ledgerId();
    assertThat(nextLedgerId).isNotEqualTo(ledgerId);
    assertThat(closed.getBody().openingBalance()).isEqualByComparingTo("1600.00");

    ResponseEntity<LedgerSummaryDTO[]> saved =
        rest.getForEntity("/api/ledgers", LedgerSummaryDTO[].class);
    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(saved.getBody()).hasSize(1);
    assertThat(saved.getBody()[0].ledgerId()).isEqualTo(ledgerId);

    ResponseEntity<SavedLedgerDetailDTO> detail =
        rest.getForEntity("/api/ledgers/" + ledgerId, SavedLedgerDetailDTO.class);
    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(detail.getBody().transactions()).hasSize(1);
  }
}
