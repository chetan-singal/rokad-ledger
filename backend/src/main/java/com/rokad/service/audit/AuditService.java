package com.rokad.service.audit;

import com.rokad.entity.AuditAction;
import com.rokad.entity.LedgerAudit;
import com.rokad.repository.AuditRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private final AuditRepository auditRepository;

  public AuditService(AuditRepository auditRepository) {
    this.auditRepository = auditRepository;
  }

  public void log(AuditAction action, String entityType, Long entityId, String details) {
    auditRepository.save(new LedgerAudit(action, entityType, entityId, details));
  }
}
