import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import ExitDialog from '../components/ExitDialog'
import { useUnsavedChanges } from './UnsavedChangesContext'

const ExitContext = createContext(null)

// Closing never touches the ledger: it stays Active until Verify & Close completes (FR-012).
function finishExit() {
  if (window.rokad) window.rokad.confirmClose()
  else window.close()
}

export function ExitProvider({ children }) {
  const { isDirty, saveAll } = useUnsavedChanges()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [saveError, setSaveError] = useState(null)

  const requestExit = useCallback(() => {
    if (isDirty) {
      setSaveError(null)
      setDialogOpen(true)
    } else {
      finishExit()
    }
  }, [isDirty])

  // The window's own close button and Alt+F4 arrive from the Electron main process;
  // optional chaining keeps this a no-op in a plain browser tab.
  useEffect(() => window.rokad?.onCloseRequested(requestExit), [requestExit])

  const handleSave = useCallback(async () => {
    try {
      if (await saveAll()) finishExit()
      else setSaveError('Some changes could not be saved, so ROKAD has not closed.')
    } catch (error) {
      setSaveError(error.message)
    }
  }, [saveAll])

  const handleCancel = useCallback(() => setDialogOpen(false), [])

  const value = useMemo(() => ({ requestExit }), [requestExit])
  return (
    <ExitContext.Provider value={value}>
      {children}
      {dialogOpen && (
        <ExitDialog
          error={saveError}
          onSave={handleSave}
          onDiscard={finishExit}
          onCancel={handleCancel}
        />
      )}
    </ExitContext.Provider>
  )
}

export function useExit() {
  const context = useContext(ExitContext)
  if (!context) throw new Error('useExit must be used inside ExitProvider')
  return context
}