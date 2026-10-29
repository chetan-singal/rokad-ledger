import { formatDate } from '../format'
import { formatCurrency } from '../money'
import { typeLabel } from '../transactions/logic'

// Pure helpers for Home and Dashboard. Every figure arrives from the backend's overview;
// nothing in this file adds, subtracts or filters money (SRS 20: the browser never calculates).

// SRS 6.3: with no entries yet, Home shows its empty state instead of recent activity.
export function hasNoTransactions(summary) {
  return summary.transactionCount === 0
}

export function statusLabel(status) {
  return status === 'ACTIVE' ? 'Active' : 'Closed'
}

// Same wording as the header's ledger indicator.
export function ledgerLabel(summary) {
  return summary.ledgerName || `Ledger #${summary.ledgerId}`
}

// The seven cards of SRS 7.2, in the SRS's own order.
export function dashboardCards(overview) {
  const { summary } = overview
  return [
    { key: 'opening', label: 'Opening Balance', value: formatCurrency(summary.openingBalance) },
    { key: 'credit', label: 'Total Credit', value: formatCurrency(summary.totalCredit) },
    { key: 'debit', label: 'Total Debit', value: formatCurrency(summary.totalDebit) },
    { key: 'balance', label: 'Current Balance', value: formatCurrency(summary.currentBalance) },
    { key: 'count', label: 'Total Transactions', value: String(summary.transactionCount) },
    { key: 'today-debit', label: "Today's Debit", value: formatCurrency(overview.todayDebit) },
    { key: 'today-credit', label: "Today's Credit", value: formatCurrency(overview.todayCredit) }
  ]
}

// One line of Home's recent-activity list.
export function activityRow(transaction) {
  return {
    id: transaction.transactionId,
    date: formatDate(transaction.transactionDate),
    side: typeLabel(transaction.transactionType),
    reason: transaction.reason,
    amount: formatCurrency(transaction.amount)
  }
}