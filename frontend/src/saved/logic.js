import { formatDate, formatDateTime } from '../format'
import { formatCurrency } from '../money'

export const EMPTY_FILTERS = { text: '', from: '', to: '' }

export const STORED_NOTE =
  'A read-only record. These figures were stored when the ledger was closed and are never recalculated.'

export function savedLedgerLabel(ledger) {
  return ledger.ledgerName
    ? `${ledger.ledgerName} (#${ledger.ledgerId})`
    : `Ledger #${ledger.ledgerId}`
}

// The text matches any part of the name, in any case, or the whole ledger number ("3" or "#3").
function matchesText(ledger, text) {
  const wanted = text.trim().toLowerCase()
  if (!wanted) return true
  const number = wanted.startsWith('#') ? wanted.slice(1) : wanted
  return (
    (ledger.ledgerName ?? '').toLowerCase().includes(wanted) || String(ledger.ledgerId) === number
  )
}

// A ledger matches a date range when its own period overlaps it. ISO dates compare as text.
function overlaps(ledger, from, to) {
  if (from && ledger.endDate < from) return false
  if (to && ledger.startDate > to) return false
  return true
}

export function isBackwardsRange(filters) {
  return Boolean(filters.from && filters.to && filters.from > filters.to)
}

export function filterSavedLedgers(ledgers, filters) {
  if (isBackwardsRange(filters)) return []
  return ledgers.filter(
    (ledger) => matchesText(ledger, filters.text) && overlaps(ledger, filters.from, filters.to)
  )
}

export function hasActiveFilters(filters) {
  return Boolean(filters.text.trim() || filters.from || filters.to)
}

export function filterNote(shown, total) {
  return `Filtered: showing ${shown} of ${total} saved ${total === 1 ? 'ledger' : 'ledgers'}.`
}

// The period and the closing time in two parts each, so a narrow table cell can break between
// them instead of in the middle of a date.
export function periodParts(ledger) {
  return [formatDate(ledger.startDate), `to ${formatDate(ledger.endDate)}`]
}

export function periodText(ledger) {
  return periodParts(ledger).join(' ')
}

export function closedOnParts(ledger) {
  const text = formatDateTime(ledger.closedAt)
  const cut = text.lastIndexOf(', ')
  return cut < 0 ? [text] : [text.slice(0, cut + 1), text.slice(cut + 2)]
}

export function closedOnText(ledger) {
  return closedOnParts(ledger).join(' ')
}

export function entryCountText(count) {
  return count === 1 ? '1 entry' : `${count} entries`
}

// The figures stored when the ledger was closed, in the order of SRS 10.1.
export function storedFigureRows(summary) {
  return [
    ['Opening balance', formatCurrency(summary.openingBalance)],
    ['Total debit', formatCurrency(summary.totalDebit)],
    ['Total credit', formatCurrency(summary.totalCredit)],
    ['Closing balance', formatCurrency(summary.closingBalance)],
    ['Entries', String(summary.transactionCount)]
  ]
}

export function loadErrorMessage(error) {
  return error.status === 404
    ? 'This saved ledger does not exist. A restore may have replaced it.'
    : error.message
}
