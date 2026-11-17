import { describe, expect, it } from 'vitest'
import {
  EMPTY_FILTERS,
  closedOnParts,
  closedOnText,
  entryCountText,
  filterNote,
  filterSavedLedgers,
  hasActiveFilters,
  isBackwardsRange,
  loadErrorMessage,
  periodParts,
  periodText,
  savedLedgerLabel,
  storedFigureRows
} from './logic'

const ledgers = [
  { ledgerId: 3, ledgerName: 'Diwali stock', startDate: '2026-10-01', endDate: '2026-10-31' },
  { ledgerId: 2, ledgerName: null, startDate: '2026-07-01', endDate: '2026-09-30' },
  { ledgerId: 1, ledgerName: 'April to June', startDate: '2026-04-01', endDate: '2026-06-30' }
]

const ids = (list) => list.map((ledger) => ledger.ledgerId)

describe('saved ledger labels', () => {
  it('uses the name and the number, or just the number', () => {
    expect(savedLedgerLabel(ledgers[0])).toBe('Diwali stock (#3)')
    expect(savedLedgerLabel(ledgers[1])).toBe('Ledger #2')
  })
})

describe('filterSavedLedgers', () => {
  it('shows everything, in the given order, when nothing is filtered', () => {
    expect(ids(filterSavedLedgers(ledgers, EMPTY_FILTERS))).toEqual([3, 2, 1])
    expect(hasActiveFilters(EMPTY_FILTERS)).toBe(false)
  })

  it('matches any part of the name in any case', () => {
    expect(ids(filterSavedLedgers(ledgers, { ...EMPTY_FILTERS, text: 'DIWALI' }))).toEqual([3])
    expect(ids(filterSavedLedgers(ledgers, { ...EMPTY_FILTERS, text: 'to jun' }))).toEqual([1])
  })

  it('matches a ledger number, with or without the hash, and only the whole number', () => {
    expect(ids(filterSavedLedgers(ledgers, { ...EMPTY_FILTERS, text: '2' }))).toEqual([2])
    expect(ids(filterSavedLedgers(ledgers, { ...EMPTY_FILTERS, text: '#1' }))).toEqual([1])
    expect(ids(filterSavedLedgers(ledgers, { ...EMPTY_FILTERS, text: '#12' }))).toEqual([])
  })

  it('matches a ledger whose period overlaps the dates, boundary days included', () => {
    expect(ids(filterSavedLedgers(ledgers, { ...EMPTY_FILTERS, from: '2026-09-30' }))).toEqual([3, 2])
    expect(ids(filterSavedLedgers(ledgers, { ...EMPTY_FILTERS, to: '2026-07-01' }))).toEqual([2, 1])
    expect(
      ids(filterSavedLedgers(ledgers, { ...EMPTY_FILTERS, from: '2026-08-01', to: '2026-08-31' }))
    ).toEqual([2])
  })

  it('combines text and dates', () => {
    expect(
      ids(filterSavedLedgers(ledgers, { text: 'diwali', from: '2026-07-01', to: '2026-07-31' }))
    ).toEqual([])
  })

  it('finds nothing for a range that runs backwards', () => {
    const backwards = { ...EMPTY_FILTERS, from: '2026-10-01', to: '2026-04-01' }
    expect(isBackwardsRange(backwards)).toBe(true)
    expect(filterSavedLedgers(ledgers, backwards)).toEqual([])
  })

  it('says when a filter is on, and how many ledgers it leaves', () => {
    expect(hasActiveFilters({ ...EMPTY_FILTERS, text: ' ' })).toBe(false)
    expect(hasActiveFilters({ ...EMPTY_FILTERS, from: '2026-04-01' })).toBe(true)
    expect(filterNote(1, 3)).toBe('Filtered: showing 1 of 3 saved ledgers.')
    expect(filterNote(1, 1)).toBe('Filtered: showing 1 of 1 saved ledger.')
  })
})

describe('saved ledger wording', () => {
  const summary = {
    openingBalance: 50000,
    totalDebit: 80000,
    totalCredit: 120000,
    closingBalance: 90000,
    transactionCount: 12,
    startDate: '2026-04-01',
    endDate: '2026-08-31',
    closedAt: '2026-10-01T18:30:00'
  }

  it('writes the period and the closing time', () => {
    expect(periodText(summary)).toBe('1 Apr 2026 to 31 Aug 2026')
    expect(closedOnText(summary)).toBe('1 Oct 2026, 18:30')
  })

  it('offers both as parts a narrow cell can break between', () => {
    expect(periodParts(summary)).toEqual(['1 Apr 2026', 'to 31 Aug 2026'])
    expect(closedOnParts(summary)).toEqual(['1 Oct 2026,', '18:30'])
  })

  it('counts entries in the singular and the plural', () => {
    expect(entryCountText(1)).toBe('1 entry')
    expect(entryCountText(0)).toBe('0 entries')
    expect(entryCountText(12)).toBe('12 entries')
  })

  it('lists the stored figures in the order of SRS 10.1 with Indian digit grouping', () => {
    expect(storedFigureRows(summary)).toEqual([
      ['Opening balance', '₹50,000.00'],
      ['Total debit', '₹80,000.00'],
      ['Total credit', '₹1,20,000.00'],
      ['Closing balance', '₹90,000.00'],
      ['Entries', '12']
    ])
  })

  it('explains a missing saved ledger and passes other errors through', () => {
    expect(loadErrorMessage({ status: 404, message: 'x' })).toMatch(/does not exist/)
    expect(loadErrorMessage({ status: 500, message: 'Boom' })).toBe('Boom')
  })
})
