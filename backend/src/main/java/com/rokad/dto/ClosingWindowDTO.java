package com.rokad.dto;

import java.time.LocalDate;

// The end dates the active ledger can be closed on (FR-008, rule R2). lastEntryDate is null
// while the ledger has no entries; closable is false only when the ledger starts after today.
public record ClosingWindowDTO(
    Long ledgerId,
    LocalDate startDate,
    LocalDate lastEntryDate,
    LocalDate earliestEndDate,
    LocalDate latestEndDate,
    boolean closable) {}
