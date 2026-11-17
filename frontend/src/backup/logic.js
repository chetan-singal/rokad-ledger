import { formatDate, formatDateTime } from '../format'
import { formatCurrency } from '../money'

const plural = (count, one, many) => `${count} ${count === 1 ? one : many}`

// rokad-backup-2026-11-05-1403.json (local time, so the name matches the clock on the wall)
export function backupFileName(now = new Date()) {
  const pad = (n) => String(n).padStart(2, '0')
  const day = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
  return `rokad-backup-${day}-${pad(now.getHours())}${pad(now.getMinutes())}.json`
}

export function backupSavedMessage(fileName) {
  return `Backup created: ${fileName}. Keep a copy on another drive or a pen drive, so one failure cannot take both ROKAD and its backup.`
}

export function lastBackupText(info) {
  return info?.lastBackupAt
    ? `Last backup: ${formatDateTime(info.lastBackupAt)}`
    : 'No backup has been made yet.'
}

export function describeBackup(summary) {
  return [
    `Made on ${formatDateTime(summary.createdAt)} by ROKAD ${summary.appVersion}.`,
    `${plural(summary.ledgerCount, 'ledger', 'ledgers')} (${summary.closedLedgerCount} closed, 1 active) and ${plural(summary.entryCount, 'entry', 'entries')}.`,
    `Starts on ${formatDate(summary.firstStartDate)}. The active ledger began on ${formatDate(summary.activeStartDate)} and its balance is ${formatCurrency(summary.activeBalance)}.`
  ]
}

export function currentDataText(summary) {
  return `Now in ROKAD: ${plural(summary.currentLedgerCount, 'ledger', 'ledgers')} and ${plural(summary.currentEntryCount, 'entry', 'entries')}.`
}

export function replaceWarning() {
  return 'Restoring replaces everything now in ROKAD with the contents of this backup. Anything entered after the backup was made will be lost. This cannot be undone.'
}

export function restoredText(summary) {
  return `Restored. ROKAD now holds ${plural(summary.ledgerCount, 'ledger', 'ledgers')} and ${plural(summary.entryCount, 'entry', 'entries')}. The active ledger's balance is ${formatCurrency(summary.activeBalance)}.`
}

export function restoreErrorMessage(error) {
  if (error.status === 0) {
    return 'The connection was lost, so it is not certain whether the backup was restored. Check the Home screen before trying again.'
  }
  return error.field === 'backup' ? error.message : `The backup was not restored. ${error.message}`
}

export const FILE_UNREADABLE = 'This file could not be read from the disk.'
