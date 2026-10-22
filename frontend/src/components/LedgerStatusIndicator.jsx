import { formatDate } from '../format'
import { useLedger } from '../state/LedgerContext'

export default function LedgerStatusIndicator() {
  const { ledger } = useLedger()
  if (!ledger) return null

  const name = ledger.ledgerName || `Ledger #${ledger.ledgerId}`
  const status = ledger.status === 'ACTIVE' ? 'Active' : 'Closed'

  return (
    <p className="ledger-status">
      <strong>{name}</strong> · {status} · from {formatDate(ledger.startDate)}
    </p>
  )
}