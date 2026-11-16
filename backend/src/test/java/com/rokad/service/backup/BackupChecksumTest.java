package com.rokad.service.backup;

import static com.rokad.service.backup.BackupTestData.AT;
import static com.rokad.service.backup.BackupTestData.money;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackupChecksumTest {

  // TC-072: the same content always gives the same checksum
  @Test
  void theSameContentGivesTheSameChecksum() {
    assertThat(BackupChecksum.compute(BackupTestData.valid()))
        .isEqualTo(BackupChecksum.compute(BackupTestData.valid()));
    assertThat(BackupChecksum.compute(BackupTestData.valid())).startsWith("sha256:");
  }

  // TC-072: 1500.5 and 1500.50 are the same amount, whichever way the file was written
  @Test
  void amountsAreComparedByValueNotByScale() {
    List<BackupFile.TransactionRow> a = new ArrayList<>(BackupTestData.entries());
    List<BackupFile.TransactionRow> b = new ArrayList<>(BackupTestData.entries());
    BackupFile.TransactionRow first = a.get(0);
    b.set(
        0,
        new BackupFile.TransactionRow(
            first.transactionId(),
            first.ledgerId(),
            first.transactionDate(),
            first.transactionType(),
            money("500"),
            first.reason(),
            first.createdAt(),
            first.updatedAt()));

    BackupFile one =
        BackupTestData.seal(
            List.of(BackupTestData.closedLedger(), BackupTestData.activeLedger()),
            a,
            BackupTestData.audit());
    BackupFile two =
        BackupTestData.seal(
            List.of(BackupTestData.closedLedger(), BackupTestData.activeLedger()),
            b,
            BackupTestData.audit());

    assertThat(one.checksum()).isEqualTo(two.checksum());
  }

  // TC-072: any change to an entry changes the checksum
  @Test
  void changingAnyEntryChangesTheChecksum() {
    List<BackupFile.TransactionRow> edited = new ArrayList<>(BackupTestData.entries());
    BackupFile.TransactionRow first = edited.get(0);
    edited.set(
        0,
        new BackupFile.TransactionRow(
            first.transactionId(),
            first.ledgerId(),
            first.transactionDate(),
            first.transactionType(),
            money("500.01"),
            first.reason(),
            first.createdAt(),
            first.updatedAt()));

    BackupFile changed =
        BackupTestData.seal(
            List.of(BackupTestData.closedLedger(), BackupTestData.activeLedger()),
            edited,
            BackupTestData.audit());

    assertThat(changed.checksum()).isNotEqualTo(BackupTestData.valid().checksum());
  }

  // TC-072: the label fields do not take part, so re-labelling a file does not invalidate it
  @Test
  void theHeaderLabelsAreNotPartOfTheChecksum() {
    BackupFile valid = BackupTestData.valid();
    BackupFile relabelled =
        new BackupFile(
            valid.format(),
            valid.formatVersion(),
            "9.9.9",
            AT.plusDays(3),
            valid.counts(),
            valid.ledgers(),
            valid.transactions(),
            valid.audit(),
            valid.checksum());

    assertThat(BackupChecksum.compute(relabelled)).isEqualTo(valid.checksum());
  }
}
