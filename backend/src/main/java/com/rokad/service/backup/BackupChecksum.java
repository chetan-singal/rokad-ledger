package com.rokad.service.backup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// SHA-256 over a canonical text of the file's content. Amounts are written without trailing
// zeros, so 1500.5 and 1500.50 count as the same value however the file was read and written.
// The header's createdAt, appVersion and counts are not part of it: counts are checked on their
// own, and the other two are only labels.
public final class BackupChecksum {

  private static final String SEPARATOR = "\u001f";

  private BackupChecksum() {}

  public static String compute(BackupFile file) {
    StringBuilder text = new StringBuilder();
    text.append(file.format()).append(SEPARATOR).append(file.formatVersion()).append('\n');
    for (BackupFile.LedgerRow l : orEmpty(file.ledgers())) {
      text.append("L")
          .append(SEPARATOR)
          .append(
              line(
                  l.ledgerId(),
                  l.ledgerName(),
                  l.startDate(),
                  l.endDate(),
                  amount(l.openingBalance()),
                  amount(l.totalDebit()),
                  amount(l.totalCredit()),
                  amount(l.closingBalance()),
                  l.status(),
                  l.createdAt(),
                  l.closedAt(),
                  l.previousLedgerId(),
                  l.nextLedgerId()))
          .append('\n');
    }
    for (BackupFile.TransactionRow t : orEmpty(file.transactions())) {
      text.append("T")
          .append(SEPARATOR)
          .append(
              line(
                  t.transactionId(),
                  t.ledgerId(),
                  t.transactionDate(),
                  t.transactionType(),
                  amount(t.amount()),
                  t.reason(),
                  t.createdAt(),
                  t.updatedAt()))
          .append('\n');
    }
    for (BackupFile.AuditRow a : orEmpty(file.audit())) {
      text.append("A")
          .append(SEPARATOR)
          .append(
              line(
                  a.auditId(),
                  a.action(),
                  a.entityType(),
                  a.entityId(),
                  a.performedAt(),
                  a.details()))
          .append('\n');
    }
    return "sha256:" + sha256Hex(text.toString());
  }

  private static <T> List<T> orEmpty(List<T> list) {
    return list == null ? List.of() : list;
  }

  private static String amount(java.math.BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros().toPlainString();
  }

  private static String line(Object... values) {
    return java.util.Arrays.stream(values)
        .map(value -> Objects.toString(value, ""))
        .collect(Collectors.joining(SEPARATOR));
  }

  private static String sha256Hex(String text) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available in this Java runtime.", e);
    }
  }
}
