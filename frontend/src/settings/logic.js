import { lastBackupText } from '../backup/logic'
import { DATE_FORMATS, formatDateWith } from '../format'

const STORAGE_KEY = 'rokad.settings'
const SAMPLE_DATE = '2026-11-05'

export const DEFAULT_SETTINGS = { dateFormat: 'dMonY' }

export const CURRENCY_NOTE = 'Indian Rupee (₹) with two decimal places. This is fixed in this version.'
export const DISPLAY_NOTE =
  'These settings only change how things look. The amounts and dates stored in your ledgers never change.'

// Only known settings with known values survive, so a damaged or older entry can never break the
// app: anything else falls back to the default.
export function sanitizeSettings(value) {
  const dateFormat = DATE_FORMATS.includes(value?.dateFormat)
    ? value.dateFormat
    : DEFAULT_SETTINGS.dateFormat
  return { dateFormat }
}

export function loadSettings(storage) {
  try {
    const raw = storage?.getItem(STORAGE_KEY)
    return raw ? sanitizeSettings(JSON.parse(raw)) : { ...DEFAULT_SETTINGS }
  } catch {
    return { ...DEFAULT_SETTINGS }
  }
}

// true when the choice was kept for next time
export function saveSettings(storage, settings) {
  try {
    storage.setItem(STORAGE_KEY, JSON.stringify(sanitizeSettings(settings)))
    return true
  } catch {
    return false
  }
}

// Every option shows the same sample date, so the choice can be made by looking.
export function dateFormatOptions() {
  return DATE_FORMATS.map((id) => ({
    id,
    label:
      formatDateWith(id, SAMPLE_DATE) + (id === DEFAULT_SETTINGS.dateFormat ? ' (default)' : '')
  }))
}

const plural = (count, one, many) => `${count} ${count === 1 ? one : many}`

// Application and data storage information (SRS 11.1), as the backend reports it.
export function storageRows(info) {
  return [
    ['Application', `${info.applicationName}, version ${info.version}`],
    ['Data is stored in', `The MySQL database "${info.databaseName}" on this computer`],
    ['Connection', info.databaseUrl],
    [
      'Stored now',
      `${plural(info.ledgerCount, 'ledger', 'ledgers')} (${info.closedLedgerCount} closed) and ${plural(info.entryCount, 'entry', 'entries')}`
    ],
    ['Backups', `${lastBackupText(info)} ROKAD never makes a backup by itself.`]
  ]
}
