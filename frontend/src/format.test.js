import { describe, expect, it } from 'vitest'
import {
  formatDate,
  formatDateTime,
  formatDateWith,
  getDateFormat,
  setDateFormat
} from './format'

// The format is a setting kept in one place, so every test puts it back as it found it.
function withFormat(format, check) {
  const before = getDateFormat()
  setDateFormat(format)
  try {
    check()
  } finally {
    setDateFormat(before)
  }
}

describe('date formats', () => {
  it('writes the default as day, month name and year', () => {
    withFormat('dMonY', () => {
      expect(formatDate('2026-04-01')).toBe('1 Apr 2026')
      expect(formatDate('2026-10-31')).toBe('31 Oct 2026')
    })
  })

  it('writes day/month/year with two-digit day and month', () => {
    withFormat('dmy', () => {
      expect(formatDate('2026-04-01')).toBe('01/04/2026')
      expect(formatDate('2026-11-25')).toBe('25/11/2026')
    })
  })

  it('writes the year first when asked', () => {
    withFormat('ymd', () => {
      expect(formatDate('2026-04-01')).toBe('2026-04-01')
    })
  })

  it('falls back to the default for a format it does not know', () => {
    withFormat('nonsense', () => {
      expect(getDateFormat()).toBe('dMonY')
      expect(formatDate('2026-04-01')).toBe('1 Apr 2026')
    })
  })

  it('formats with an explicit format without touching the setting', () => {
    expect(formatDateWith('dmy', '2026-12-05')).toBe('05/12/2026')
    expect(getDateFormat()).toBe('dMonY')
  })

  it('adds the time of day after the date in the chosen format', () => {
    expect(formatDateTime('2026-11-01T09:05:30')).toBe('1 Nov 2026, 09:05')
    withFormat('dmy', () => {
      expect(formatDateTime('2026-11-01T18:30:00')).toBe('01/11/2026, 18:30')
    })
  })

  it('writes nothing for an empty date', () => {
    expect(formatDate('')).toBe('')
    expect(formatDate(null)).toBe('')
    expect(formatDateTime(undefined)).toBe('')
  })
})
