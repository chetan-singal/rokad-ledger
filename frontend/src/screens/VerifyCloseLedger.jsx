import { useEffect, useRef, useState } from 'react'
import { closeLedger, listTransactions, verifyPeriod } from '../api/client'
import ConfirmDialog from '../components/ConfirmDialog'
import Field from '../components/Field'
import GuardedLink from '../components/GuardedLink'
import LedgerSides from '../components/LedgerSides'
import { formatDate } from '../format'
import { formatCurrency } from '../money'
import { useLedger } from '../state/LedgerContext'
import { useUnsavedChanges } from '../state/UnsavedChangesContext'
import { useToday } from '../useToday'
import {
  READ_ONLY_NOTE,
  checkEndDate,
  closeErrorMessage,
  defaultEndDate,
  endDateHint,
  entriesInPeriod,
  figureRows,
  hasNoEntries,
  newLedgerNote,
  nextLedgerNote,
  notStartedMessage,
  periodLabel,
  periodRequest,
  shouldRefreshAfter
} from '../verify/logic'
import { useClosingWindow } from '../verify/useClosingWindow'

export default function VerifyCloseLedger() {
  const { ledger, refresh } = useLedger()
  const { isDirty } = useUnsavedChanges()
  const today = useToday()
  const { status, closingWindow, error: windowError, reload } = useClosingWindow()

  // null until the user picks a date; until then the field follows the backend's earliest date.
  const [endDate, setEndDate] = useState(null)
  const [endDateError, setEndDateError] = useState('')
  const [verified, setVerified] = useState(null) // { summary, transactions } after Verify
  const [busy, setBusy] = useState('idle') // 'idle' | 'verifying' | 'closing'
  const [confirming, setConfirming] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [closed, setClosed] = useState(null) // { summary, nextLedger } after a successful close
  const resultsHeading = useRef(null)
  const inFlight = useRef(false)

  useEffect(() => {
    if (verified) resultsHeading.current?.focus()
  }, [verified])

  if (closed) return <ClosedPanel closed={closed} today={today} />
  if (status === 'loading') return <p role="status">Loading…</p>
  if (status === 'error') {
    return (
      <div role="alert" className="banner">
        <span>{windowError.message}</span>
        <button type="button" onClick={reload}>
          Try again
        </button>
      </div>
    )
  }
  if (!closingWindow.closable) return <NotStartedPanel closingWindow={closingWindow} />

  const endValue = endDate ?? defaultEndDate(closingWindow)
  const ledgerName = ledger.ledgerName
    ? `${ledger.ledgerName} (#${ledger.ledgerId})`
    : `#${ledger.ledgerId}`
  const idle = busy === 'idle'

  function changeEndDate(event) {
    setEndDate(event.target.value)
    setEndDateError('')
    if (verified) {
      setVerified(null)
      setMessage('The end date changed, so the earlier check was cleared. Verify the period again.')
    }
  }

  // Verifying never closes anything (SRS 8.4). It runs only from the button, because every
  // call is written to the audit log.
  async function handleVerify(event) {
    event.preventDefault()
    if (inFlight.current) return
    const problem = checkEndDate(endValue, closingWindow)
    setEndDateError(problem)
    if (problem) return

    inFlight.current = true
    setBusy('verifying')
    setError('')
    setMessage('')
    setVerified(null)
    try {
      const request = periodRequest(ledger.ledgerId, endValue, closingWindow)
      const [summary, transactions] = await Promise.all([verifyPeriod(request), listTransactions()])
      setVerified({ summary, transactions: entriesInPeriod(transactions, summary) })
      setMessage(`Verified ${periodLabel(summary)}. Review the figures, then close the ledger.`)
    } catch (err) {
      setError(err.message)
    } finally {
      inFlight.current = false
      setBusy('idle')
    }
  }

  // The backend re-checks everything and closes in one database transaction; any refusal or
  // failure leaves the ledger open and unchanged.
  async function performClose() {
    if (inFlight.current || !verified) return
    inFlight.current = true
    setConfirming(false)
    setBusy('closing')
    setError('')
    const { summary } = verified
    try {
      const nextLedger = await closeLedger({
        ledgerId: summary.ledgerId,
        periodStart: summary.periodStart,
        periodEnd: summary.periodEnd,
        verified: summary
      })
      setClosed({ summary, nextLedger })
      await refresh()
    } catch (err) {
      setError(closeErrorMessage(err))
      setVerified(null)
      setMessage('')
      if (shouldRefreshAfter(err)) await Promise.all([refresh(), reload()])
    } finally {
      inFlight.current = false
      setBusy('idle')
    }
  }

  return (
    <div className="verify-close">
      <p className="verify-intro">
        Check your ledger, then close it. Closing saves this ledger as a read-only record and
        starts the next one with the closing balance as its opening balance. Nothing changes
        until you confirm.
      </p>
      <p className="ledger-line">
        <strong>{ledgerName}</strong> · started {formatDate(closingWindow.startDate)}
      </p>

      <form className="period-form" onSubmit={handleVerify} noValidate aria-labelledby="period-heading">
        <h2 id="period-heading">Period to close</h2>
        <div className="period-row">
          <div className="period-fixed">
            <span className="period-label">Start date</span>
            <strong>{formatDate(closingWindow.startDate)}</strong>
            <span className="period-note">the ledger&apos;s own start date</span>
          </div>
          <Field
            id="end-date"
            label="End date"
            hint={endDateHint(closingWindow)}
            error={endDateError}
          >
            {(props) => (
              <input
                {...props}
                type="date"
                value={endValue}
                min={closingWindow.earliestEndDate}
                max={closingWindow.latestEndDate}
                onChange={changeEndDate}
              />
            )}
          </Field>
        </div>
        <button type="submit" disabled={!idle}>
          {busy === 'verifying' ? 'Verifying…' : 'Verify period'}
        </button>
      </form>

      <p role="status" className="status-message">
        {message}
      </p>
      {error && (
        <div role="alert" className="banner">
          <span>{error}</span>
        </div>
      )}

      {verified && (
        <section className="verify-results" aria-labelledby="results-heading">
          <h2 id="results-heading" ref={resultsHeading} tabIndex={-1}>
            Verification: {periodLabel(verified.summary)}
          </h2>
          <dl className="summary-strip">
            {figureRows(verified.summary).map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
          <p className="next-ledger-note">{nextLedgerNote(verified.summary, today)}</p>
          {hasNoEntries(verified.summary) && (
            <p className="notice">This ledger has no entries. Closing it saves an empty record.</p>
          )}
          <LedgerSides
            transactions={verified.transactions}
            totals={{
              debit: formatCurrency(verified.summary.totalDebit),
              credit: formatCurrency(verified.summary.totalCredit)
            }}
          />
        </section>
      )}

      <div className="verify-actions">
        {verified && (
          <button
            type="button"
            className="close-action"
            disabled={!idle || isDirty}
            onClick={() => setConfirming(true)}
          >
            Close ledger…
          </button>
        )}
        <GuardedLink to="/current-ledger" className="button-link">
          Back to Current Ledger
        </GuardedLink>
      </div>
      {verified && isDirty && (
        <p className="field-hint">Save or discard your unsaved changes before closing.</p>
      )}

      {confirming && verified && (
        <ConfirmDialog
          title="Close this ledger?"
          confirmLabel="Close ledger"
          onConfirm={performClose}
          onCancel={() => setConfirming(false)}
        >
          <dl className="confirm-figures">
            <div>
              <dt>Ledger</dt>
              <dd>{ledgerName}</dd>
            </div>
            <div>
              <dt>Period</dt>
              <dd>{periodLabel(verified.summary)}</dd>
            </div>
            {figureRows(verified.summary)
              .slice(0, 4)
              .map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
          </dl>
          <p>{READ_ONLY_NOTE}</p>
          <p>{nextLedgerNote(verified.summary, today)}</p>
        </ConfirmDialog>
      )}
    </div>
  )
}

// A ledger that starts after today has nothing to close yet.
function NotStartedPanel({ closingWindow }) {
  return (
    <div className="verify-close">
      <div className="empty-state">
        <h2>Nothing to close yet</h2>
        <p>{notStartedMessage(closingWindow.startDate)}</p>
        <GuardedLink to="/current-ledger" className="button-link">
          Back to Current Ledger
        </GuardedLink>
      </div>
    </div>
  )
}

function ClosedPanel({ closed, today }) {
  const { summary, nextLedger } = closed
  return (
    <div className="verify-close">
      <div className="closed-panel" role="status">
        <h2>Ledger closed</h2>
        <p>
          The ledger for {periodLabel(summary)} is now a read-only record. Its closing balance is{' '}
          {formatCurrency(summary.closingBalance)}.
        </p>
        <p>{newLedgerNote(nextLedger, today)}</p>
        <div className="verify-actions">
          <GuardedLink to="/current-ledger" className="button-link">
            Go to Current Ledger
          </GuardedLink>
          <GuardedLink to="/" className="button-link">
            Home
          </GuardedLink>
          <GuardedLink to="/saved-ledgers" className="button-link">
            Saved Ledgers
          </GuardedLink>
        </div>
      </div>
    </div>
  )
}
