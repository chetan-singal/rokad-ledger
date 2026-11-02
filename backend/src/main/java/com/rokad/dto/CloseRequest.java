package com.rokad.dto;

import com.rokad.service.closure.VerificationSummary;
import java.time.LocalDate;

// verified is the summary the user reviewed and confirmed. Closing is refused if the ledger
// no longer produces exactly those figures (rule R5).
public record CloseRequest(
    Long ledgerId, LocalDate periodStart, LocalDate periodEnd, VerificationSummary verified) {}
