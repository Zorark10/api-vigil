import { useCallback, useEffect, useState } from 'react'
import { useHashRoute, usePolling, navigate } from './lib/hooks.js'
import { getIncidents } from './lib/api.js'
import { MonitorsPage } from './pages/monitors-page.jsx'
import { MonitorDetailPage } from './pages/monitor-detail-page.jsx'
import { IncidentsPage } from './pages/incidents-page.jsx'

function Mark() {
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
      <path d="M2 12h4l2.5-7 3 10L14 9h4" fill="none" stroke="var(--accent)" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  )
}

export default function App() {
  const route = useHashRoute()
  const [openIncidents, setOpenIncidents] = useState([])

  const loadOpen = useCallback(async () => {
    try {
      const open = await getIncidents('OPEN', 0, 50)
      setOpenIncidents(open.content ?? [])
    } catch {
      /* silent — nav count is decorative */
    }
  }, [])
  useEffect(() => {
    loadOpen()
  }, [loadOpen])
  usePolling(loadOpen, 10000)

  return (
    <div className="shell">
      <aside className="sidebar" aria-label="Product navigation">
        <a className="wordmark" href="#/" onClick={() => (window.location.hash = '#/')}>
          <Mark />
          <span>api-monitor</span>
        </a>
        <nav className="nav">
          <a
            className={`nav-item${route.name === 'monitors' || route.name === 'monitor-detail' ? ' nav-item--active' : ''}`}
            href="#/"
            onClick={(e) => { e.preventDefault(); navigate('#/') }}
          >
            Monitors
          </a>
          <a
            className={`nav-item${route.name === 'incidents' ? ' nav-item--active' : ''}`}
            href="#/incidents"
            onClick={(e) => { e.preventDefault(); navigate('#/incidents') }}
          >
            Incidents
            {openIncidents.length > 0 && <span className="nav-count num">{openIncidents.length} open</span>}
          </a>
        </nav>
      </aside>
      <main className="sheet" id="content">
        {route.name === 'monitor-detail' ? (
          <MonitorDetailPage id={route.id} />
        ) : route.name === 'incidents' ? (
          <IncidentsPage />
        ) : (
          <MonitorsPage openIncidents={openIncidents} />
        )}
      </main>
    </div>
  )
}
