import { formatDate } from '../format'
import { formatCurrency } from '../money'

// Whole-ledger figures straight from the backend (FR-002). Filters never change these.
export default function LedgerSummaryStrip({ ledger }) {
  const name = ledger.ledgerName
    ? `${ledger.ledgerName} (#${ledger.ledgerId})`
    : `#${ledger.ledgerId}`
  const items = [
    ['Ledger', name],
    ['Start date', formatDate(ledger.startDate)],
    ['Opening balance', formatCurrency(ledger.openingBalance)],
    ['Current balance', formatCurrency(ledger.currentBalance)],
    ['Total debit', formatCurrency(ledger.totalDebit)],
    ['Total credit', formatCurrency(ledger.totalCredit)],
    ['Transactions', String(ledger.transactionCount)]
  ]

  return (
    <dl className="summary-strip">
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  )
}
