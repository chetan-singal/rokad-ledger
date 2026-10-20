package com.rokad.dto;

import com.rokad.entity.Transaction;
import com.rokad.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionDTO(
    Long transactionId,
    Long ledgerId,
    LocalDate transactionDate,
    TransactionType transactionType,
    BigDecimal amount,
    String reason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static TransactionDTO from(Transaction t) {
    return new TransactionDTO(
        t.getTransactionId(),
        t.getLedger().getLedgerId(),
        t.getTransactionDate(),
        t.getTransactionType(),
        t.getAmount(),
        t.getReason(),
        t.getCreatedAt(),
        t.getUpdatedAt());
  }
}
