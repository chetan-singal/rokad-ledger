package com.rokad.dto;

import com.rokad.entity.LedgerStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerSummaryDTO(
    Long ledgerId,
    String ledgerName,
    LocalDate startDate,
    LedgerStatus status,
    BigDecimal openingBalance,
    BigDecimal currentBalance,
    BigDecimal totalDebit,
    BigDecimal totalCredit,
    long transactionCount) {}
