import { describe, expect, it } from 'vitest'
import {
  checkEndDate,
  closeErrorMessage,
  defaultEndDate,
  endDateHint,
  entriesInPeriod,
  figureRows,
  hasNoEntries,
  hasNotStarted,
  newLedgerNote,
  nextLedgerNote,
  notStartedMessage,
  periodLabel,
  periodRequest,
  shouldRefreshAfter
} from './logic'

const closingWindow = {
  ledgerId: 3,
  startDate: '2026-10-01',
  lastEntryDate: '2026-10-27',
  earliestEndDate: '2026-10-27',
  latestEndDate: '2026-10-31',
  closable: true
}

const summary = {
  ledgerId: 3,
  periodStart: '2026-10-01',
  periodEnd: '2026-10-31',
  openingBalance: 50000,
  totalDebit: 120000,
  totalCredit: 150000,
  closingBalance: 80000,
  debitCount: 4,
  creditCount: 6,
  nextLedgerStartDate: '2026-11-01'
}

describe('closing window wording', () => {
  it('starts the end date at the earliest allowed day', () => {
    expect(defaultEndDate(closingWindow)).toBe('2026-10-27')
  })

  it('names the latest entry as the reason for the earliest end date', () => {
    const hint = endDateHint(closingWindow)
    expect(hint).toMatch(/Earliest 27 Oct 2026 \(your latest entry\)/)
    expect(hint).toMatch(/latest 31 Oct 2026 \(today\)/)
  })

  it("falls back to the ledger's start date when there are no entries", () => {
    const empty = { ...closingWindow, lastEntryDate: null, earliestEndDate: '2026-10-01' }
    expect(endDateHint(empty)).toMatch(/Earliest 1 Oct 2026 \(the ledger's start date\)/)
  })
})

describe('checkEndDate', () => {
  it('accepts the earliest and the latest allowed day', () => {
    expect(checkEndDate('2026-10-27', closingWindow)).toBe('')
    expect(checkEndDate('2026-10-31', closingWindow)).toBe('')
  })

  it('asks for a date when the field is empty', () => {
    expect(checkEndDate('', closingWindow)).toBe('Choose an end date.')
  })

  it('rejects a day before the latest entry and names it', () => {
    expect(checkEndDate('2026-10-20', closingWindow)).toBe(
      'The end date cannot be earlier than 27 Oct 2026 (your latest entry).'
    )
  })

  it('rejects a day in the future and names today', () => {
    expect(checkEndDate('2026-11-02', closingWindow)).toBe(
      'The end date cannot be in the future. Today is 31 Oct 2026.'
    )
  })
})

describe('request and period helpers', () => {
  it("closes the whole ledger: the period starts on the ledger's own start date", () => {
    expect(periodRequest(3, '2026-10-31', closingWindow)).toEqual({
      ledgerId: 3,
      periodStart: '2026-10-01',
      periodEnd: '2026-10-31'
    })
  })

  it('keeps entries on both boundary days and drops the rest', () => {
    const entries = [
      { transactionId: 1, transactionDate: '2026-09-30' },
      { transactionId: 2, transactionDate: '2026-10-01' },
      { transactionId: 3, transactionDate: '2026-10-31' },
      { transactionId: 4, transactionDate: '2026-11-01' }
    ]
    expect(entriesInPeriod(entries, summary).map((t) => t.transactionId)).toEqual([2, 3])
  })

  it('writes both ends of the period', () => {
    expect(periodLabel(summary)).toBe('1 Oct 2026 to 31 Oct 2026')
  })

  it('lists the figures in verification order with Indian digit grouping', () => {
    expect(figureRows(summary)).toEqual([
      ['Opening balance', '₹50,000.00'],
      ['Total debit', '₹1,20,000.00'],
      ['Total credit', '₹1,50,000.00'],
      ['Closing balance', '₹80,000.00'],
      ['Debit entries', '4'],
      ['Credit entries', '6']
    ])
  })

  it('counts both sides when deciding whether a ledger has entries', () => {
    expect(hasNoEntries({ debitCount: 0, creditCount: 0 })).toBe(true)
    expect(hasNoEntries({ debitCount: 0, creditCount: 1 })).toBe(false)
  })
})

describe('next ledger wording', () => {
  it('compares ISO dates to decide whether a ledger has started', () => {
    expect(hasNotStarted('2026-11-01', '2026-10-31')).toBe(true)
    expect(hasNotStarted('2026-10-31', '2026-10-31')).toBe(false)
    expect(hasNotStarted('2026-10-01', '2026-10-31')).toBe(false)
  })

  it('names the start date of a ledger that has not started', () => {
    expect(notStartedMessage('2026-11-01')).toBe(
      'This ledger starts on 1 Nov 2026. You can add transactions from that day.'
    )
  })

  it('states the next ledger start date and opening balance', () => {
    const sameDay = { ...summary, nextLedgerStartDate: '2026-10-31' }
    expect(nextLedgerNote(sameDay, '2026-10-31')).toBe(
      'The next ledger starts on 31 Oct 2026 with an opening balance of ₹80,000.00.'
    )
  })

  it('adds the wait when the next ledger starts after today', () => {
    expect(nextLedgerNote(summary, '2026-10-31')).toBe(
      'The next ledger starts on 1 Nov 2026 with an opening balance of ₹80,000.00. You can add transactions from that day.'
    )
  })

  it("uses the new ledger's own figures after closing", () => {
    const next = { ledgerId: 4, startDate: '2026-11-01', openingBalance: 80000 }
    expect(newLedgerNote(next, '2026-10-31')).toBe(
      'The new ledger starts on 1 Nov 2026 with an opening balance of ₹80,000.00. You can add transactions from that day.'
    )
    expect(newLedgerNote(next, '2026-11-01')).toBe(
      'The new ledger starts on 1 Nov 2026 with an opening balance of ₹80,000.00.'
    )
  })
})

describe('close errors', () => {
  it('says nothing was changed when the request was refused', () => {
    expect(closeErrorMessage({ status: 409, message: 'The ledger changed.' })).toBe(
      'The ledger was not closed and nothing was changed. The ledger changed.'
    )
  })

  it('admits it cannot be sure after a lost connection', () => {
    expect(closeErrorMessage({ status: 0, message: 'Cannot reach the backend.' })).toMatch(
      /not certain whether the ledger was closed/
    )
  })

  it('refreshes after network, not-found and conflict errors only', () => {
    expect(shouldRefreshAfter({ status: 0 })).toBe(true)
    expect(shouldRefreshAfter({ status: 404 })).toBe(true)
    expect(shouldRefreshAfter({ status: 409 })).toBe(true)
    expect(shouldRefreshAfter({ status: 400 })).toBe(false)
    expect(shouldRefreshAfter({ status: 500 })).toBe(false)
  })
})
