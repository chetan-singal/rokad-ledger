package com.rokad.dto;

import com.rokad.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequestDTO(
    LocalDate transactionDate, TransactionType transactionType, BigDecimal amount, String reason) {}
