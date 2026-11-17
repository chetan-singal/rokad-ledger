import { describe, expect, it } from 'vitest'
import {
  DEFAULT_SETTINGS,
  dateFormatOptions,
  loadSettings,
  sanitizeSettings,
  saveSettings,
  storageRows
} from './logic'

function fakeStorage(initial = {}) {
  const data = { ...initial }
  return {
    data,
    getItem: (key) => (key in data ? data[key] : null),
    setItem: (key, value) => {
      data[key] = value
    }
  }
}

describe('settings storage', () => {
  it('starts from the defaults when nothing was saved', () => {
    expect(loadSettings(fakeStorage())).toEqual(DEFAULT_SETTINGS)
  })

  it('reads back what was saved', () => {
    const storage = fakeStorage()
    expect(saveSettings(storage, { dateFormat: 'ymd' })).toBe(true)
    expect(loadSettings(storage)).toEqual({ dateFormat: 'ymd' })
  })

  it('ignores damaged text and unknown values', () => {
    expect(loadSettings(fakeStorage({ 'rokad.settings': '{not json' }))).toEqual(DEFAULT_SETTINGS)
    expect(sanitizeSettings({ dateFormat: 'weird', other: 1 })).toEqual(DEFAULT_SETTINGS)
    expect(sanitizeSettings(null)).toEqual(DEFAULT_SETTINGS)
  })

  it('works without any storage at all', () => {
    expect(loadSettings(null)).toEqual(DEFAULT_SETTINGS)
    expect(saveSettings(null, DEFAULT_SETTINGS)).toBe(false)
  })

  it('says so when the choice could not be kept', () => {
    const full = {
      getItem: () => null,
      setItem: () => {
        throw new Error('quota')
      }
    }
    expect(saveSettings(full, { dateFormat: 'dmy' })).toBe(false)
  })

  it('offers every format with the same sample date and marks the default', () => {
    expect(dateFormatOptions()).toEqual([
      { id: 'dMonY', label: '5 Nov 2026 (default)' },
      { id: 'dmy', label: '05/11/2026' },
      { id: 'ymd', label: '2026-11-05' }
    ])
  })
})

describe('storage information', () => {
  const info = {
    applicationName: 'ROKAD — Daily Ledger',
    version: '0.1.0',
    databaseName: 'rokad_db',
    databaseUrl: 'jdbc:mysql://localhost:3306/rokad_db',
    ledgerCount: 3,
    closedLedgerCount: 2,
    entryCount: 1,
    lastBackupAt: '2026-11-05T14:03:00'
  }

  it('lists where the data is, how much there is and when it was last backed up', () => {
    expect(storageRows(info)).toEqual([
      ['Application', 'ROKAD — Daily Ledger, version 0.1.0'],
      ['Data is stored in', 'The MySQL database "rokad_db" on this computer'],
      ['Connection', 'jdbc:mysql://localhost:3306/rokad_db'],
      ['Stored now', '3 ledgers (2 closed) and 1 entry'],
      ['Backups', 'Last backup: 5 Nov 2026, 14:03 ROKAD never makes a backup by itself.']
    ])
  })

  it('says so when no backup has been made', () => {
    expect(storageRows({ ...info, lastBackupAt: null })[4][1]).toMatch(/^No backup has been made yet\./)
  })
})
