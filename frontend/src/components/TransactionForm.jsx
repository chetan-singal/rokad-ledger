import { useCallback, useEffect, useRef, useState } from 'react'
import { addTransaction, editTransaction } from '../api/client'
import { todayIso } from '../format'
import { useUnsavedChanges } from '../state/UnsavedChangesContext'
import {
  changesAmountTypeOrDate,
  describeTransaction,
  emptyForm,
  formFromTransaction,
  toRequest,
  validateTransaction
} from '../transactions/logic'
import ConfirmDialog from './ConfirmDialog'
import Field from './Field'

const FORM_KEY = 'transaction-form'
const FIELD_ORDER = ['transactionType', 'transactionDate', 'amount', 'reason']
// Field name (client or server) -> the input to highlight and focus.
const FIELD_IDS = {
  transactionType: 'txn-type-debit',
  transactionDate: 'txn-date',
  amount: 'txn-amount',
  reason: 'txn-reason'
}

export default function TransactionForm({
  mode,
  transaction,
  ledgerStartDate,
  onSaved,
  onCancel
}) {
  const { setDirty } = useUnsavedChanges()
  const initial = useRef(
    mode === 'edit' ? formFromTransaction(transaction) : emptyForm(todayIso())
  )
  const [values, setValues] = useState(initial.current)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const inFlight = useRef(false)
  const valuesRef = useRef(values)
  valuesRef.current = values
  const commitRef = useRef(null)

  const isDirty = FIELD_ORDER.some((field) => values[field] !== initial.current[field])

  // Register with the unsaved-changes registry (Exit and in-app navigation consult it).
  // `save` is stable and always runs the latest commit(), so "Save and exit" submits
  // whatever is in the form right now.
  const save = useCallback(() => commitRef.current(), [])
  useEffect(() => {
    setDirty(FORM_KEY, isDirty, save)
    return () => setDirty(FORM_KEY, false)
  }, [isDirty, save, setDirty])

  useEffect(() => {
    document.getElementById(FIELD_IDS.transactionType)?.focus()
  }, [])

  const update = (field) => (event) => {
    const { value } = event.target
    setValues((prev) => ({ ...prev, [field]: value }))
    setErrors((prev) => (prev[field] ? { ...prev, [field]: undefined } : prev))
  }

  function validateAndShow() {
    const found = validateTransaction(valuesRef.current, {
      ledgerStartDate,
      today: todayIso()
    })
    setErrors(found)
    setFormError(null)
    const firstInvalid = FIELD_ORDER.find((field) => found[field])
    if (firstInvalid) {
      document.getElementById(FIELD_IDS[firstInvalid])?.focus()
      return false
    }
    return true
  }

  // A server rejection still ties to its field when it names one the form has; anything
  // else (a duplicate, a lost connection) is a form-level message. Either way every value
  // the user typed stays exactly where it was (SRS 16).
  function showServerError(error) {
    const id = FIELD_IDS[error.field]
    if (id) {
      setErrors({ [error.field]: error.message })
      document.getElementById(id)?.focus()
    } else {
      setFormError(error.message)
    }
  }

  function resetForNextEntry() {
    initial.current = emptyForm(todayIso())
    setValues(initial.current)
    setErrors({})
    setFormError(null)
    document.getElementById(FIELD_IDS.transactionType)?.focus()
  }

  // Resolves true only if the transaction was saved.
  async function commit() {
    if (inFlight.current) return false
    if (!validateAndShow()) return false

    inFlight.current = true
    setSubmitting(true)
    let saved
    try {
      const body = toRequest(valuesRef.current)
      saved =
        mode === 'edit'
          ? await editTransaction(transaction.transactionId, body)
          : await addTransaction(body)
    } catch (error) {
      showServerError(error)
      return false
    } finally {
      inFlight.current = false
      setSubmitting(false)
    }

    // The save has happened. Nothing below may report failure: a failed reload is the
    // page's problem to show, and treating it as a failed save would invite a retry
    // that creates the entry twice.
    if (mode === 'add') resetForNextEntry()
    await onSaved(saved, mode)
    return true
  }
  commitRef.current = commit

  function handleSubmit(event) {
    event.preventDefault()
    if (inFlight.current || !validateAndShow()) return
    if (mode === 'edit' && changesAmountTypeOrDate(transaction, valuesRef.current)) {
      setConfirming(true) // FR-004
      return
    }
    commit()
  }

  return (
    <>
      <form
        className="txn-form"
        onSubmit={handleSubmit}
        noValidate
        aria-labelledby="txn-form-title"
      >
        <h2 id="txn-form-title">{mode === 'edit' ? 'Edit transaction' : 'Add transaction'}</h2>

        <fieldset
          className="field"
          aria-describedby={errors.transactionType ? 'txn-type-error' : undefined}
        >
          <legend>Transaction type</legend>
          <label className="choice">
            <input
              id="txn-type-debit"
              type="radio"
              name="transactionType"
              value="DEBIT"
              checked={values.transactionType === 'DEBIT'}
              onChange={update('transactionType')}
              aria-invalid={Boolean(errors.transactionType)}
            />
            Debit
          </label>
          <label className="choice">
            <input
              type="radio"
              name="transactionType"
              value="CREDIT"
              checked={values.transactionType === 'CREDIT'}
              onChange={update('transactionType')}
              aria-invalid={Boolean(errors.transactionType)}
            />
            Credit
          </label>
          {errors.transactionType && (
            <p id="txn-type-error" className="field-error">
              {errors.transactionType}
            </p>
          )}
        </fieldset>

        <Field id="txn-date" label="Date" error={errors.transactionDate}>
          {(props) => (
            <input
              {...props}
              type="date"
              min={ledgerStartDate}
              max={todayIso()}
              value={values.transactionDate}
              onChange={update('transactionDate')}
            />
          )}
        </Field>

        <Field
          id="txn-amount"
          label="Amount (₹)"
          hint="Greater than zero, for example 1500.50"
          error={errors.amount}
        >
          {(props) => (
            <input
              {...props}
              type="text"
              inputMode="decimal"
              autoComplete="off"
              value={values.amount}
              onChange={update('amount')}
            />
          )}
        </Field>

        <Field id="txn-reason" label="Reason" error={errors.reason}>
          {(props) => (
            <input
              {...props}
              type="text"
              maxLength={500}
              autoComplete="off"
              value={values.reason}
              onChange={update('reason')}
            />
          )}
        </Field>

        {formError && (
          <p role="alert" className="form-error">
            {formError}
          </p>
        )}
        <div className="form-actions">
          <button type="submit" disabled={submitting}>
            {mode === 'edit' ? 'Save changes' : 'Save'}
          </button>
          <button type="button" onClick={onCancel}>
            Cancel
          </button>
        </div>
      </form>

      {confirming && (
        <ConfirmDialog
          title="Change this transaction?"
          confirmLabel="Save change"
          onConfirm={() => {
            setConfirming(false)
            commit()
          }}
          onCancel={() => setConfirming(false)}
        >
          <p>Currently: {describeTransaction(transaction)}</p>
          <p>Changing to: {describeTransaction(toRequest(values))}</p>
        </ConfirmDialog>
      )}
    </>
  )
}
