package com.rokad.repository;

import com.rokad.entity.LedgerAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<LedgerAudit, Long> {}
