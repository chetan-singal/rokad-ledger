import { describe, expect, it } from 'vitest'
import {
  NO_FILTERS,
  changesAmountTypeOrDate,
  describeTransaction,
  emptyForm,
  filterTransactions,
  formFromTransaction,
  formatTotal,
  hasErrors,
  isFilterActive,
  paginate,
  sortChronologically,
  splitBySide,
  toRequest,
  typeLabel,
  validateTransaction
} from './logic'

const context = { ledgerStartDate: '2026-04-01', today: '2026-10-22' }
const valid = {
  transactionType: 'DEBIT',
  transactionDate: '2026-10-01',
  amount: '500.00',
  reason: 'Office supplies'
}
const tx = (id, date, type, amount, reason = `entry ${id}`) => ({
  transactionId: id,
  transactionDate: date,
  transactionType: type,
  amount,
  reason
})

describe('validateTransaction', () => {
  it('accepts a complete, valid entry', () => {
    expect(validateTransaction(valid, context)).toEqual({})
  })

  it('uses the backend wording, field for field', () => {
    const errors = validateTransaction(
      { transactionType: '', transactionDate: '', amount: '', reason: '   ' },
      context
    )
    expect(errors).toEqual({
      transactionType: 'Transaction type is required.',
      transactionDate: 'Transaction date is required.',
      amount: 'Amount must be greater than zero.',
      reason: 'Reason cannot be empty.'
    })
  })

  it('reports every problem in one pass', () => {
    const errors = validateTransaction({ ...valid, amount: '0', reason: '' }, context)
    expect(Object.keys(errors).sort()).toEqual(['amount', 'reason'])
  })

  it('checks the date against the ledger start and today, both ends allowed', () => {
    expect(validateTransaction({ ...valid, transactionDate: '2026-04-01' }, context)).toEqual({})
    expect(validateTransaction({ ...valid, transactionDate: '2026-10-22' }, context)).toEqual({})
    expect(validateTransaction({ ...valid, transactionDate: '2026-03-31' }, context)).toEqual({
      transactionDate: "Transaction date cannot be before the ledger's start date."
    })
    expect(validateTransaction({ ...valid, transactionDate: '2026-10-23' }, context)).toEqual({
      transactionDate: 'Future-dated transactions are not allowed.'
    })
  })

  it.each([
    ['', 'Amount must be greater than zero.'],
    ['0', 'Amount must be greater than zero.'],
    ['0.00', 'Amount must be greater than zero.'],
    ['-5', 'Amount must be greater than zero.'],
    ['12.345', 'Enter an amount like 1500 or 1500.50, with at most two decimal places.'],
    ['abc', 'Enter an amount like 1500 or 1500.50, with at most two decimal places.'],
    ['1e3', 'Enter an amount like 1500 or 1500.50, with at most two decimal places.'],
    ['1,000', 'Enter an amount like 1500 or 1500.50, with at most two decimal places.']
  ])('rejects the amount %j', (amount, message) => {
    expect(validateTransaction({ ...valid, amount }, context).amount).toBe(message)
  })

  it.each(['0.01', '1', '1500.5', '1500.50', ' 42 ', '9999999999999.99'])(
    'accepts the amount %j',
    (amount) => {
      expect(validateTransaction({ ...valid, amount }, context).amount).toBeUndefined()
    }
  )

  it('rejects an amount wider than DECIMAL(15,2)', () => {
    expect(validateTransaction({ ...valid, amount: '12345678901234' }, context).amount).toMatch(
      /at most two decimal places/
    )
  })
})

