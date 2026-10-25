import { formatDate } from '../format'
import { formatCurrency, formatPaise, sumPaise, toPaise } from '../money'

export const PAGE_SIZE = 50
export const NO_FILTERS = { from: '', to: '', keyword: '', type: 'ALL' }

// Matches DECIMAL(15,2): up to 13 digits, at most two decimals, never negative.
const AMOUNT_PATTERN = /^\d{1,13}(\.\d{1,2})?$/

export function typeLabel(type) {
  return type === 'DEBIT' ? 'Debit' : 'Credit'
}

// ---- the add / edit form -------------------------------------------------------------

export function emptyForm(today) {
  return { transactionType: '', transactionDate: today, amount: '', reason: '' }
}

export function formFromTransaction(transaction) {
  return {
    transactionType: transaction.transactionType,
    transactionDate: transaction.transactionDate,
    amount: (toPaise(transaction.amount) / 100).toFixed(2),
    reason: transaction.reason
  }
}

// Same rules, same wording and same field names as TransactionManagementService, so a
// message reads identically whether the browser or the server caught the problem. The
// server stays the authority; this exists to report every problem in one pass.
export function validateTransaction(values, { ledgerStartDate, today }) {
  const errors = {}

  if (values.transactionType !== 'DEBIT' && values.transactionType !== 'CREDIT') {
    errors.transactionType = 'Transaction type is required.'
  }

  if (!values.transactionDate) {
    errors.transactionDate = 'Transaction date is required.'
  } else if (values.transactionDate < ledgerStartDate) {
    errors.transactionDate = "Transaction date cannot be before the ledger's start date."
  } else if (values.transactionDate > today) {
    errors.transactionDate = 'Future-dated transactions are not allowed.'
  }

  const amount = values.amount.trim()
  if (!amount || amount.startsWith('-')) {
    errors.amount = 'Amount must be greater than zero.'
  } else if (!AMOUNT_PATTERN.test(amount)) {
    errors.amount = 'Enter an amount like 1500 or 1500.50, with at most two decimal places.'
  } else if (toPaise(amount) === 0) {
    errors.amount = 'Amount must be greater than zero.'
  }

  if (!values.reason.trim()) {
    errors.reason = 'Reason cannot be empty.'
  }

  return errors
}

export function hasErrors(errors) {
  return Object.keys(errors).length > 0
}

// The amount goes out as the string the user typed (never through a JS number), and the
// reason is trimmed so "  Rent " and "Rent" are the same entry.
export function toRequest(values) {
  return {
    transactionType: values.transactionType,
    transactionDate: values.transactionDate,
    amount: values.amount.trim(),
    reason: values.reason.trim()
  }
}

// FR-004: an edit that changes the amount, type or date needs a confirmation.
export function changesAmountTypeOrDate(transaction, values) {
  return (
    values.transactionType !== transaction.transactionType ||
    values.transactionDate !== transaction.transactionDate ||
    toPaise(values.amount.trim()) !== toPaise(transaction.amount)
  )
}

export function describeTransaction(t) {
  return `${typeLabel(t.transactionType)} ${formatCurrency(t.amount)} on ${formatDate(
    t.transactionDate
  )} (${t.reason})`
}

// ---- the two lists -------------------------------------------------------------------

// Date, then id: the same stable same-day order the backend uses (SRS 5.3).
export function sortChronologically(transactions) {
  return [...transactions].sort((a, b) => {
    if (a.transactionDate !== b.transactionDate) {
      return a.transactionDate < b.transactionDate ? -1 : 1
    }
    return a.transactionId - b.transactionId
  })
}

export function isFilterActive(filters) {
  return Boolean(filters.from || filters.to || filters.keyword.trim() || filters.type !== 'ALL')
}

// Order-preserving. From and To are inclusive; a From later than To matches nothing.
export function filterTransactions(transactions, filters) {
  const keyword = filters.keyword.trim().toLowerCase()
  return transactions.filter((t) => {
    if (filters.type !== 'ALL' && t.transactionType !== filters.type) return false
    if (filters.from && t.transactionDate < filters.from) return false
    if (filters.to && t.transactionDate > filters.to) return false
    if (keyword && !t.reason.toLowerCase().includes(keyword)) return false
    return true
  })
}

// Two independent lists: nothing here ever pairs a debit with a credit (SRS 5.3).
export function splitBySide(transactions) {
  const debit = []
  const credit = []
  for (const t of transactions) (t.transactionType === 'DEBIT' ? debit : credit).push(t)
  return { debit, credit }
}

export function paginate(items, page, pageSize = PAGE_SIZE) {
  const total = items.length
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const current = Math.min(Math.max(1, page), pageCount)
  const start = (current - 1) * pageSize
  return {
    items: items.slice(start, start + pageSize),
    page: current,
    pageCount,
    total,
    first: total === 0 ? 0 : start + 1,
    last: Math.min(start + pageSize, total)
  }
}

export function formatTotal(transactions) {
  return formatPaise(sumPaise(transactions.map((t) => t.amount)))
}