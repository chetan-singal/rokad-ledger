package com.rokad.controller;

import com.rokad.dto.ErrorResponse;
import com.rokad.service.closure.LedgerNotActiveException;
import com.rokad.service.closure.LedgerVerificationException;
import com.rokad.service.transaction.ClosedLedgerModificationException;
import com.rokad.service.transaction.TransactionNotFoundException;
import com.rokad.service.transaction.TransactionValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(TransactionValidationException.class)
  public ResponseEntity<ErrorResponse> handle(TransactionValidationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of("VALIDATION_ERROR", ex.getField(), ex.getMessage()));
  }

  @ExceptionHandler(LedgerVerificationException.class)
  public ResponseEntity<ErrorResponse> handle(LedgerVerificationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of("VALIDATION_ERROR", ex.getField(), ex.getMessage()));
  }

  @ExceptionHandler(TransactionNotFoundException.class)
  public ResponseEntity<ErrorResponse> handle(TransactionNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of("NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler(LedgerNotFoundException.class)
  public ResponseEntity<ErrorResponse> handle(LedgerNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of("NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler(ClosedLedgerModificationException.class)
  public ResponseEntity<ErrorResponse> handle(ClosedLedgerModificationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of("CONFLICT", ex.getMessage()));
  }

  @ExceptionHandler(LedgerNotActiveException.class)
  public ResponseEntity<ErrorResponse> handle(LedgerNotActiveException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of("CONFLICT", ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handle(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of("VALIDATION_ERROR", ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handle(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of("CONFLICT", ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred. Please try again."));
  }
}
