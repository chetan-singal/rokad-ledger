import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'

const UnsavedChangesContext = createContext(null)

// Any form with unsaved input registers itself here: setDirty(key, true, saveFn).
// The Exit flow asks this registry whether to prompt, and calls saveFn on "Save".
export function UnsavedChangesProvider({ children }) {
  const savers = useRef(new Map())
  const [dirtyCount, setDirtyCount] = useState(0)

  const setDirty = useCallback((key, dirty, save = null) => {
    if (dirty) savers.current.set(key, save)
    else savers.current.delete(key)
    setDirtyCount(savers.current.size)
  }, [])

  // Resolves false if any form reports it could not save; the exit is then abandoned.
  const saveAll = useCallback(async () => {
    for (const save of [...savers.current.values()]) {
      if (save && (await save()) === false) return false
    }
    return true
  }, [])

  const value = useMemo(
    () => ({ isDirty: dirtyCount > 0, setDirty, saveAll }),
    [dirtyCount, setDirty, saveAll]
  )
  return <UnsavedChangesContext.Provider value={value}>{children}</UnsavedChangesContext.Provider>
}

export function useUnsavedChanges() {
  const context = useContext(UnsavedChangesContext)
  if (!context) throw new Error('useUnsavedChanges must be used inside UnsavedChangesProvider')
  return context
}