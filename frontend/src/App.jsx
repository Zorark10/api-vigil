import { useCallback, useEffect, useState } from 'react'
import { useHashRoute, usePolling, navigate } from './lib/hooks.js'
import { getIncidents } from './lib/api.js'
import { AuthProvider, useAuth } from './lib/auth-context.jsx'
import { MonitorsPage } from './pages/monitors-page.jsx'
import { MonitorDetailPage } from './pages/monitor-detail-page.jsx'
import { IncidentsPage } from './pages/incidents-page.jsx'
import { NotificationsPage } from './pages/notifications-page.jsx'
import { LoginPage } from './components/login-page.jsx'
import { Mark } from './components/mark.jsx'

function Wordmark() {
  return (
    <a className="wordmark" href="#/" onClick={() => (window.location.hash = '#/')}>
      <Mark />
      <span>api-monitor</span>
    </a>
  )
}

function AuthGate() {
  const { user, initializing } = useAuth()
  const route = useHashRoute()

  if (initializing) {
    return (
      <div className="login-wrap" aria-busy="true">
        <p className="page-note">Checking session…</p>
      </div>
    )
  }

  if (!user) return <LoginPage />

  return <Dashboard route={route} user={user} />
}

function Dashboard({ route, user }) {
  const { logout } = useAuth()
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
        <Wordmark />
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
          <a
            className={`nav-item${route.name === 'notifications' ? ' nav-item--active' : ''}`}
            href="#/notifications"
            onClick={(e) => { e.preventDefault(); navigate('#/notifications') }}
          >
            Notifications
          </a>
        </nav>
        <div className="sidebar-user">
          <span className="sidebar-user-name mono" title={user.username}>{user.username}</span>
          <button
            type="button"
            className="btn btn--ghost btn--small"
            onClick={() => { logout(); navigate('#/login') }}
          >
            Sign out
          </button>
        </div>
      </aside>
      <main className="sheet" id="content">
        {route.name === 'monitor-detail' ? (
          <MonitorDetailPage id={route.id} />
        ) : route.name === 'incidents' ? (
          <IncidentsPage />
        ) : route.name === 'notifications' ? (
          <NotificationsPage />
        ) : (
          <MonitorsPage openIncidents={openIncidents} />
        )}
      </main>
    </div>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <AuthGate />
    </AuthProvider>
  )
}
