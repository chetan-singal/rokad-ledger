import { useMemo, useRef, useState } from 'react'
import { deleteTransaction } from '../api/client'
import ConfirmDialog from '../components/ConfirmDialog'
import FilterBar from '../components/FilterBar'
import GuardedLink from '../components/GuardedLink'
import LedgerSummaryStrip from '../components/LedgerSummaryStrip'
import TransactionForm from '../components/TransactionForm'
import TransactionTable from '../components/TransactionTable'
import { formatCurrency } from '../money'
import { useLedger } from '../state/LedgerContext'
import { useUnsavedChanges } from '../state/UnsavedChangesContext'
import {
  NO_FILTERS,
  describeTransaction,
  filterTransactions,
  formatTotal,
  isFilterActive,
  paginate,
  sortChronologically,
  splitBySide,
  typeLabel
} from '../transactions/logic'
import { useTransactions } from '../transactions/useTransactions'

export default function CurrentLedger() {
  const { ledger, refresh, error: ledgerError } = useLedger()
  const { status, transactions, error: listError, reload } = useTransactions()
  const { isDirty } = useUnsavedChanges()

  // null, { kind: 'add' }, or { kind: 'edit', transaction }
  const [formMode, setFormMode] = useState(null)
  const [filters, setFilters] = useState(NO_FILTERS)
  const [pages, setPages] = useState({ debit: 1, credit: 1 })
  const [confirmation, setConfirmation] = useState(null)
  const [message, setMessage] = useState('')
  const [actionError, setActionError] = useState(null)
  const addButton = useRef(null)
  const debitHeading = useRef(null)
  const creditHeading = useRef(null)

  // Sort once per load; filtering only ever removes rows, so order survives every keystroke.
  const ordered = useMemo(() => sortChronologically(transactions), [transactions])
  const visible = useMemo(() => filterTransactions(ordered, filters), [ordered, filters])
  const sides = useMemo(() => splitBySide(visible), [visible])
  const filtersActive = isFilterActive(filters)

  const debitPage = paginate(sides.debit, pages.debit)
  const creditPage = paginate(sides.credit, pages.credit)

  // Unfiltered, the totals under the tables are the backend's own (every screen reads the
  // same engine, FR-006). Filtered, they cover only the displayed entries (SRS 5.6).
  const totals = filtersActive
    ? { debit: formatTotal(sides.debit), credit: formatTotal(sides.credit) }
    : { debit: formatCurrency(ledger.totalDebit), credit: formatCurrency(ledger.totalCredit) }

  const bannerMessage = actionError || listError?.message || ledgerError?.message

  function changeFilters(next) {
    setFilters(next)
    setPages({ debit: 1, credit: 1 })
  }

  // Replacing the form while it holds unsaved input needs an explicit say-so.
  function guardForm(proceed) {
    if (!isDirty) {
      proceed()
      return
    }
    setConfirmation({
      title: 'Discard the changes in the form?',
      body: 'The form has unsaved changes that will be lost.',
      confirmLabel: 'Discard changes',
      danger: true,
      onConfirm: proceed
    })
  }

  function closeForm() {
    setFormMode(null)
    addButton.current?.focus()
  }

  async function reloadAll() {
    await Promise.all([reload(), refresh()])
  }

  async function handleReload() {
    setActionError(null)
    await reloadAll()
  }

  async function handleSaved(saved, mode) {
    setActionError(null)
    if (mode === 'add') {
      const side = saved.transactionType === 'DEBIT' ? 'debit' : 'credit'
      setPages((prev) => ({ ...prev, [side]: Number.POSITIVE_INFINITY })) // jump to the new entry
    }
    await reloadAll()

    let text = `${typeLabel(saved.transactionType)} of ${formatCurrency(saved.amount)} ${
      mode === 'edit' ? 'updated' : 'saved'
    }.`
    if (filtersActive && filterTransactions([saved], filters).length === 0) {
      text += ' It is hidden by the current filters.'
    }
    setMessage(text)
    if (mode === 'edit') closeForm()
  }

  function requestDelete(transaction) {
    setConfirmation({
      title: 'Delete this transaction?',
      body: `${describeTransaction(transaction)} will be removed and the totals recalculated. This cannot be undone.`,
      confirmLabel: 'Delete',
      danger: true,
      onConfirm: () => performDelete(transaction)
    })
  }

  async function performDelete(transaction) {
    try {
      await deleteTransaction(transaction.transactionId)
    } catch (error) {
      setActionError(error.message)
      await reload() // e.g. it was already gone: show the list as it really is
      return
    }
    setActionError(null)
    const beingEdited = formMode?.kind === 'edit' ? formMode.transaction.transactionId : null
    if (beingEdited === transaction.transactionId) setFormMode(null)
    await reloadAll()
    setMessage(`${describeTransaction(transaction)} deleted.`)
    const heading = transaction.transactionType === 'DEBIT' ? debitHeading : creditHeading
    heading.current?.focus()
  }

  return (
    <div className="current-ledger">
      <LedgerSummaryStrip ledger={ledger} />

      <div className="toolbar">
        <button
          type="button"
          ref={addButton}
          aria-expanded={formMode?.kind === 'add'}
          onClick={() => guardForm(() => setFormMode({ kind: 'add' }))}
        >
          Add transaction
        </button>
        <GuardedLink to="/verify-close" className="button-link">
          Verify &amp; Close Ledger
        </GuardedLink>
      </div>

      <p role="status" className="status-message">
        {message}
      </p>
      {bannerMessage && (
        <div role="alert" className="banner">
          <span>{bannerMessage}</span>
          <button type="button" onClick={handleReload}>
            Reload
          </button>
        </div>
      )}

      {formMode && (
        <TransactionForm
          key={formMode.kind === 'edit' ? `edit-${formMode.transaction.transactionId}` : 'add'}
          mode={formMode.kind}
          transaction={formMode.transaction}
          ledgerStartDate={ledger.startDate}
          onSaved={handleSaved}
          onCancel={closeForm}
        />
      )}

      <FilterBar
        filters={filters}
        active={filtersActive}
        onChange={changeFilters}
        onClear={() => changeFilters(NO_FILTERS)}
      />
      {filtersActive && (
        <p role="status" className="filter-note">
          Filtered view: showing {visible.length} of {transactions.length} transactions. The totals
          under the tables cover only these entries; the summary above still shows the whole ledger.
        </p>
      )}

      {status === 'loading' && <p role="status">Loading transactions…</p>}
      {status === 'ready' && (
        <div className="ledger-sides">
          <TransactionTable
            id="debit"
            title="Debit"
            page={debitPage}
            total={totals.debit}
            headingRef={debitHeading}
            emptyMessage={
              filters.type === 'CREDIT'
                ? 'Hidden by the Type filter.'
                : filtersActive
                  ? 'No debit entries match the filters.'
                  : 'No debit entries yet.'
            }
            onPageChange={(page) => setPages((prev) => ({ ...prev, debit: page }))}
            onEdit={(t) => guardForm(() => setFormMode({ kind: 'edit', transaction: t }))}
            onDelete={requestDelete}
          />
          <TransactionTable
            id="credit"
            title="Credit"
            page={creditPage}
            total={totals.credit}
            headingRef={creditHeading}
            emptyMessage={
              filters.type === 'DEBIT'
                ? 'Hidden by the Type filter.'
                : filtersActive
                  ? 'No credit entries match the filters.'
                  : 'No credit entries yet.'
            }
            onPageChange={(page) => setPages((prev) => ({ ...prev, credit: page }))}
            onEdit={(t) => guardForm(() => setFormMode({ kind: 'edit', transaction: t }))}
            onDelete={requestDelete}
          />
        </div>
      )}

      {confirmation && (
        <ConfirmDialog
          title={confirmation.title}
          confirmLabel={confirmation.confirmLabel}
          danger={confirmation.danger}
          onConfirm={() => {
            setConfirmation(null)
            confirmation.onConfirm()
          }}
          onCancel={() => setConfirmation(null)}
        >
          <p>{confirmation.body}</p>
        </ConfirmDialog>
      )}
    </div>
  )
}
