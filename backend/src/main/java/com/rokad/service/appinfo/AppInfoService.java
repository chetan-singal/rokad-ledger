package com.rokad.service.appinfo;

import com.rokad.dto.AppInfoDTO;
import com.rokad.entity.AuditAction;
import com.rokad.entity.LedgerAudit;
import com.rokad.entity.LedgerStatus;
import com.rokad.repository.AuditRepository;
import com.rokad.repository.LedgerRepository;
import com.rokad.repository.TransactionRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Application and data storage information for the Settings screen (SRS 11.1). Read only.
@Service
public class AppInfoService {

  private static final String APPLICATION_NAME = "ROKAD — Daily Ledger";

  private final LedgerRepository ledgerRepository;
  private final TransactionRepository transactionRepository;
  private final AuditRepository auditRepository;
  private final String version;
  private final String datasourceUrl;

  public AppInfoService(
      LedgerRepository ledgerRepository,
      TransactionRepository transactionRepository,
      AuditRepository auditRepository,
      @Value("${rokad.version:0.1.0}") String version,
      @Value("${spring.datasource.url}") String datasourceUrl) {
    this.ledgerRepository = ledgerRepository;
    this.transactionRepository = transactionRepository;
    this.auditRepository = auditRepository;
    this.version = version;
    this.datasourceUrl = datasourceUrl;
  }

  @Transactional(readOnly = true)
  public AppInfoDTO getInfo() {
    LocalDateTime lastBackupAt =
        auditRepository
            .findFirstByActionOrderByAuditIdDesc(AuditAction.BACKUP)
            .map(LedgerAudit::getPerformedAt)
            .orElse(null);
    return new AppInfoDTO(
        APPLICATION_NAME,
        version,
        databaseNameOf(datasourceUrl),
        datasourceUrl,
        ledgerRepository.count(),
        ledgerRepository.countByStatus(LedgerStatus.CLOSED),
        transactionRepository.count(),
        lastBackupAt);
  }

  // jdbc:mysql://localhost:3306/rokad_db?useSSL=false  ->  rokad_db
  static String databaseNameOf(String url) {
    if (url == null) return "unknown";
    String withoutParameters = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
    int slash = withoutParameters.lastIndexOf('/');
    String name = slash < 0 ? "" : withoutParameters.substring(slash + 1);
    return name.isBlank() ? "unknown" : name;
  }
}
