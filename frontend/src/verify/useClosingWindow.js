import { useCallback, useEffect, useRef, useState } from 'react'
import { getClosingWindow } from '../api/client'

// Loads the range of end dates the active ledger can be closed on. Same shape as useOverview:
// reload() never throws, only the newest request may update the screen, a failed reload keeps
// what is already shown, and the window reloads when the app regains focus so "today" rolls
// over at midnight without anyone navigating.
export function useClosingWindow() {
  const [state, setState] = useState({ status: 'loading', closingWindow: null, error: null })
  const latest = useRef(0)

  const reload = useCallback(async () => {
    const request = ++latest.current
    try {
      const closingWindow = await getClosingWindow()
      if (request === latest.current) setState({ status: 'ready', closingWindow, error: null })
    } catch (error) {
      if (request !== latest.current) return
      setState((prev) =>
        prev.status === 'ready'
          ? { ...prev, error }
          : { status: 'error', closingWindow: null, error }
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
