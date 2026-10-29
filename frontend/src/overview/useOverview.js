import { useCallback, useEffect, useRef, useState } from 'react'
import { getOverview } from '../api/client'

// Loads the active ledger's overview: the summary, today's totals and the recent entries.
// Same shape as useTransactions: reload() never throws, only the newest request may update
// the screen, and a failed reload keeps the figures already shown and adds the error.
// It also reloads whenever the window regains focus, so the figures cannot go stale while
// the app sits open, and "today" rolls over at midnight without anyone navigating.
export function useOverview() {
  const [state, setState] = useState({ status: 'loading', overview: null, error: null })
  const latest = useRef(0)

  const reload = useCallback(async () => {
    const request = ++latest.current
    try {
      const overview = await getOverview()
      if (request === latest.current) setState({ status: 'ready', overview, error: null })
    } catch (error) {
      if (request !== latest.current) return
      setState((prev) =>
        prev.status === 'ready' ? { ...prev, error } : { status: 'error', overview: null, error }
      )
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  useEffect(() => {
    window.addEventListener('focus', reload)
    return () => window.removeEventListener('focus', reload)
  }, [reload])

  return { ...state, reload }
}