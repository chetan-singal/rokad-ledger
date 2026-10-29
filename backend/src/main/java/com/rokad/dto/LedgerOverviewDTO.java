package com.rokad.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LedgerOverviewDTO(
    LedgerSummaryDTO summary,
    LocalDate today,
    BigDecimal todayDebit,
    BigDecimal todayCredit,
    List<TransactionDTO> recentTransactions) {}
