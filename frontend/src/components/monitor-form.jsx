import { useState } from 'react'
import { Dialog } from './dialog.jsx'

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const BLANK = { name: '', url: 'https://', httpMethod: 'GET', interval: 60, timeout: 5000, enabled: true }

export function MonitorForm({ initial, onSubmit, onClose, saving }) {
  const [values, setValues] = useState(
    initial
      ? { ...BLANK, ...initial }
      : BLANK,
  )
  const [errors, setErrors] = useState({})

  const set = (field) => (e) => {
    const raw = e.target.type === 'checkbox' ? e.target.checked : e.target.value
    setValues((v) => ({ ...v, [field]: raw }))
  }

  const validate = () => {
    const next = {}
    if (!values.name.trim()) next.name = 'Name is required'
    if (!values.url.trim()) next.url = 'URL is required'
    const interval = Number(values.interval)
    if (!Number.isInteger(interval) || interval <= 0) next.interval = 'Interval must be a positive number of seconds'
    const timeout = Number(values.timeout)
    if (!Number.isInteger(timeout) || timeout <= 0) next.timeout = 'Timeout must be a positive number of ms'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = (e) => {
    e.preventDefault()
    if (!validate()) return
    onSubmit({
      name: values.name.trim(),
      url: values.url.trim(),
      httpMethod: values.httpMethod,
      interval: Number(values.interval),
      timeout: Number(values.timeout),
      enabled: values.enabled,
    })
  }

  return (
    <Dialog title={initial ? 'Edit monitor' : 'Add monitor'} onClose={onClose}>
      <form className="form" onSubmit={submit} noValidate>
        <label className="field">
          <span className="field-label">Name</span>
          <input value={values.name} onChange={set('name')} aria-invalid={Boolean(errors.name)} placeholder="Payments API" />
          {errors.name && <span className="field-error">{errors.name}</span>}
        </label>

        <label className="field">
          <span className="field-label">URL</span>
          <input value={values.url} onChange={set('url')} aria-invalid={Boolean(errors.url)} className="mono-input" placeholder="https://api.internal.ridgeline.dev/health" />
          {errors.url && <span className="field-error">{errors.url}</span>}
        </label>

        <div className="form-row">
          <label className="field">
            <span className="field-label">Method</span>
            <select value={values.httpMethod} onChange={set('httpMethod')}>
              {METHODS.map((m) => (
                <option key={m} value={m}>{m}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field-label">Interval (seconds)</span>
            <input type="number" min="1" value={values.interval} onChange={set('interval')} aria-invalid={Boolean(errors.interval)} />
            {errors.interval && <span className="field-error">{errors.interval}</span>}
          </label>
          <label className="field">
            <span className="field-label">Timeout (ms)</span>
            <input type="number" min="1" value={values.timeout} onChange={set('timeout')} aria-invalid={Boolean(errors.timeout)} />
            {errors.timeout && <span className="field-error">{errors.timeout}</span>}
          </label>
        </div>

        <label className="toggle-row">
          <input type="checkbox" checked={values.enabled} onChange={set('enabled')} />
          <span>Enabled — the scheduler checks this monitor on its interval</span>
        </label>

        <div className="form-actions">
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn--primary" disabled={saving}>
            {saving ? 'Saving…' : initial ? 'Save changes' : 'Add monitor'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
