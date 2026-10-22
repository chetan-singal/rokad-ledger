import SetupScreen from '../screens/SetupScreen'
import { useLedger } from '../state/LedgerContext'

// Nothing in the app works without an active ledger, so until one is confirmed
// the routed screens are never rendered.
export default function LedgerGate({ children }) {
  const { status, error, refresh } = useLedger()

  if (status === 'loading') {
    return (
      <p className="startup-message" role="status">
        Loading ROKAD…
      </p>
    )
  }
  if (status === 'error') {
    return (
      <div className="startup-message" role="alert">
        <h1>Can&apos;t reach the ROKAD backend</h1>
        <p>{error.message}</p>
        <button type="button" onClick={refresh}>
          Try again
        </button>
      </div>
    )
  }
  if (status === 'none') return <SetupScreen />
  return children
}