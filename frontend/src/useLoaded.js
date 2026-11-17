import { useCallback, useEffect, useRef, useState } from 'react'

// Loads something once and again on request. Same rules as the other loaders: reload() never
// throws, only the newest request may update the screen, and a failed reload keeps what is
// already shown. `load` must keep its identity between renders (a function defined outside the
// component, or wrapped in useCallback): a new `load` starts a fresh load.
export function useLoaded(load) {
  const [state, setState] = useState({ status: 'loading', data: null, error: null })
  const latest = useRef(0)

  const reload = useCallback(async () => {
    const request = ++latest.current
    try {
      const data = await load()
      if (request === latest.current) setState({ status: 'ready', data, error: null })
    } catch (error) {
      if (request !== latest.current) return
      setState((prev) =>
        prev.status === 'ready' ? { ...prev, error } : { status: 'error', data: null, error }
      )
    }
  }, [load])

  useEffect(() => {
    setState({ status: 'loading', data: null, error: null })
    reload()
  }, [reload])

  return { ...state, reload }
}
