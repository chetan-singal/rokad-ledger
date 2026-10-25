import { useCallback, useEffect, useRef, useState } from 'react'
import { listTransactions } from '../api/client'

// Loads the active ledger's transactions. reload() never throws: a failed reload keeps the
// list already on screen and reports the error, so a save that succeeded can never be
// mistaken for one that failed.
export function useTransactions() {
  const [state, setState] = useState({ status: 'loading', transactions: [], error: null })
  const latest = useRef(0)

  const reload = useCallback(async () => {
    const request = ++latest.current
    try {
      const transactions = await listTransactions()
      if (request === latest.current) setState({ status: 'ready', transactions, error: null })
    } catch (error) {
      if (request !== latest.current) return
      setState((prev) =>
        prev.status === 'ready'
          ? { ...prev, error }
          : { status: 'error', transactions: [], error }
      )
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  return { ...state, reload }
}