package com.rokad.repository;

import com.rokad.entity.AuditAction;
import com.rokad.entity.LedgerAudit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<LedgerAudit, Long> {

  Optional<LedgerAudit> findFirstByActionOrderByAuditIdDesc(AuditAction action);
}
