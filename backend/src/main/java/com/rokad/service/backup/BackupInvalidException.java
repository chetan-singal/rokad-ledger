package com.rokad.service.backup;

// The backup file cannot be used. The message is written for the person who chose the file.
public class BackupInvalidException extends RuntimeException {

  public BackupInvalidException(String problem) {
    super("This file is not a valid ROKAD backup: " + problem + ".");
  }
}
