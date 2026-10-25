import { useState } from 'react'
import Field from '../components/Field'
import { setupLedger } from '../api/client'
import { todayIso } from '../format'
import { useLedger } from '../state/LedgerContext'

// Matches DECIMAL(15,2): up to 13 digits, at most two decimals, never negative.
const AMOUNT_PATTERN = /^\d{1,13}(\.\d{1,2})?$/

function validate({ startDate, openingBalance }) {
  const errors = {}
  if (!startDate) errors.startDate = 'Start date is required.'
  const amount = openingBalance.trim()
  if (!amount) {
    errors.openingBalance = 'Opening balance is required. Enter 0 to start from zero.'
  } else if (!AMOUNT_PATTERN.test(amount)) {
    errors.openingBalance = 'Enter an amount of zero or more, with at most two decimal places.'
  }
  return errors
}

export default function SetupScreen() {
  const { refresh } = useLedger()
  const [values, setValues] = useState({
    ledgerName: '',
    startDate: todayIso(),
    openingBalance: ''
  })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const update = (field) => (event) =>
    setValues((prev) => ({ ...prev, [field]: event.target.value }))

  async function handleSubmit(event) {
    event.preventDefault()
    const found = validate(values)
    setErrors(found)
    setFormError(null)

    const firstInvalid = ['startDate', 'openingBalance'].find((field) => found[field])
    if (firstInvalid) {
      document.getElementById(`setup-${firstInvalid}`).focus()
      return
    }

    setSubmitting(true)
    try {
      // The amount goes out as the string the user typed; the backend parses it straight
      // into a BigDecimal, so no floating-point number ever touches a money value.
      await setupLedger({
        ledgerName: values.ledgerName.trim() || null,
        startDate: values.startDate,
        openingBalance: values.openingBalance.trim()
      })
      await refresh() // the gate now sees an active ledger and swaps this screen out
    } catch (error) {
      setFormError(error.message)
      setSubmitting(false)
    }
  }

  return (
    <main className="setup">
      <h1>Set up your first ledger</h1>
      <p>ROKAD keeps one active ledger at a time. Tell it where this one starts.</p>

      <form onSubmit={handleSubmit} noValidate>
        <Field id="setup-ledgerName" label="Ledger name (optional)">
          {(props) => (
            <input
              {...props}
              type="text"
              value={values.ledgerName}
              onChange={update('ledgerName')}
              autoComplete="off"
            />
          )}
        </Field>

        <Field id="setup-startDate" label="Start date" error={errors.startDate}>
          {(props) => (
            <input {...props} type="date" value={values.startDate} onChange={update('startDate')} />
          )}
        </Field>

        <Field
          id="setup-openingBalance"
          label="Opening balance (₹)"
          hint="Zero is allowed. Example: 50000.00"
          error={errors.openingBalance}
        >
          {(props) => (
            <input
              {...props}
              type="text"
              inputMode="decimal"
              value={values.openingBalance}
              onChange={update('openingBalance')}
              autoComplete="off"
            />
          )}
        </Field>

        {formError && <p role="alert">{formError}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create ledger'}
        </button>
      </form>
    </main>
  )
}