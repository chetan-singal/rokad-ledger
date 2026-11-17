import { describe, expect, it } from 'vitest'
import {
  backupFileName,
  backupSavedMessage,
  currentDataText,
  describeBackup,
  lastBackupText,
  replaceWarning,
  restoreErrorMessage,
  restoredText
} from './logic'

const summary = {
  createdAt: '2026-11-05T14:03:00',
  appVersion: '0.1.0',
  ledgerCount: 3,
  closedLedgerCount: 2,
  entryCount: 120,
  firstStartDate: '2026-04-01',
  activeStartDate: '2026-10-01',
  activeBalance: 61500,
  currentLedgerCount: 1,
  currentEntryCount: 1
}

describe('backup file name', () => {
  it('carries the local date and time to the minute', () => {
    expect(backupFileName(new Date(2026, 10, 5, 14, 3))).toBe('rokad-backup-2026-11-05-1403.json')
    expect(backupFileName(new Date(2026, 0, 2, 9, 7))).toBe('rokad-backup-2026-01-02-0907.json')
  })

  it('tells the person where the file went and how to keep it safe', () => {
    expect(backupSavedMessage('rokad-backup-2026-11-05-1403.json')).toMatch(
      /^Backup created: rokad-backup-2026-11-05-1403\.json\. Keep a copy on another drive/
    )
  })
})

describe('backup wording', () => {
  it('describes what a backup file holds', () => {
    expect(describeBackup(summary)).toEqual([
      'Made on 5 Nov 2026, 14:03 by ROKAD 0.1.0.',
      '3 ledgers (2 closed, 1 active) and 120 entries.',
      'Starts on 1 Apr 2026. The active ledger began on 1 Oct 2026 and its balance is ₹61,500.00.'
    ])
  })

  it('says what is in ROKAD now, in the singular where it applies', () => {
    expect(currentDataText(summary)).toBe('Now in ROKAD: 1 ledger and 1 entry.')
    expect(currentDataText({ ...summary, currentLedgerCount: 4, currentEntryCount: 0 })).toBe(
      'Now in ROKAD: 4 ledgers and 0 entries.'
    )
  })

  it('warns that a restore replaces everything and cannot be undone', () => {
    expect(replaceWarning()).toMatch(/replaces everything now in ROKAD/)
    expect(replaceWarning()).toMatch(/cannot be undone/)
  })

  it('reports the outcome of a restore', () => {
    expect(restoredText(summary)).toBe(
      "Restored. ROKAD now holds 3 ledgers and 120 entries. The active ledger's balance is ₹61,500.00."
    )
  })

  it('says when the last backup was made, or that there is none', () => {
    expect(lastBackupText({ lastBackupAt: '2026-11-05T14:03:00' })).toBe(
      'Last backup: 5 Nov 2026, 14:03'
    )
    expect(lastBackupText({ lastBackupAt: null })).toBe('No backup has been made yet.')
    expect(lastBackupText(null)).toBe('No backup has been made yet.')
  })

  it('shows a refused file as the backend worded it, and other failures with a lead-in', () => {
    const refused = { field: 'backup', message: 'This file is not a valid ROKAD backup: x.' }
    expect(restoreErrorMessage(refused)).toBe('This file is not a valid ROKAD backup: x.')
    expect(restoreErrorMessage({ status: 500, field: null, message: 'Boom.' })).toBe(
      'The backup was not restored. Boom.'
    )
  })

  it('admits it cannot be sure after a lost connection', () => {
    expect(restoreErrorMessage({ status: 0, message: 'Cannot reach the ROKAD backend.' })).toMatch(
      /not certain whether the backup was restored/
    )
  })
})
