import { useCallback, useState } from 'react'
import * as api from '../lib/api.js'
import { fmtMs, fmtRelative } from '../lib/format.js'
import { usePolling, useNow } from '../lib/hooks.js'
import { StatusBadge, EnabledBadge, StatusPulse } from '../components/status.jsx'
import { MonitorForm } from '../components/monitor-form.jsx'
import { DeleteMonitorDialog } from '../components/delete-monitor-dialog.jsx'
import { EmptyState, ErrorBlock, LoadingRows } from '../components/states.jsx'
import { IncidentRows } from './monitor-detail-page.jsx'

export function MonitorsPage({ openIncidents }) {
  const [data, setData] = useState({ monitors: [], statusById: {}, resultsById: {} })
  const [recentIncidents, setRecentIncidents] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [freshness, setFreshness] = useState(null)
  const [editing, setEditing] = useState(null) // null | "new" | monitor
  const [saving, setSaving] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(null)
  const now = useNow(5000)

  const load = useCallback(async () => {
    try {
      const [monitors, incidents] = await Promise.all([
        api.getMonitors(),
        api.getIncidents(null, 0, 3).catch(() => null),
      ])
      setRecentIncidents(incidents)
      const [statuses, results] = await Promise.all([
        Promise.all(monitors.map((m) => api.getMonitorStatus(m.id).catch(() => null))),
        Promise.all(monitors.map((m) => api.getResults(m.id, 0, 16).catch(() => null))),
      ])
      const statusById = {}
      const resultsById = {}
      monitors.forEach((m, i) => {
        const raw = statuses[i]
        const status = raw?.status ? raw : null
        if (status) statusById[m.id] = status
        resultsById[m.id] = results[i]
      })
      setData({ monitors, statusById, resultsById })
      setError(null)
      setFreshness(new Date())
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  usePolling(load, 10000)

  const saveMonitor = async (values) => {
    setSaving(true)
    try {
      if (editing === 'new') await api.createMonitor(values)
      else await api.updateMonitor(editing.id, values)
      setEditing(null)
      await load()
    } finally {
      setSaving(false)
    }
  }

  const monitorDeleted = async () => {
    setConfirmingDelete(null)
    await load()
  }

  const down = data.monitors.filter((m) => data.statusById[m.id]?.status === 'DOWN')
  const finding =
    data.monitors.length === 0
      ? null
      : down.length === 0
        ? `All ${data.monitors.length} monitors UP`
        : `${down.length} DOWN · ${down.map((m) => m.name).join(', ')}`

  return (
    <section className="page" aria-label="Monitors">
      <header className="title-row">
        <div>
          <h1>Monitors</h1>
          {finding && <p className="finding">{finding}</p>}
        </div>
        <div className="title-actions">
          {freshness && <span className="freshness">Updated {fmtRelative(freshness, now)}</span>}
          <button type="button" className="btn btn--secondary" onClick={load}>
            Refresh
          </button>
          <button type="button" className="btn btn--primary" onClick={() => setEditing('new')}>
            Add monitor
          </button>
        </div>
      </header>

      {loading ? (
        <div className="panel">
          <div className="table-head table-head--monitor">
            <span>Name</span>
            <span>Status</span>
            <span>Last check</span>
            <span>Recent checks</span>
            <span className="table-right">Actions</span>
          </div>
          <LoadingRows rows={5} />
        </div>
      ) : error ? (
        <ErrorBlock error={error} onRetry={load} context="Couldn't load monitors" />
      ) : data.monitors.length === 0 ? (
        <EmptyState
          action={
            <button type="button" className="btn btn--secondary" onClick={() => setEditing('new')}>
              + Add your first monitor
            </button>
          }
        >
          No monitors yet. Add an HTTP endpoint and the scheduler starts checking it on its interval.
        </EmptyState>
      ) : (
        <div className="monitor-list">
          {data.monitors.map((m) => {
            const status = data.statusById[m.id]
            const recent = data.resultsById[m.id]?.content ?? []
            const chron = [...recent].reverse().filter((r) => r.status === 'UP' || r.status === 'DOWN')
            const okCount = chron.filter((r) => r.status === 'UP').length
            return (
              <article key={m.id} className="monitor-card">
                <div className="monitor-main">
                  <div className="monitor-identity">
                    <a className="monitor-name" href={`#/monitors/${m.id}`}>
                      {m.name}
                    </a>
                    <span className="monitor-url mono">
                      <span className="method-chip num">{m.httpMethod}</span>
                      {m.url}
                    </span>
                    <div className="monitor-tags">
                      <EnabledBadge enabled={m.enabled} />
                      {openIncidents.some((i) => i.monitorId === m.id) && <span className="pill pill--negative">incident open</span>}
                    </div>
                  </div>
                </div>
                <div className="monitor-metrics">
                  <div className="monitor-cell">
                    <span className="cell-label">Status</span>
                    <StatusBadge status={status?.status ?? null} />
                  </div>
                  <div className="monitor-cell">
                    <span className="cell-label">Response</span>
                    <span className="cell-value num">{status?.responseTime != null ? fmtMs(status.responseTime) : '—'}</span>
                  </div>
                  <div className="monitor-cell">
                    <span className="cell-label">Last check</span>
                    <span className="cell-value num">{status?.checkedAt ? fmtRelative(status.checkedAt, now) : '—'}</span>
                  </div>
                  <div className="monitor-cell">
                    <span className="cell-label">Last {chron.length > 0 ? chron.length : 0} checks</span>
                    <StatusPulse results={recent} />
                    <span className="cell-unit num">
                      {chron.length > 0 ? `${okCount}/${chron.length} ok` : '—'}
                      {' · '}every {m.interval}s
                    </span>
                  </div>
                  <div className="monitor-cell monitor-cell--actions">
                    <button type="button" className="btn btn--small btn--ghost" onClick={() => setEditing(m)}>
                      Edit
                    </button>
                    <button type="button" className="btn btn--small btn--ghost btn--danger-text" onClick={() => setConfirmingDelete(m)}>
                      Delete
                    </button>
                  </div>
                </div>
              </article>
            )
          })}
        </div>
      )}

      {confirmingDelete && (
        <DeleteMonitorDialog
          monitor={confirmingDelete}
          onClose={() => setConfirmingDelete(null)}
          onDeleted={monitorDeleted}
        />
      )}

      {data.monitors.length === 0 && !loading && !error && (
        <p className="page-note">
          The scheduler checks enabled monitors from the queue (Kafka <span className="mono">monitor-checks</span>) and stores results in PostgreSQL; incidents open on the first DOWN and resolve on UP.
        </p>
      )}

      {recentIncidents?.content?.length > 0 && (
        <div className="panel">
          <header className="panel-head">
            <h2>Latest incidents</h2>
            <a className="panel-meta panel-link" href="#/incidents">
              View all {openIncidents.length > 0 && <>({openIncidents.length} open)</>}
            </a>
          </header>
          <IncidentRows
            content={recentIncidents.content.slice(0, 3)}
            withMonitorName={(monitorId) => {
              const monitor = data.monitors.find((m) => m.id === monitorId)
              return monitor ? monitor.name : `Monitor #${monitorId}`
            }}
          />
        </div>
      )}

      {editing && (
        <MonitorForm
          initial={editing === 'new' ? null : editing}
          saving={saving}
          onSubmit={saveMonitor}
          onClose={() => setEditing(null)}
        />
      )}
    </section>
  )
}
