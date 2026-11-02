package com.rokad.service.closure;

public class StaleVerificationException extends RuntimeException {

  public StaleVerificationException() {
    super("The ledger changed after you verified it. Verify the period again before closing.");
  }
}
