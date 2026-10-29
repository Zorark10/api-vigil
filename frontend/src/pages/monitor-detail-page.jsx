import { useCallback, useState } from 'react'
import * as api from '../lib/api.js'
import { fmtDateTime, fmtDuration, fmtMs, fmtPercent, fmtRelative, parseDate } from '../lib/format.js'
import { usePolling, useNow } from '../lib/hooks.js'
import { StatusBadge, IncidentBadge } from '../components/status.jsx'
import { ResponseTimeChart } from '../components/chart.jsx'
import { MonitorForm } from '../components/monitor-form.jsx'
import { EmptyState, ErrorBlock, LoadingRows } from '../components/states.jsx'
import { navigate } from '../lib/hooks.js'

export function MonitorDetailPage({ id }) {
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [freshness, setFreshness] = useState(null)
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const now = useNow(5000)

  const load = useCallback(async () => {
    const size = 20
    const [monitor, status, statistics, results, incidents, chartResults] = await Promise.all([
      api.getMonitor(id),
      api.getMonitorStatus(id).catch(() => null),
      api.getStatistics(id).catch(() => null),
      api.getResults(id, page, size).catch(() => null),
      api.getIncidentsForMonitor(id, 0, 20).catch(() => []),
      api.getResults(id, 0, 50).catch(() => null),
    ])
    setData({ monitor, status: status?.status ? status : null, statistics, results, incidents, chartResults })
    setError(null)
    setFreshness(new Date())
  }, [id, page])

  usePolling(
    () =>
      load()
        .catch((e) => setError(e))
        .finally(() => setLoading(false)),
    10000,
  )

  const saveMonitor = async (values) => {
    setSaving(true)
    try {
      await api.updateMonitor(id, values)
      setEditing(false)
      await load()
    } finally {
      setSaving(false)
    }
  }

  const deleteMonitor = async () => {
    await api.deleteMonitor(id)
    navigate('#/')
  }

  if (loading && !data) return <LoadingRows rows={6} />
  if (error && !data)
    return (
      <section className="page">
        <ErrorBlock error={error} onRetry={() => { setError(null); load() }} context={`Couldn't load monitor ${id}`} />
      </section>
    )
  if (!data?.monitor) return <EmptyState>Monitor not found.</EmptyState>

  const { monitor, status, statistics, results, incidents, chartResults } = data
  const openIncident = (incidents?.content ?? []).find((i) => i.status === 'OPEN')
  const stats = statistics ?? null
  const statusCell = (key, label, node, lead = false) => (
    <div key={key} className={`kpi-cell${lead ? ' kpi-cell--lead' : ''}`}>
      <span className="cell-label">{label}</span>
      {node}
    </div>
  )

  return (
    <section className="page" aria-label={monitor.name}>
      <header className="title-row">
        <div>
          <h1>{monitor.name}</h1>
          <p className="finding">
            <StatusBadge status={status?.status ?? null} />{' '}
            <span className="mono">{monitor.httpMethod} {monitor.url}</span>
            {monitor.enabled ? '' : ' · paused'}
          </p>
        </div>
        <div className="title-actions">
          {freshness && <span className="freshness">Updated {fmtRelative(freshness, now)}</span>}
          <button type="button" className="btn btn--secondary" onClick={load}>
            Refresh
          </button>
          <button type="button" className="btn btn--secondary" onClick={() => setEditing(true)}>
            Edit
          </button>
          {confirmingDelete ? (
            <span className="row-confirm">
              Delete?
              <button type="button" className="btn btn--small btn--danger" onClick={deleteMonitor}>
                Yes, delete
              </button>
              <button type="button" className="btn btn--small btn--ghost" onClick={() => setConfirmingDelete(false)}>
                Cancel
              </button>
            </span>
          ) : (
            <button type="button" className="btn btn--secondary btn--danger-text" onClick={() => setConfirmingDelete(true)}>
              Delete
            </button>
          )}
        </div>
      </header>

      {stats ? (
        <div className="kpi-strip">
          {statusCell('up', 'Uptime', <span className="cell-value num kpi-lead">{fmtPercent(stats.uptimePercentage)}</span>, true)}
          {statusCell(
            'checks',
            'Checks',
            <span className="cell-value num">
              {stats.totalChecks}
              {stats.failedChecks > 0 && <span className="cell-unit"> / {stats.failedChecks} failed</span>}
            </span>,
          )}
          {statusCell('avg', 'Avg response', <span className="cell-value num">{fmtMs(stats.averageResponseTime)}</span>)}
          {statusCell('min', 'Min', <span className="cell-value num">{fmtMs(stats.minResponseTime)}</span>)}
          {statusCell('max', 'Max', <span className="cell-value num">{fmtMs(stats.maxResponseTime)}</span>)}
        </div>
      ) : (
        <LoadingRows rows={1} valueSize={30} />
      )}

      <div className="detail-columns">
        <div className="panel chart-panel">
          <header className="panel-head">
            <h2>Response time</h2>
            <span className="panel-meta">last {chartResults?.content?.length ?? 0} checks</span>
          </header>
          {chartResults?.content?.length ? (
            <ResponseTimeChart points={chartResults.content} />
          ) : (
            <EmptyState>No checks recorded yet. The first results appear here within one interval.</EmptyState>
          )}
        </div>
        <div className="panel status-panel">
          <header className="panel-head">
            <h2>Current state</h2>
          </header>
          <dl className="state-list">
            <div>
              <dt>Status</dt>
              <dd>
                <StatusBadge status={status?.status ?? null} />
              </dd>
            </div>
            <div>
              <dt>Response time</dt>
              <dd className="num">{status?.responseTime != null ? fmtMs(status.responseTime) : '—'}</dd>
            </div>
            <div>
              <dt>Last checked</dt>
              <dd className="num">{status?.checkedAt ? fmtDateTime(status.checkedAt) : '—'}</dd>
            </div>
            <div>
              <dt>Incident</dt>
              <dd>
                {openIncident ? (
                  <a href="#/incidents">
                    open {fmtDuration(null, openIncident.startedAt)} so far
                  </a>
                ) : (
                  <span className="num">none</span>
                )}
              </dd>
            </div>
          </dl>
        </div>
      </div>

      <div className="panel">
        <header className="panel-head">
          <h2>Recent checks</h2>
          <span className="panel-meta">
            {results ? `${results.totalElements} total` : ''}
          </span>
        </header>
        {results?.content?.length ? (
          <TableWithPager
            current={results.number}
            total={results.totalPages}
            onPage={setPage}
          >
            <div className="table table-head table-head--4">
              <span>Checked at</span>
              <span>Status</span>
              <span className="table-right">HTTP code</span>
              <span className="table-right">Response</span>
            </div>
            {results.content.map((r) => (
              <div className="table-row table-row--4" key={r.id}>
                <span className="num">{fmtDateTime(r.checkedAt)}</span>
                <span>
                  <StatusBadge status={r.status} />
                </span>
                <span className="table-right num">{r.statusCode ?? '—'}</span>
                <span className="table-right num">{fmtMs(r.responseTime)}</span>
              </div>
            ))}
          </TableWithPager>
        ) : (
          <EmptyState>No checks recorded yet.</EmptyState>
        )}
      </div>

      <div className="panel">
        <header className="panel-head">
          <h2>Incidents</h2>
          <span className="panel-meta">{incidents?.totalElements ?? 0} total</span>
        </header>
        {incidents?.content?.length ? (
          <IncidentRows content={incidents.content} now={now} />
        ) : (
          <EmptyState>No incidents. Every check has come back UP since this monitor was added.</EmptyState>
        )}
      </div>

      {editing && (
        <MonitorForm initial={monitor} saving={saving} onSubmit={saveMonitor} onClose={() => setEditing(false)} />
      )}
    </section>
  )
}

