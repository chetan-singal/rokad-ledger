import { useRef, useState } from 'react'
import { inspectBackup, restoreBackup } from '../api/client'
import {
  FILE_UNREADABLE,
  currentDataText,
  describeBackup,
  replaceWarning,
  restoreErrorMessage,
  restoredText
} from '../backup/logic'
import { useLedger } from '../state/LedgerContext'
import ConfirmDialog from './ConfirmDialog'

// Restore from a backup file (SRS 11.2) in three stages the person can see and stop at: choose a
// file, read what it holds next to what ROKAD holds now, then confirm. The file travels as text,
// untouched; the backend checks all of it before anything is replaced, and a restore either
// replaces everything or changes nothing.
export default function RestorePanel() {
  const { refresh } = useLedger()
  const fileText = useRef('')
  const input = useRef(null)
  const [stage, setStage] = useState('choose') // choose | checking | review | restoring | done
  const [summary, setSummary] = useState(null)
  const [fileName, setFileName] = useState('')
  const [problem, setProblem] = useState('')
  const [confirming, setConfirming] = useState(false)

  function clearFile() {
    fileText.current = ''
    if (input.current) input.current.value = ''
  }

  function startOver() {
    clearFile()
    setStage('choose')
    setSummary(null)
    setFileName('')
    setProblem('')
    setConfirming(false)
  }

  async function handleFile(event) {
    const file = event.target.files[0]
    if (!file) return
    setProblem('')
    setFileName(file.name)
    setStage('checking')
    let text
    try {
      text = await file.text()
    } catch {
      clearFile()
      setProblem(FILE_UNREADABLE)
      setStage('choose')
      return
    }
    try {
      const inspected = await inspectBackup(text)
      fileText.current = text
      setSummary(inspected)
      setStage('review')
    } catch (error) {
      clearFile()
      setProblem(restoreErrorMessage(error))
      setStage('choose')
    }
  }

  async function performRestore() {
    setConfirming(false)
    setProblem('')
    setStage('restoring')
    try {
      const restored = await restoreBackup(fileText.current)
      clearFile()
      await refresh()
      setSummary(restored)
      setStage('done')
    } catch (error) {
      setProblem(restoreErrorMessage(error))
      setStage('review')
      await refresh() // show what is really there, whatever happened
    }
  }

  const busy = stage === 'checking' || stage === 'restoring'
  return (
    <div className="restore-panel">
      {stage !== 'done' && (
        <div className="field">
          <label htmlFor="restore-file">Backup file</label>
          <p className="field-hint" id="restore-file-hint">
            A file made by Back up now. It ends in .json.
          </p>
          <input
            id="restore-file"
            ref={input}
            type="file"
            accept=".json,application/json"
            aria-describedby="restore-file-hint"
            onChange={handleFile}
            disabled={busy}
          />
        </div>
      )}
      {problem && (
        <p role="alert" className="field-error">
          {problem}
        </p>
      )}
      {stage === 'checking' && <p role="status">Reading {fileName}…</p>}

      {(stage === 'review' || stage === 'restoring') && summary && (
        <section className="restore-review" aria-labelledby="restore-review-heading">
          <h3 id="restore-review-heading">{fileName}</h3>
          {describeBackup(summary).map((line) => (
            <p key={line}>{line}</p>
          ))}
          <p>
            <strong>{currentDataText(summary)}</strong>
          </p>
          <p className="notice">{replaceWarning()}</p>
          <div className="restore-actions">
            <button
              type="button"
              className="close-action"
              onClick={() => setConfirming(true)}
              disabled={busy}
            >
              Restore this backup…
            </button>
            <button type="button" onClick={startOver} disabled={busy}>
              Choose a different file
            </button>
          </div>
        </section>
      )}
      {stage === 'restoring' && <p role="status">Restoring…</p>}

      {stage === 'done' && summary && (
        <div className="restore-done">
          <p role="status">{restoredText(summary)}</p>
          <button type="button" onClick={startOver}>
            Restore another backup
          </button>
        </div>
      )}

      {confirming && (
        <ConfirmDialog
          title="Replace all data with this backup?"
          confirmLabel="Replace my data"
          onConfirm={performRestore}
          onCancel={() => setConfirming(false)}
        >
          <p>{replaceWarning()}</p>
          {summary && <p>{currentDataText(summary)}</p>}
        </ConfirmDialog>
      )}
    </div>
  )
}
