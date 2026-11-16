package com.rokad.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// What a backup file holds, for the person about to restore it, next to what ROKAD holds now.
// After a restore it describes the data that is now in place.
public record BackupSummaryDTO(
    LocalDateTime createdAt,
    String appVersion,
    int ledgerCount,
    int closedLedgerCount,
    int entryCount,
    LocalDate firstStartDate,
    LocalDate activeStartDate,
    BigDecimal activeBalance,
    long currentLedgerCount,
    long currentEntryCount) {}
