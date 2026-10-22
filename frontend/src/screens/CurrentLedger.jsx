import { useEffect, useState } from 'react'
import PlaceholderScreen from '../components/PlaceholderScreen'
import { useUnsavedChanges } from '../state/UnsavedChangesContext'

export default function CurrentLedger() {
  // TEMPORARY (removed in Phase 10): stands in for a transaction form with unsaved
  // input, so the Exit prompt can be exercised before any real form exists.
  const [simulateUnsaved, setSimulateUnsaved] = useState(false)
  const { setDirty } = useUnsavedChanges()

  useEffect(() => {
    setDirty('placeholder-form', simulateUnsaved, async () => true)
    return () => setDirty('placeholder-form', false)
  }, [simulateUnsaved, setDirty])

  return (
    <>
      <PlaceholderScreen />
      <label>
        <input
          type="checkbox"
          checked={simulateUnsaved}
          onChange={(event) => setSimulateUnsaved(event.target.checked)}
        />{' '}
        Simulate unsaved changes (temporary, for testing Exit)
      </label>
    </>
  )
}