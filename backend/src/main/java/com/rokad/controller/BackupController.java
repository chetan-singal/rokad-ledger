package com.rokad.controller;

import com.rokad.dto.AppInfoDTO;
import com.rokad.dto.BackupSummaryDTO;
import com.rokad.dto.ErrorResponse;
import com.rokad.service.appinfo.AppInfoService;
import com.rokad.service.backup.BackupFile;
import com.rokad.service.backup.BackupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BackupController {

  private final BackupService backupService;
  private final AppInfoService appInfoService;

  public BackupController(BackupService backupService, AppInfoService appInfoService) {
    this.backupService = backupService;
    this.appInfoService = appInfoService;
  }

  @GetMapping("/backup/export")
  public BackupFile export() {
    return backupService.export();
  }

  @PostMapping("/backup/inspect")
  public BackupSummaryDTO inspect(@RequestBody BackupFile file) {
    return backupService.inspect(file);
  }

  @PostMapping("/backup/restore")
  public BackupSummaryDTO restore(@RequestBody BackupFile file) {
    return backupService.restore(file);
  }

  @GetMapping("/app/info")
  public AppInfoDTO info() {
    return appInfoService.getInfo();
  }

  // A file that is not JSON at all, or is cut short, never reaches the validator.
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            ErrorResponse.of(
                "VALIDATION_ERROR",
                "backup",
                "This file is not a valid ROKAD backup: it could not be read. It may be cut short"
                    + " or may not be a ROKAD backup at all."));
  }
}
