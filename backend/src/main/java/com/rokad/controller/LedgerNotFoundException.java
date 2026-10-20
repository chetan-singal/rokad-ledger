package com.rokad.controller;

public class LedgerNotFoundException extends RuntimeException {

  public LedgerNotFoundException(Long ledgerId) {
    super("No ledger found with id " + ledgerId);
  }
}
