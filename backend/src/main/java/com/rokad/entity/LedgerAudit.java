package com.rokad.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_audit")
public class LedgerAudit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "audit_id")
  private Long auditId;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false)
  private AuditAction action;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id")
  private Long entityId;

  @Column(name = "performed_at", insertable = false, updatable = false)
  private LocalDateTime performedAt;

  @Column(name = "details", length = 1000)
  private String details;

  protected LedgerAudit() {
    // required by JPA
  }

  public LedgerAudit(AuditAction action, String entityType, Long entityId, String details) {
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.details = details;
  }

  public Long getAuditId() {
    return auditId;
  }

  public AuditAction getAction() {
    return action;
  }

  public String getEntityType() {
    return entityType;
  }

  public Long getEntityId() {
    return entityId;
  }

  public LocalDateTime getPerformedAt() {
    return performedAt;
  }

  public String getDetails() {
    return details;
  }
}
