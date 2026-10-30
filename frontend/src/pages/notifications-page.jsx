import { useCallback, useState } from 'react'
import * as api from '../lib/api.js'
import { usePolling } from '../lib/hooks.js'
import { EmptyState, ErrorBlock } from '../components/states.jsx'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function testNotice(result) {
  if (!result) return null
  const { succeeded, failed } = result
  if (succeeded === 0 && failed === 0) {
    return { kind: 'note', text: 'No enabled recipients to write to. Add or enable one below and try again.' }
  }
  if (failed > 0) {
    return { kind: 'note', text: `Delivered to ${succeeded} recipient${succeeded === 1 ? '' : 's'}; ${failed} failed — this is usually the SMTP login or network, not the address.` }
  }
  return { kind: 'ok', text: `Test email delivered to ${succeeded} enabled recipient${succeeded === 1 ? '' : 's'}.` }
}

export function NotificationsPage() {
  const [settings, setSettings] = useState(null)
  const [recipients, setRecipients] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [formEmail, setFormEmail] = useState('')
  const [formError, setFormError] = useState(null)
  const [adding, setAdding] = useState(false)
  const [busyId, setBusyId] = useState(null)
  const [testing, setTesting] = useState(false)
  const [notice, setNotice] = useState(null)

  const load = useCallback(async () => {
    try {
      const [nextSettings, nextRecipients] = await Promise.all([
        api.getNotificationSettings(),
        api.getRecipients(),
      ])
      setSettings(nextSettings)
      setRecipients(nextRecipients)
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  usePolling(load, 15000, true)

  const toggleMaster = async (event) => {
    const next = event.target.checked
    const before = settings
    setSettings({ ...settings, emailEnabled: next })
    try {
      const saved = await api.updateNotificationSettings(next)
      setSettings(saved)
    } catch (e) {
      setSettings(before)
      setNotice({ kind: 'note', text: `Couldn't save the switch: ${e.message}` })
    }
  }

  const addRecipient = async (event) => {
    event.preventDefault()
    const email = formEmail.trim()
    if (!EMAIL_RE.test(email)) {
      setFormError('Enter a valid email address, like ops@yourteam.com')
      return
    }
    setFormError(null)
    setAdding(true)
    try {
      const created = await api.addRecipient(email)
      setRecipients((list) => [...(list ?? []), created])
      setFormEmail('')
    } catch (e) {
      setFormError(e.message)
    } finally {
      setAdding(false)
    }
  }

  const toggleRecipient = async (recipient) => {
    setBusyId(recipient.id)
    try {
      const updated = await api.updateRecipient(recipient.id, !recipient.enabled)
      setRecipients((list) =>
        (list ?? []).map((r) => (r.id === updated.id ? updated : r)),
      )
    } catch (e) {
      setNotice({ kind: 'note', text: `Couldn't update ${recipient.email}: ${e.message}` })
    } finally {
      setBusyId(null)
    }
  }

  const removeRecipient = async (recipient) => {
    setBusyId(recipient.id)
    try {
      await api.removeRecipient(recipient.id)
      setRecipients((list) => (list ?? []).filter((r) => r.id !== recipient.id))
    } catch (e) {
      setNotice({ kind: 'note', text: `Couldn't remove ${recipient.email}: ${e.message}` })
    } finally {
      setBusyId(null)
    }
  }

  const sendTest = async () => {
    setTesting(true)
    try {
      setNotice(testNotice(await api.sendTestEmail()))
    } catch (e) {
      setNotice({ kind: 'note', text: `Test email failed: ${e.message}` })
    } finally {
      setTesting(false)
    }
  }

  return (
    <section className="page" aria-label="Email notifications">
      <header className="title-row">
        <div>
          <h1>Email notifications</h1>
          <p className="finding">
            An email goes out to every enabled recipient when an incident opens and when it recovers.
            One email per event, never a repeat for the same outage.
          </p>
        </div>
        <div className="title-actions">
          <button
            type="button"
            className="btn btn--secondary"
            onClick={sendTest}
            disabled={testing || loading || error}
          >
            {testing ? 'Sending…' : 'Send test email'}
          </button>
        </div>
      </header>

      {loading && !settings ? (
        <div className="panel">
          <div className="loading" role="status" aria-label="Loading notification settings">
            <div className="loading-row">
              <span className="loading-bar" style={{ width: 180 }} />
            </div>
            <div className="loading-row">
              <span className="loading-bar" style={{ width: 120 }} />
            </div>
          </div>
        </div>
      ) : error && !settings ? (
        <ErrorBlock error={error} onRetry={load} context="Couldn't load notification settings" />
      ) : (
        <>
          {notice && (
            <p className={`notice${notice.kind === 'ok' ? ' notice--ok' : ''}`} role="status">
              {notice.text}
            </p>
          )}

          <div className="panel">
            <div className="panel-head">
              <h2>Delivery</h2>
              <span className="panel-meta">
                {recipients?.length
                  ? `${recipients.filter((r) => r.enabled).length} of ${recipients.length} recipients enabled`
                  : 'no recipients yet'}
              </span>
            </div>
            <label className="toggle-row">
              <input type="checkbox" checked={settings.emailEnabled} onChange={toggleMaster} />
              <span>Sending incident emails for this api-monitor instance</span>
            </label>
            <p className="page-note">
              With this off, incidents are still recorded in the dashboard but no email is sent.
              Recipients below stay saved; turn it back on to resume.
            </p>
          </div>

          <div className="panel">
            <div className="panel-head">
              <h2>Recipients</h2>
              <span className="panel-meta">addresses that receive incident emails</span>
            </div>
            <form className="recipient-form" onSubmit={addRecipient} noValidate>
              <div className="field">
                <label className="field-label" htmlFor="recipient-email">Email address</label>
                <input
                  id="recipient-email"
                  type="email"
                  className="mono-input"
                  placeholder="ops@yourteam.com"
                  value={formEmail}
                  onChange={(e) => { setFormEmail(e.target.value); setFormError(null) }}
                  aria-invalid={Boolean(formError)}
                  disabled={adding}
                />
                {formError && <span className="field-error" role="alert">{formError}</span>}
              </div>
              <button type="submit" className="btn btn--primary" disabled={adding}>
                {adding ? 'Adding…' : 'Add recipient'}
              </button>
            </form>

            {recipients && recipients.length > 0 && (
              <div className="state-list recipient-list">
                {recipients.map((r) => (
                  <div key={r.id} className="recipient-row">
                    <span className="mono">{r.email}</span>
                    <div className="recipient-actions">
                      <button
                        type="button"
                        className="btn btn--small btn--ghost"
                        disabled={busyId === r.id}
                        onClick={() => toggleRecipient(r)}
                        aria-pressed={r.enabled}
                      >
                        {r.enabled ? 'Enabled' : 'Disabled'}
                      </button>
                      <button
                        type="button"
                        className="btn btn--small btn--ghost btn--danger-text"
                        disabled={busyId === r.id}
                        onClick={() => removeRecipient(r)}
                      >
                        Remove
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {recipients && recipients.length === 0 && (
              <EmptyState>
                No recipients yet. Incident emails land at the addresses you add here.
              </EmptyState>
            )}
          </div>
        </>
      )}
    </section>
  )
}
