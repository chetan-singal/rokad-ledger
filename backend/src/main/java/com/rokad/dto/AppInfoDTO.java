package com.rokad.dto;

import java.time.LocalDateTime;

// Application and data storage information for Settings (SRS 11.1).
public record AppInfoDTO(
    String applicationName,
    String version,
    String databaseName,
    String databaseUrl,
    long ledgerCount,
    long closedLedgerCount,
    long entryCount,
    LocalDateTime lastBackupAt) {}