export function IncidentRows({ content, now, withMonitorName = null }) {
  return (
    <div className="table">
      <div className={`table-head ${withMonitorName ? 'table-head--5' : 'table-head--4'}`}>
        {withMonitorName && <span>Monitor</span>}
        <span>Started</span>
        <span>Resolved</span>
        <span className="table-right">Duration</span>
        <span>Status</span>
      </div>
      {content.map((i) => (
        <div className={`table-row ${withMonitorName ? 'table-row--5' : 'table-row--4'}`} key={i.id}>
          {withMonitorName && <span>{withMonitorName(i.monitorId)}</span>}
          <span className="num">{fmtDateTime(i.startedAt)}</span>
          <span className="num">{i.resolvedAt ? fmtDateTime(i.resolvedAt) : <span className="dash" title="Still open">—</span>}</span>
          <span className="table-right num">{fmtDuration(i.resolvedAt, i.startedAt)}</span>
          <span>
            <IncidentBadge status={i.status} />
          </span>
        </div>
      ))}
    </div>
  )
}

function TableWithPager({ current, total, onPage, children }) {
  return (
    <>
      {children}
      {total > 1 && (
        <div className="pager">
          <button type="button" className="btn btn--small btn--ghost" disabled={current === 0} onClick={() => onPage(current - 1)}>
            Prev
          </button>
          <span className="num pager-range">
            Page {current + 1} of {total}
          </span>
          <button type="button" className="btn btn--small btn--ghost" disabled={current + 1 >= total} onClick={() => onPage(current + 1)}>
            Next
          </button>
        </div>
      )}
    </>
  )
}
