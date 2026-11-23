import { useMemo, useState } from 'react'
import GuardedLink from '../components/GuardedLink'
import SavedFilterBar from '../components/SavedFilterBar'
import { formatCurrency } from '../money'
import {
  EMPTY_FILTERS,
  closedOnParts,
  entryCountText,
  filterNote,
  filterSavedLedgers,
  hasActiveFilters,
  periodParts,
  savedLedgerLabel
} from '../saved/logic'
import { useSavedLedgers } from '../saved/useSavedLedgers'

// Each part stays on one line; the cell breaks between parts when it is narrow.
function Parts({ parts }) {
  return parts.map((part) => (
    <span key={part} className="nowrap">
      {part}{' '}
    </span>
  ))
}

// Every closed ledger, most recently closed first (SRS 10.1). Nothing here can change a ledger.
export default function SavedLedgers() {
  const { status, data, error, reload } = useSavedLedgers()
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const ledgers = useMemo(() => data ?? [], [data])
  const shown = useMemo(() => filterSavedLedgers(ledgers, filters), [ledgers, filters])
  const filtering = hasActiveFilters(filters)

  if (status === 'loading') return <p role="status">Loading saved ledgers…</p>
  if (status === 'error') {
    return (
      <div className="banner" role="alert">
        <span>{error.message}</span>
        <button type="button" onClick={reload}>
          Try again
        </button>
      </div>
    )
  }

  return (
    <div className="saved-ledgers">
      {error && (
        <div className="banner" role="alert">
          <span>{error.message}</span>
          <button type="button" onClick={reload}>
            Reload
          </button>
        </div>
      )}
      <p className="saved-intro">
        Every ledger you have closed, most recently closed first. Opening one shows it exactly as it
        was closed. Nothing here can be changed.
      </p>

      {ledgers.length === 0 ? (
        <div className="empty-state">
          <h2>No saved ledgers yet</h2>
          <p>A ledger appears here after you close it.</p>
          <GuardedLink to="/verify-close" className="button-link">
            Verify &amp; Close Ledger
          </GuardedLink>
        </div>
      ) : (
        <>
          <SavedFilterBar
            filters={filters}
            onChange={setFilters}
            onClear={() => setFilters(EMPTY_FILTERS)}
            active={filtering}
          />
          {filtering && (
            <p role="status" className="filter-note">
              {filterNote(shown.length, ledgers.length)}
            </p>
          )}
          <div className="table-scroll">
            <table>
              <caption className="visually-hidden">Saved ledgers</caption>
              <thead>
                <tr>
                  <th scope="col">Ledger</th>
                  <th scope="col">Period</th>
                  <th scope="col" className="amount">
                    Opening balance
                  </th>
                  <th scope="col" className="amount">
                    Total credit
                  </th>
                  <th scope="col" className="amount">
                    Total debit
                  </th>
                  <th scope="col" className="amount">
                    Closing balance
                  </th>
                  <th scope="col">Closed on</th>
                </tr>
              </thead>
              <tbody>
                {shown.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="empty-row">
                      No saved ledger matches these filters.
                    </td>
                  </tr>
                ) : (
                  shown.map((ledger) => (
                    <tr key={ledger.ledgerId}>
                      <th scope="row">
                        <GuardedLink
                          to={`/saved-ledgers/${ledger.ledgerId}`}
                          aria-label={`Open ${savedLedgerLabel(ledger)}`}
                        >
                          {savedLedgerLabel(ledger)}
                        </GuardedLink>
                        <span className="status-tag">
                          Closed · {entryCountText(ledger.transactionCount)}
                        </span>
                      </th>
                      <td>
                        <Parts parts={periodParts(ledger)} />
                      </td>
                      <td className="amount">{formatCurrency(ledger.openingBalance)}</td>
                      <td className="amount">{formatCurrency(ledger.totalCredit)}</td>
                      <td className="amount">{formatCurrency(ledger.totalDebit)}</td>
                      <td className="amount">{formatCurrency(ledger.closingBalance)}</td>
                      <td>
                        <Parts parts={closedOnParts(ledger)} />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  )
}
