package com.rokad.service.closure;

public class LedgerNotActiveException extends RuntimeException {

  public LedgerNotActiveException(Long ledgerId) {
    super("Ledger " + ledgerId + " is not the active ledger (it may already be closed).");
  }
}
