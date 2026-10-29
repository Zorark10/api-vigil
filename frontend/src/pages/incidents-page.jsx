import { useCallback, useState } from 'react'
import * as api from '../lib/api.js'
import { usePolling } from '../lib/hooks.js'
import { IncidentRows } from './monitor-detail-page.jsx'
import { ErrorBlock, LoadingRows } from '../components/states.jsx'

const TABS = [
  { key: '', label: 'All' },
  { key: 'OPEN', label: 'Open' },
  { key: 'RESOLVED', label: 'Resolved' },
]

export function IncidentsPage() {
  const [tab, setTab] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [monitors, setMonitors] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    try {
      const [incidents, monitorList] = await Promise.all([api.getIncidents(tab || null, page, 20), api.getMonitors().catch(() => [])])
      setData(incidents)
      setMonitors(monitorList)
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [tab, page])

  usePolling(load, 10000)

  const monitorName = (monitorId) => {
    const monitor = monitors.find((m) => m.id === monitorId)
    return (
      <a className="monitor-name" href={`#/monitors/${monitorId}`}>
        {monitor ? monitor.name : `Monitor #${monitorId}`}
      </a>
    )
  }

  const switchTab = (key) => {
    setTab(key)
    setPage(0)
  }

  return (
    <section className="page" aria-label="Incidents">
      <header className="title-row">
        <div>
          <h1>Incidents</h1>
          <p className="finding">
            {data ? `${data.totalElements} incidents` : ''}
            {tab === 'OPEN' ? ' · unresolved only' : tab === 'RESOLVED' ? ' · resolved only' : ''}
          </p>
        </div>
      </header>

      <nav className="tabs" aria-label="Incident filter">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            className={`tab${tab === t.key ? ' tab--active' : ''}`}
            aria-current={tab === t.key ? 'page' : undefined}
            onClick={() => switchTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </nav>

      {loading && !data ? (
        <div className="panel">
          <LoadingRows rows={5} />
        </div>
      ) : error ? (
        <ErrorBlock error={error} onRetry={load} context="Couldn't load incidents" />
      ) : data.content.length === 0 ? (
        <EmptyState>
          {tab === 'OPEN'
            ? 'No open incidents. Every monitor is back UP.'
            : 'No incidents yet. One opens when a check first comes back DOWN.'}
        </EmptyState>
      ) : (
        <>
          <div className="panel">
            <IncidentRows content={data.content} withMonitorName={monitorName} />
          </div>
          <div className="pager">
            <span className="num pager-range">
              {data.totalElements === 0 ? '0 incidents' : `${data.number * data.size + 1}–${Math.min((data.number + 1) * data.size, data.totalElements)} of ${data.totalElements}`}
            </span>
            <button type="button" className="btn btn--small btn--ghost" disabled={data.number === 0} onClick={() => setPage(data.number - 1)}>
              Prev
            </button>
            <button
              type="button"
              className="btn btn--small btn--ghost"
              disabled={data.number + 1 >= data.totalPages}
              onClick={() => setPage(data.number + 1)}
            >
              Next
            </button>
          </div>
        </>
      )}
    </section>
  )
}
