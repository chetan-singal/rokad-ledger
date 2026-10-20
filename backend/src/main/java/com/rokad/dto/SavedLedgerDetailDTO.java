package com.rokad.dto;

import java.util.List;

public record SavedLedgerDetailDTO(LedgerSummaryDTO summary, List<TransactionDTO> transactions) {}
