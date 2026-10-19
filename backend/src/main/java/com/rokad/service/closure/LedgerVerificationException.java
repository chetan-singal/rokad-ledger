package com.rokad.service.closure;

public class LedgerVerificationException extends RuntimeException {

  private final String field;

  public LedgerVerificationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