describe('form helpers', () => {
  it('starts an add form with today and nothing else', () => {
    expect(emptyForm('2026-10-22')).toEqual({
      transactionType: '',
      transactionDate: '2026-10-22',
      amount: '',
      reason: ''
    })
  })

  it('fills an edit form from a transaction, amount as two-decimal text', () => {
    expect(formFromTransaction(tx(1, '2026-10-01', 'CREDIT', 600, 'Sale'))).toEqual({
      transactionType: 'CREDIT',
      transactionDate: '2026-10-01',
      amount: '600.00',
      reason: 'Sale'
    })
    expect(formFromTransaction(tx(2, '2026-10-01', 'DEBIT', 0.5)).amount).toBe('0.50')
  })

  it('sends the amount as typed and trims the reason', () => {
    expect(toRequest({ ...valid, amount: ' 600.50 ', reason: '  Rent ' })).toEqual({
      transactionType: 'DEBIT',
      transactionDate: '2026-10-01',
      amount: '600.50',
      reason: 'Rent'
    })
  })

  it('reports whether any error exists', () => {
    expect(hasErrors({})).toBe(false)
    expect(hasErrors({ amount: 'x' })).toBe(true)
  })
})

describe('changesAmountTypeOrDate (FR-004)', () => {
  const original = tx(1, '2026-10-01', 'DEBIT', 600, 'Rent')
  const same = { transactionType: 'DEBIT', transactionDate: '2026-10-01', amount: '600.00', reason: 'Rent' }

  it('is false for a reason-only edit, and for the same amount written differently', () => {
    expect(changesAmountTypeOrDate(original, { ...same, reason: 'Rent, corrected' })).toBe(false)
    expect(changesAmountTypeOrDate(original, { ...same, amount: '600.0' })).toBe(false)
    expect(changesAmountTypeOrDate(original, { ...same, amount: '600' })).toBe(false)
  })

  it('is true when the amount, type or date changes', () => {
    expect(changesAmountTypeOrDate(original, { ...same, amount: '600.01' })).toBe(true)
    expect(changesAmountTypeOrDate(original, { ...same, transactionType: 'CREDIT' })).toBe(true)
    expect(changesAmountTypeOrDate(original, { ...same, transactionDate: '2026-10-02' })).toBe(true)
  })
})

describe('sortChronologically', () => {
  it('orders by date, then by id, without touching the input', () => {
    const input = [
      tx(3, '2026-10-02', 'DEBIT', 1),
      tx(2, '2026-10-01', 'CREDIT', 1),
      tx(1, '2026-10-02', 'CREDIT', 1),
      tx(4, '2026-09-30', 'DEBIT', 1)
    ]
    const sorted = sortChronologically(input)
    expect(sorted.map((t) => t.transactionId)).toEqual([4, 2, 1, 3])
    expect(input.map((t) => t.transactionId)).toEqual([3, 2, 1, 4])
  })
})

describe('filterTransactions', () => {
  const all = sortChronologically([
    tx(1, '2026-10-01', 'DEBIT', 100, 'Office Supplies'),
    tx(2, '2026-10-05', 'CREDIT', 200, 'Payment from Rahul'),
    tx(3, '2026-10-10', 'DEBIT', 300, 'Transport'),
    tx(4, '2026-10-15', 'CREDIT', 400, 'Supplies refund')
  ])
  const ids = (list) => list.map((t) => t.transactionId)

  it('returns everything, in the same order, when no filter is set', () => {
    expect(ids(filterTransactions(all, NO_FILTERS))).toEqual([1, 2, 3, 4])
    expect(isFilterActive(NO_FILTERS)).toBe(false)
  })

  it('treats From and To as inclusive', () => {
    expect(ids(filterTransactions(all, { ...NO_FILTERS, from: '2026-10-05', to: '2026-10-10' }))).toEqual([2, 3])
    expect(ids(filterTransactions(all, { ...NO_FILTERS, from: '2026-10-10' }))).toEqual([3, 4])
    expect(ids(filterTransactions(all, { ...NO_FILTERS, to: '2026-10-01' }))).toEqual([1])
  })

  it('matches the keyword in the reason, ignoring case and surrounding spaces', () => {
    expect(ids(filterTransactions(all, { ...NO_FILTERS, keyword: '  SUPPLIES ' }))).toEqual([1, 4])
    expect(ids(filterTransactions(all, { ...NO_FILTERS, keyword: '300' }))).toEqual([]) // reason only
  })

  it('filters by type', () => {
    expect(ids(filterTransactions(all, { ...NO_FILTERS, type: 'DEBIT' }))).toEqual([1, 3])
    expect(ids(filterTransactions(all, { ...NO_FILTERS, type: 'CREDIT' }))).toEqual([2, 4])
  })

  it('combines filters with AND', () => {
    const filters = { from: '2026-10-02', to: '2026-10-31', keyword: 'supplies', type: 'CREDIT' }
    expect(ids(filterTransactions(all, filters))).toEqual([4])
  })

  it('matches nothing when From is after To', () => {
    expect(filterTransactions(all, { ...NO_FILTERS, from: '2026-10-10', to: '2026-10-01' })).toEqual([])
  })

  it('knows when a filter is active', () => {
    expect(isFilterActive({ ...NO_FILTERS, keyword: '  ' })).toBe(false)
    expect(isFilterActive({ ...NO_FILTERS, keyword: 'x' })).toBe(true)
    expect(isFilterActive({ ...NO_FILTERS, type: 'DEBIT' })).toBe(true)
    expect(isFilterActive({ ...NO_FILTERS, from: '2026-10-01' })).toBe(true)
  })
})

