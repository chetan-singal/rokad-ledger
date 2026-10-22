import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { getActiveLedger } from '../api/client'

const LedgerContext = createContext(null)

// status: 'loading' (first fetch only) | 'ready' | 'none' (no active ledger yet) | 'error'
export function LedgerProvider({ children }) {
  const [state, setState] = useState({ status: 'loading', ledger: null, error: null })

  const refresh = useCallback(async () => {
    try {
      const ledger = await getActiveLedger()
      setState({ status: ledger ? 'ready' : 'none', ledger, error: null })
    } catch (error) {
      // A failed refresh after the app is up keeps the last known ledger on screen:
      // only a failed first load replaces the whole UI with the error screen.
      setState((prev) =>
        prev.status === 'ready' ? { ...prev, error } : { status: 'error', ledger: null, error }
      )
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const value = useMemo(() => ({ ...state, refresh }), [state, refresh])
  return <LedgerContext.Provider value={value}>{children}</LedgerContext.Provider>
}

export function useLedger() {
  const context = useContext(LedgerContext)
  if (!context) throw new Error('useLedger must be used inside LedgerProvider')
  return context
}