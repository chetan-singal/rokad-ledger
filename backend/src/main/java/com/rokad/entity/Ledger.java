package com.rokad.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger")
public class Ledger {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ledger_id")
  private Long ledgerId;

  @Column(name = "ledger_name")
  private String ledgerName;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "opening_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal openingBalance;

  @Column(name = "total_debit", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalDebit;

  @Column(name = "total_credit", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalCredit;

  @Column(name = "closing_balance", precision = 15, scale = 2)
  private BigDecimal closingBalance;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private LedgerStatus status;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "closed_at")
  private LocalDateTime closedAt;

  @Column(name = "previous_ledger_id")
  private Long previousLedgerId;

  @Column(name = "next_ledger_id")
  private Long nextLedgerId;

  protected Ledger() {
    // required by JPA
  }

  public Ledger(LocalDate startDate, BigDecimal openingBalance) {
    this.startDate = startDate;
    this.openingBalance = openingBalance;
    this.totalDebit = BigDecimal.ZERO;
    this.totalCredit = BigDecimal.ZERO;
    this.status = LedgerStatus.ACTIVE;
  }

  public Long getLedgerId() {
    return ledgerId;
  }

  public String getLedgerName() {
    return ledgerName;
  }

  public void setLedgerName(String ledgerName) {
    this.ledgerName = ledgerName;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public BigDecimal getOpeningBalance() {
    return openingBalance;
  }

  public void setOpeningBalance(BigDecimal openingBalance) {
    this.openingBalance = openingBalance;
  }

  public BigDecimal getTotalDebit() {
    return totalDebit;
  }

  public void setTotalDebit(BigDecimal totalDebit) {
    this.totalDebit = totalDebit;
  }

  public BigDecimal getTotalCredit() {
    return totalCredit;
  }

  public void setTotalCredit(BigDecimal totalCredit) {
    this.totalCredit = totalCredit;
  }

  public BigDecimal getClosingBalance() {
    return closingBalance;
  }

  public void setClosingBalance(BigDecimal closingBalance) {
    this.closingBalance = closingBalance;
  }

  public LedgerStatus getStatus() {
    return status;
  }

  public void setStatus(LedgerStatus status) {
    this.status = status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(LocalDateTime closedAt) {
    this.closedAt = closedAt;
  }

  public Long getPreviousLedgerId() {
    return previousLedgerId;
  }

  public void setPreviousLedgerId(Long previousLedgerId) {
    this.previousLedgerId = previousLedgerId;
  }

  public Long getNextLedgerId() {
    return nextLedgerId;
  }

  public void setNextLedgerId(Long nextLedgerId) {
    this.nextLedgerId = nextLedgerId;
  }
}
