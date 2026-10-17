package com.rokad.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_transaction")
public class Transaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "transaction_id")
  private Long transactionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ledger_id", nullable = false)
  private Ledger ledger;

  @Column(name = "transaction_date", nullable = false)
  private LocalDate transactionDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false)
  private TransactionType transactionType;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(name = "reason", nullable = false, length = 500)
  private String reason;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;

  protected Transaction() {
    // required by JPA
  }

  public Transaction(
      Ledger ledger,
      LocalDate transactionDate,
      TransactionType transactionType,
      BigDecimal amount,
      String reason) {
    this.ledger = ledger;
    this.transactionDate = transactionDate;
    this.transactionType = transactionType;
    this.amount = amount;
    this.reason = reason;
  }

  public Long getTransactionId() {
    return transactionId;
  }

  public Ledger getLedger() {
    return ledger;
  }

  public void setLedger(Ledger ledger) {
    this.ledger = ledger;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  public void setTransactionDate(LocalDate transactionDate) {
    this.transactionDate = transactionDate;
  }

  public TransactionType getTransactionType() {
    return transactionType;
  }

  public void setTransactionType(TransactionType transactionType) {
    this.transactionType = transactionType;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
