package com.rokad.service.transaction;

public class ClosedLedgerModificationException extends RuntimeException {

  public ClosedLedgerModificationException(Long ledgerId) {
    super("Ledger " + ledgerId + " is closed and cannot be modified.");
  }
}
