import { formatDate } from '../format'
import { formatCurrency } from '../money'

// Wording and small decisions for Verify & Close Ledger. The rules themselves (which end dates
// are allowed, whether a ledger can close) belong to the backend, which enforces them and
// publishes the allowed range; nothing here recalculates money or re-derives a rule.

export const READ_ONLY_NOTE =
  'After closing, this ledger becomes a read-only record. It cannot be reopened or edited.'

function earliestReason(closingWindow) {
  return closingWindow.lastEntryDate ? 'your latest entry' : "the ledger's start date"
}

// Until the user picks a date, the field shows the earliest allowed one, which can never be
// refused and never leaves a day without a ledger.
export function defaultEndDate(closingWindow) {
  return closingWindow.earliestEndDate
}

export function endDateHint(closingWindow) {
  const earliest = formatDate(closingWindow.earliestEndDate)
  const latest = formatDate(closingWindow.latestEndDate)
  const reason = earliestReason(closingWindow)
  return `The last day this ledger covers. Earliest ${earliest} (${reason}); latest ${latest} (today).`
}

// '' means the date is acceptable. ISO dates compare correctly as plain strings.
export function checkEndDate(endDate, closingWindow) {
  if (!endDate) return 'Choose an end date.'
  if (endDate < closingWindow.earliestEndDate) {
    const earliest = formatDate(closingWindow.earliestEndDate)
    return `The end date cannot be earlier than ${earliest} (${earliestReason(closingWindow)}).`
  }
  if (endDate > closingWindow.latestEndDate) {
    return `The end date cannot be in the future. Today is ${formatDate(closingWindow.latestEndDate)}.`
  }
  return ''
}

// A ledger is closed as a whole, so the period always starts on the ledger's own start date.
export function periodRequest(ledgerId, endDate, closingWindow) {
  return { ledgerId, periodStart: closingWindow.startDate, periodEnd: endDate }
}

// Both boundary days are included (SRS 8.2).
export function entriesInPeriod(transactions, summary) {
  return transactions.filter(
    (t) => t.transactionDate >= summary.periodStart && t.transactionDate <= summary.periodEnd
  )
}

export function periodLabel(summary) {
  return `${formatDate(summary.periodStart)} to ${formatDate(summary.periodEnd)}`
}

// The backend's own figures, in the order of SRS 8.1.
export function figureRows(summary) {
  return [
    ['Opening balance', formatCurrency(summary.openingBalance)],
    ['Total debit', formatCurrency(summary.totalDebit)],
    ['Total credit', formatCurrency(summary.totalCredit)],
    ['Closing balance', formatCurrency(summary.closingBalance)],
    ['Debit entries', String(summary.debitCount)],
    ['Credit entries', String(summary.creditCount)]
  ]
}

export function hasNoEntries(summary) {
  return summary.debitCount + summary.creditCount === 0
}

export function hasNotStarted(startDate, today) {
  return startDate > today
}

export function notStartedMessage(startDate) {
  return `This ledger starts on ${formatDate(startDate)}. You can add transactions from that day.`
}

export function nextLedgerNote(summary, today) {
  const start = formatDate(summary.nextLedgerStartDate)
  const opening = formatCurrency(summary.closingBalance)
  const note = `The next ledger starts on ${start} with an opening balance of ${opening}.`
  return summary.nextLedgerStartDate > today ? `${note} You can add transactions from that day.` : note
}

// After closing: the new ledger's own figures, as the backend returned them.
export function newLedgerNote(nextLedger, today) {
  const start = formatDate(nextLedger.startDate)
  const opening = formatCurrency(nextLedger.openingBalance)
  const note = `The new ledger starts on ${start} with an opening balance of ${opening}.`
  return nextLedger.startDate > today ? `${note} You can add transactions from that day.` : note
}

export function closeErrorMessage(error) {
  if (error.status === 0) {
    return 'The connection was lost, so it is not certain whether the ledger was closed. Check the Current Ledger before trying again.'
  }
  return `The ledger was not closed and nothing was changed. ${error.message}`
}

// After these, the ledger may no longer be what the screen thinks it is.
export function shouldRefreshAfter(error) {
  return error.status === 0 || error.status === 404 || error.status === 409
}