import { useState } from 'react'
import { exportBackup } from '../api/client'
import { saveTextFile } from '../backup/download'
import { backupFileName, backupSavedMessage, lastBackupText } from '../backup/logic'
import RestorePanel from '../components/RestorePanel'
import { CURRENCY_NOTE, DISPLAY_NOTE, dateFormatOptions, storageRows } from '../settings/logic'
import { useAppInfo } from '../settings/useAppInfo'
import { useSettings } from '../state/SettingsContext'

// Basic preferences, backup and restore, and where the data lives (SRS 11). Settings never
// introduce accounting features or change how a balance is worked out.
export default function Settings() {
  const { settings, update, kept } = useSettings()
  const info = useAppInfo()
  const [backup, setBackup] = useState({ busy: false, message: '', problem: '' })
  const options = dateFormatOptions()

  async function handleBackup() {
    setBackup({ busy: true, message: '', problem: '' })
    try {
      const text = await exportBackup()
      const fileName = backupFileName()
      saveTextFile(text, fileName)
      setBackup({ busy: false, message: backupSavedMessage(fileName), problem: '' })
      info.reload()
    } catch (error) {
      setBackup({ busy: false, message: '', problem: error.message })
    }
  }

  return (
    <div className="settings">
      <section className="settings-section" aria-labelledby="settings-display">
        <h2 id="settings-display">Display</h2>
        <div className="field">
          <label htmlFor="setting-date-format">Date format</label>
          <select
            id="setting-date-format"
            value={settings.dateFormat}
            onChange={(event) => update({ dateFormat: event.target.value })}
          >
            {options.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        {!kept && (
          <p role="alert" className="field-error">
            The choice is used now, but this computer would not keep it for next time.
          </p>
        )}
        <p>
          <strong>Currency.</strong> {CURRENCY_NOTE}
        </p>
        <p className="settings-hint">{DISPLAY_NOTE}</p>
      </section>

      <section className="settings-section" aria-labelledby="settings-backup">
        <h2 id="settings-backup">Back up</h2>
        <p>
          Saves every ledger and every entry to one file. A window opens so you can choose the
          folder. ROKAD never makes a backup by itself, so make one regularly.
        </p>
        <p className="settings-hint">
          {info.status === 'ready' ? lastBackupText(info.data) : ' '}
        </p>
        <button type="button" onClick={handleBackup} disabled={backup.busy}>
          {backup.busy ? 'Backing up…' : 'Back up now…'}
        </button>
        <p role="status" className="status-message">
          {backup.message}
        </p>
        {backup.problem && (
          <p role="alert" className="field-error">
            {backup.problem}
          </p>
        )}
      </section>

      <section className="settings-section" aria-labelledby="settings-restore">
        <h2 id="settings-restore">Restore from a backup</h2>
        <p>
          Replaces everything in ROKAD with the contents of a backup file. You will see what the
          file holds and confirm before anything changes.
        </p>
        <RestorePanel />
      </section>

      <section className="settings-section" aria-labelledby="settings-storage">
        <h2 id="settings-storage">Where your data is</h2>
        {info.status === 'loading' && <p role="status">Loading…</p>}
        {info.status === 'error' && (
          <div className="banner" role="alert">
            <span>{info.error.message}</span>
            <button type="button" onClick={info.reload}>
              Try again
            </button>
          </div>
        )}
        {info.status === 'ready' && (
          <dl className="storage-list">
            {storageRows(info.data).map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        )}
      </section>
    </div>
  )
}
