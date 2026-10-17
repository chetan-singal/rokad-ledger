package com.rokad.service.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BalanceResult(
    BigDecimal openingBalance,
    BigDecimal totalDebit,
    BigDecimal totalCredit,
    BigDecimal closingBalance,
    List<RunningBalanceEntry> runningBalances
) {

    public record RunningBalanceEntry(
        Long transactionId,
        LocalDate transactionDate,
        BigDecimal balanceAfter
    ) {}
}