package com.rokad.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerSetupRequest(
    String ledgerName, LocalDate startDate, BigDecimal openingBalance) {}
