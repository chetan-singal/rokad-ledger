import { useEffect, useId, useRef } from 'react'

export default function ConfirmDialog({
  title,
  children,
  confirmLabel,
  danger = false,
  onConfirm,
  onCancel
}) {
  const dialogRef = useRef(null)
  const cancelRef = useRef(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    dialog.showModal()
    cancelRef.current.focus() // the safe choice is the one under the cursor
    return () => dialog.close()
  }, [])

  return (
    <dialog
      ref={dialogRef}
      className="confirm-dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault() // Esc behaves exactly like the Cancel button
        onCancel()
      }}
    >
      <h2 id={titleId}>{title}</h2>
      <div>{children}</div>
      <div className="dialog-actions">
        <button type="button" className={danger ? 'danger' : undefined} onClick={onConfirm}>
          {confirmLabel}
        </button>
        <button type="button" ref={cancelRef} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </dialog>
  )
}