import { useOverview } from '../overview/useOverview'

// Shared by Home and Dashboard: loads the overview, owns the loading and error states, and
// hands the figures to whatever the screen shows. Neither screen ever enters a transaction.
export default function OverviewFrame({ children }) {
  const { status, overview, error, reload } = useOverview()

  if (status === 'loading') return <p role="status">Loading…</p>

  if (status === 'error') {
    return (
      <div role="alert" className="banner">
        <span>{error.message}</span>
        <button type="button" onClick={reload}>
          Try again
        </button>
      </div>
    )
  }

  return (
    <>
      {error && (
        <div role="alert" className="banner">
          <span>{error.message}</span>
          <button type="button" onClick={reload}>
            Reload
          </button>
        </div>
      )}
      {children(overview)}
    </>
  )
}