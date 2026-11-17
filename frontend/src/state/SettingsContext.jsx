import { createContext, Fragment, useCallback, useContext, useMemo, useState } from 'react'
import { setDateFormat } from '../format'
import { loadSettings, saveSettings } from '../settings/logic'

const SettingsContext = createContext(null)

function browserStorage() {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function SettingsProvider({ children }) {
  const [settings, setSettings] = useState(() => {
    const loaded = loadSettings(browserStorage())
    setDateFormat(loaded.dateFormat) // before the first screen draws
    return loaded
  })
  const [kept, setKept] = useState(true)

  const update = useCallback(
    (changes) => {
      const next = { ...settings, ...changes }
      setDateFormat(next.dateFormat)
      setKept(saveSettings(browserStorage(), next))
      setSettings(next)
    },
    [settings]
  )

  const value = useMemo(() => ({ settings, update, kept }), [settings, update, kept])
  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>
}

// Dates are drawn while a screen renders, so a new format needs everything below to draw again.
// Putting it around the routes (not around the ledger) leaves the loaded ledger alone.
export function SettingsRedraw({ children }) {
  const { settings } = useSettings()
  return <Fragment key={settings.dateFormat}>{children}</Fragment>
}

export function useSettings() {
  const context = useContext(SettingsContext)
  if (!context) throw new Error('useSettings must be used inside SettingsProvider')
  return context
}
