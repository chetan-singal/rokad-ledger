import { useCallback } from 'react'
import { useParams } from 'react-router'
import { getSavedLedger } from '../api/client'
import GuardedLink from '../components/GuardedLink'
import LedgerSides from '../components/LedgerSides'
import { formatCurrency } from '../money'
import {
  STORED_NOTE,
  closedOnText,
  loadErrorMessage,
  periodText,
  savedLedgerLabel,
  storedFigureRows
} from '../saved/logic'
import { useLoaded } from '../useLoaded'

// One closed ledger exactly as it was closed (SRS 10.2, 10.3): the figures stored at closure, and
// its entries in the same two-sided layout as Current Ledger, read only.
export default function SavedLedgerDetail() {
  const { ledgerId } = useParams()
  const load = useCallback(() => getSavedLedger(ledgerId), [ledgerId])
  const { status, data, error, reload } = useLoaded(load)

  const back = (
    <GuardedLink to="/saved-ledgers" className="button-link">
      Back to Saved Ledgers
    </GuardedLink>
  )

  if (status === 'loading') return <p role="status">Loading the saved ledger…</p>
  if (status === 'error') {
    return (
      <div className="saved-detail">
        <div className="banner" role="alert">
          <span>{loadErrorMessage(error)}</span>
          {error.status !== 404 && (
            <button type="button" onClick={reload}>
              Try again
            </button>
          )}
        </div>
        <p>{back}</p>
      </div>
    )
  }

  const { summary, transactions } = data
  return (
    <div className="saved-detail">
      <h2 className="saved-title">{savedLedgerLabel(summary)}</h2>
      <p className="saved-period">
        {periodText(summary)} · closed on {closedOnText(summary)}
      </p>
      <dl className="summary-strip" aria-label="Figures stored when this ledger was closed">
        {storedFigureRows(summary).map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <p className="notice">{STORED_NOTE}</p>
      <LedgerSides
        transactions={transactions}
        totals={{
          debit: formatCurrency(summary.totalDebit),
          credit: formatCurrency(summary.totalCredit)
        }}
      />
      <p>{back}</p>
    </div>
  )
}
