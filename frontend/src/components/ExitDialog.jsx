import { useEffect, useRef } from 'react'

export default function ExitDialog({ error, onSave, onDiscard, onCancel }) {
  const dialogRef = useRef(null)
  const cancelRef = useRef(null)

  useEffect(() => {
    const dialog = dialogRef.current
    dialog.showModal()
    cancelRef.current.focus() // the safe choice is the one under the cursor
    return () => dialog.close()
  }, [])

  return (
    <dialog
      ref={dialogRef}
      className="exit-dialog"
      aria-labelledby="exit-title"
      aria-describedby="exit-description"
      onCancel={(event) => {
        event.preventDefault() // Esc: let state decide, same as the Cancel button
        onCancel()
      }}
    >
      <h2 id="exit-title">Unsaved changes</h2>
      <p id="exit-description">
        A form still has changes that have not been saved. Save them, discard them, or cancel and
        keep working. Your ledger stays active either way; exiting never closes it.
      </p>
      {error && <p role="alert">{error}</p>}
      <div className="dialog-actions">
        <button type="button" onClick={onSave}>
          Save and exit
        </button>
        <button type="button" onClick={onDiscard}>
          Discard and exit
        </button>
        <button type="button" ref={cancelRef} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </dialog>
  )
}