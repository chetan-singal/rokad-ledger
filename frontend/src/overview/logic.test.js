import { describe, expect, it } from 'vitest'
import { activityRow, dashboardCards, hasNoTransactions, ledgerLabel, statusLabel } from './logic'

const summary = {
  ledgerId: 3,
  ledgerName: 'October',
  startDate: '2026-10-01',
  status: 'ACTIVE',
  openingBalance: 50000,
  currentBalance: 61500,
  totalDebit: 3500,
  totalCredit: 15000,
  transactionCount: 4
}
const overview = {
  summary,
  today: '2026-10-27',
  todayDebit: 1000,
  todayCredit: 5000.5,
  recentTransactions: []
}

describe('dashboardCards', () => {
  it('lists the seven cards of SRS 7.2 in the SRS order', () => {
    expect(dashboardCards(overview).map((card) => card.label)).toEqual([
      'Opening Balance',
      'Total Credit',
      'Total Debit',
      'Current Balance',
      'Total Transactions',
      "Today's Debit",
      "Today's Credit"
    ])
  })

  it('formats money as rupees and the count as a plain number', () => {
    expect(dashboardCards(overview).map((card) => card.value)).toEqual([
      '₹50,000.00',
      '₹15,000.00',
      '₹3,500.00',
      '₹61,500.00',
      '4',
      '₹1,000.00',
      '₹5,000.50'
    ])
  })

  it("takes Today's figures from the overview, never from the ledger totals", () => {
    const [, , , , , todayDebit, todayCredit] = dashboardCards(overview)
    expect(todayDebit.value).not.toBe('₹3,500.00')
    expect(todayCredit.value).not.toBe('₹15,000.00')
  })

  it('shows zero as ₹0.00 on a day with no entries', () => {
    const quiet = { ...overview, todayDebit: 0, todayCredit: 0 }
    const cards = dashboardCards(quiet)
    expect(cards[5].value).toBe('₹0.00')
    expect(cards[6].value).toBe('₹0.00')
  })

  it('gives every card its own key', () => {
    const keys = dashboardCards(overview).map((card) => card.key)
    expect(new Set(keys).size).toBe(7)
  })
})

describe('hasNoTransactions', () => {
  it('is true only while the ledger has no entries', () => {
    expect(hasNoTransactions({ ...summary, transactionCount: 0 })).toBe(true)
    expect(hasNoTransactions({ ...summary, transactionCount: 1 })).toBe(false)
  })
})

describe('statusLabel and ledgerLabel', () => {
  it('words the status for people', () => {
    expect(statusLabel('ACTIVE')).toBe('Active')
    expect(statusLabel('CLOSED')).toBe('Closed')
  })

  it('uses the ledger name, or its number when it has none', () => {
    expect(ledgerLabel(summary)).toBe('October')
    expect(ledgerLabel({ ...summary, ledgerName: null })).toBe('Ledger #3')
  })
})

describe('activityRow', () => {
  it('formats a debit for the recent-activity list', () => {
    const row = activityRow({
      transactionId: 9,
      transactionDate: '2026-10-22',
      transactionType: 'DEBIT',
      amount: 500,
      reason: 'Office supplies'
    })
    expect(row).toEqual({
      id: 9,
      date: '22 Oct 2026',
      side: 'Debit',
      reason: 'Office supplies',
      amount: '₹500.00'
    })
  })

  it('formats a credit, with Indian digit grouping', () => {
    const row = activityRow({
      transactionId: 10,
      transactionDate: '2026-10-05',
      transactionType: 'CREDIT',
      amount: 150000.5,
      reason: 'Client payment'
    })
    expect(row.side).toBe('Credit')
    expect(row.amount).toBe('₹1,50,000.50')
  })
})