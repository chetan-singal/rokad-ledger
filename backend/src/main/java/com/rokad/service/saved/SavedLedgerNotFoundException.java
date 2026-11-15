package com.rokad.service.saved;

public class SavedLedgerNotFoundException extends RuntimeException {

  public SavedLedgerNotFoundException(Long ledgerId) {
    super("No saved ledger found with id " + ledgerId);
  }
}
