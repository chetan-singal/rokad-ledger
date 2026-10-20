package com.rokad.dto;

import java.time.LocalDate;

public record PeriodRequest(Long ledgerId, LocalDate periodStart, LocalDate periodEnd) {}
