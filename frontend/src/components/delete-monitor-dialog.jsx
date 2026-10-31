import { useState } from 'react'
import { Dialog } from './dialog.jsx'
import * as api from '../lib/api.js'

export function DeleteMonitorDialog({ monitor, onClose, onDeleted }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const confirm = async () => {
    if (busy) return
    setError(null)
    setBusy(true)
    try {
      await api.deleteMonitor(monitor.id)
      onDeleted(monitor)
    } catch (e) {
      setError(e.message || 'The delete request failed')
      setBusy(false)
    }
  }

  return (
    <Dialog title="Delete monitor" onClose={onClose} busy={busy}>
      <div className="form dialog-body">
        <p>
          This permanently removes <strong>{monitor.name}</strong> from monitoring and unlinks its check history.
          This can't be undone.
        </p>
        <p className="mono dialog-object">{monitor.httpMethod} {monitor.url}</p>
        {error && <p className="dialog-error" role="alert">Couldn't delete this monitor: {error}</p>}
        <div className="form-actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="button" className="btn btn--danger-solid" onClick={confirm} disabled={busy}>
            {busy ? 'Deleting…' : 'Yes, delete monitor'}
          </button>
        </div>
      </div>
    </Dialog>
  )
}
