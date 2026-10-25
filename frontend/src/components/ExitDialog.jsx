import { useEffect, useRef } from 'react'

const WORDING = {
  exit: {
    description:
      'A form still has changes that have not been saved. Save them, discard them, or cancel and keep working. Your ledger stays active either way; exiting never closes it.',
    save: 'Save and exit',
    discard: 'Discard and exit'
  },
  leave: {
    description:
      'The transaction form has changes that have not been saved. Save them before leaving this screen, discard them, or stay here and keep working.',
    save: 'Save and leave',
    discard: 'Discard and leave'
  }
}

export default function ExitDialog({ intent = 'exit', error, onSave, onDiscard, onCancel }) {
  const dialogRef = useRef(null)
  const cancelRef = useRef(null)
  const wording = WORDING[intent]

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
      <p id="exit-description">{wording.description}</p>
      {error && <p role="alert">{error}</p>}
      <div className="dialog-actions">
        <button type="button" onClick={onSave}>
          {wording.save}
        </button>
        <button type="button" onClick={onDiscard}>
          {wording.discard}
        </button>
        <button type="button" ref={cancelRef} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </dialog>
  )
}