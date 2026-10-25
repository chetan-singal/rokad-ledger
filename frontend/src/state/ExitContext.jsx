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
  const [prompt, setPrompt] = useState(null) // { intent: 'exit' | 'leave', proceed }
  const [saveError, setSaveError] = useState(null)

  // Runs `proceed` straight away when nothing is unsaved; otherwise asks first.
  const guard = useCallback(
    (intent, proceed) => {
      if (isDirty) {
        setSaveError(null)
        setPrompt({ intent, proceed })
      } else {
        proceed()
      }
    },
    [isDirty]
  )

  const requestExit = useCallback(() => guard('exit', finishExit), [guard])
  const guardNavigation = useCallback((proceed) => guard('leave', proceed), [guard])

  // The window's own close button and Alt+F4 arrive from the Electron main process;
  // optional chaining keeps this a no-op in a plain browser tab.
  useEffect(() => window.rokad?.onCloseRequested(requestExit), [requestExit])

  const handleSave = useCallback(async () => {
    try {
      if (await saveAll()) {
        const { proceed } = prompt
        setPrompt(null)
        proceed()
      } else {
        setSaveError(
          prompt.intent === 'exit'
            ? 'Some changes could not be saved, so ROKAD has not closed.'
            : 'Some changes could not be saved, so you are still on this screen.'
        )
      }
    } catch (error) {
      setSaveError(error.message)
    }
  }, [saveAll, prompt])

  const handleDiscard = useCallback(() => {
    const { proceed } = prompt
    setPrompt(null)
    proceed()
  }, [prompt])

  const handleCancel = useCallback(() => setPrompt(null), [])

  const value = useMemo(() => ({ requestExit, guardNavigation }), [requestExit, guardNavigation])
  return (
    <ExitContext.Provider value={value}>
      {children}
      {prompt && (
        <ExitDialog
          intent={prompt.intent}
          error={saveError}
          onSave={handleSave}
          onDiscard={handleDiscard}
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