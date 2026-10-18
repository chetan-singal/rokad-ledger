package com.rokad.service.transaction;

public class TransactionNotFoundException extends RuntimeException {

  public TransactionNotFoundException(Long transactionId) {
    super("No transaction found with id " + transactionId);
  }
}