describe('splitBySide (SRS 5.3: two independent lists)', () => {
  it('partitions by type, keeping each side in its own order, never pairing rows', () => {
    const list = [
      tx(1, '2026-10-01', 'DEBIT', 1),
      tx(2, '2026-10-02', 'DEBIT', 1),
      tx(3, '2026-10-03', 'CREDIT', 1),
      tx(4, '2026-10-04', 'DEBIT', 1)
    ]
    const { debit, credit } = splitBySide(list)
    expect(debit.map((t) => t.transactionId)).toEqual([1, 2, 4])
    expect(credit.map((t) => t.transactionId)).toEqual([3])
    expect(debit.length + credit.length).toBe(list.length) // unequal counts are fine (TC-018)
  })
})

describe('paginate', () => {
  const items = Array.from({ length: 120 }, (_, i) => i + 1)

  it('slices pages of 50 and reports the range shown', () => {
    const first = paginate(items, 1)
    expect(first.items).toHaveLength(50)
    expect([first.page, first.pageCount, first.total, first.first, first.last]).toEqual([1, 3, 120, 1, 50])
    const last = paginate(items, 3)
    expect(last.items).toEqual(items.slice(100))
    expect([last.first, last.last]).toEqual([101, 120])
  })

  it('clamps a page that does not exist, including the "jump to last" Infinity', () => {
    expect(paginate(items, 0).page).toBe(1)
    expect(paginate(items, 99).page).toBe(3)
    expect(paginate(items, Number.POSITIVE_INFINITY).page).toBe(3)
  })

  it('handles empty and exact-multiple lists', () => {
    const empty = paginate([], 5)
    expect([empty.page, empty.pageCount, empty.first, empty.last, empty.items.length]).toEqual([1, 1, 0, 0, 0])
    expect(paginate(items.slice(0, 100), 1).pageCount).toBe(2)
    expect(paginate(items.slice(0, 101), 1).pageCount).toBe(3)
  })
})

describe('totals and wording', () => {
  it('sums a filtered subset exactly', () => {
    expect(formatTotal([tx(1, '2026-10-01', 'DEBIT', 0.1), tx(2, '2026-10-01', 'DEBIT', 0.2)])).toBe('₹0.30')
    expect(formatTotal([])).toBe('₹0.00')
  })

  it('describes a transaction for dialogs and screen readers', () => {
    expect(describeTransaction(tx(1, '2026-04-02', 'DEBIT', 500, 'Office supplies'))).toBe(
      'Debit ₹500.00 on 2 Apr 2026 (Office supplies)'
    )
    expect(typeLabel('CREDIT')).toBe('Credit')
  })
})