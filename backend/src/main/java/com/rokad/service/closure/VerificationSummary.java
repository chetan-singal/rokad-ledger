package com.rokad.service.closure;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VerificationSummary(
    Long ledgerId,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal openingBalance,
    BigDecimal totalDebit,
    BigDecimal totalCredit,
    BigDecimal closingBalance,
    long debitCount,
    long creditCount) {}
