/*
Dev-only preview fixture (activated with VITE_PREVIEW_MOCK=1). It patches
window.fetch so the screens can be inspected without a running backend.
The shipped app never loads this module.
*/

function iso(minutesAgo, secondsAgo = 0) {
  const d = new Date(Date.now() - minutesAgo * 60000 - secondsAgo * 1000)
  const pad = (n) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  )
}

const monitors = [
  { id: 1, name: 'Payments API', url: 'https://payments.internal.ridgeline.dev/health', httpMethod: 'GET', interval: 30, timeout: 5000, enabled: true, createdAt: iso(60 * 24 * 9) },
  { id: 2, name: 'Marketing site', url: 'https://www.harborlight.dev', httpMethod: 'GET', interval: 120, timeout: 5000, enabled: true, createdAt: iso(60 * 24 * 9) },
  { id: 3, name: 'Image CDN', url: 'https://img.quayline.net/status', httpMethod: 'GET', interval: 60, timeout: 5000, enabled: true, createdAt: iso(60 * 24 * 5) },
  { id: 4, name: 'Notifications webhook', url: 'https://hooks.quayline.net/ping', httpMethod: 'POST', interval: 300, timeout: 3000, enabled: false, createdAt: iso(60 * 24 * 3) },
  { id: 5, name: 'Postgres exporter', url: 'https://metrics.internal.ridgeline.dev/pg', httpMethod: 'GET', interval: 60, timeout: 2500, enabled: true, createdAt: iso(60 * 12) },
]

const downStatus = { monitorId: 1, status: 'DOWN', responseTime: 3000, checkedAt: iso(0, 41) }

const statusById = {
  1: downStatus,
  2: { monitorId: 2, status: 'UP', responseTime: 142, checkedAt: iso(0, 12) },
  3: { monitorId: 3, status: 'UP', responseTime: 88, checkedAt: iso(0, 7) },
  4: { monitorId: 4, status: 'UP', responseTime: 233, checkedAt: iso(34) },
  5: { monitorId: 5, status: 'UP', responseTime: 41, checkedAt: iso(0, 19) },
}

function seriesFor(monitorId) {
  const base = { 1: 190, 2: 140, 3: 90, 4: 240, 5: 45 }[monitorId]
  const count = monitorId === 4 ? 16 : 48
  return Array.from({ length: count + 2 }, (_, i) => {
    const jitter = Math.round(Math.sin(i * 0.7) * base * 0.18 + (i % 5) * 4)
    const spike = monitorId === 1 && i === count + 1
    return {
      id: monitorId * 1000 + i,
      monitorId,
      status: spike ? 'DOWN' : 'UP',
      statusCode: spike ? null : 200,
      responseTime: spike ? 3000 : Math.max(20, base + jitter),
      checkedAt: iso((count + 2 - i) * (monitorId === 4 ? 5 : 0.5) % 720),
    }
  }).reverse()
}

const stats = (monitorId) => {
  const rows = seriesFor(monitorId)
  const downCount = rows.filter((r) => r.status === 'DOWN').length
  const times = rows.filter((r) => r.responseTime != null).map((r) => r.responseTime)
  return {
    monitorId,
    totalChecks: rows.length,
    successfulChecks: rows.length - downCount,
    failedChecks: downCount,
    uptimePercentage: ((rows.length - downCount) / rows.length) * 100,
    averageResponseTime: times.reduce((a, b) => a + b, 0) / times.length,
    minResponseTime: Math.min(...times),
    maxResponseTime: Math.max(...times),
  }
}

const incidents = [
  { id: 3, monitorId: 1, status: 'OPEN', startedAt: iso(0, 41), resolvedAt: null },
  { id: 2, monitorId: 1, status: 'RESOLVED', startedAt: iso(3700), resolvedAt: iso(3688) },
  { id: 1, monitorId: 3, status: 'RESOLVED', startedAt: iso(8200), resolvedAt: iso(8192) },
]

// Preview session: a signed-in session so authed screens can be inspected.
// Screens/tooling that must inspect the signed-out state set the
// 'am.preview.signOut' flag; the seeded token is then withheld.
const signedOut = sessionStorage.getItem('am.preview.signOut') === '1'
if (!signedOut) sessionStorage.setItem('am.token', 'preview-token')

let user = { username: 'admin', role: 'ADMIN', createdAt: iso(60 * 24 * 30) }

let notificationSettings = { emailEnabled: true }

let recipients = [
  { id: 1, email: 'ops@quayline.net', enabled: true, createdAt: iso(320) },
  { id: 2, email: 'oncall@harborlight.dev', enabled: true, createdAt: iso(260) },
  { id: 3, email: 'security@harborlight.dev', enabled: false, createdAt: iso(180) },
]

function page(list) {
  return { content: list, totalElements: list.length, totalPages: 1, number: 0, size: list.length || 1 }
}

const originalFetch = window.fetch
window.fetch = async (input, init) => {
  const url = typeof input === 'string' ? input : input.url
  const path = url.split('?')[0]
  const query = new URLSearchParams(url.split('?')[1] ?? '')
  const j = (body) =>
    new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
  const method = (init?.method ?? 'GET').toUpperCase()
  const bodyJson = init?.body ? JSON.parse(init.body) : null

    if (path.startsWith('/api')) {
      let m
      if (path === '/api/auth/login' && method === 'POST') return j({ token: 'preview-token', tokenType: 'Bearer' })
      if (path === '/api/auth/register' && method === 'POST') {
        user = {
          username: String(bodyJson?.username ?? 'user'),
          role: 'USER',
          createdAt: iso(0),
        }
        return j({ token: 'preview-token', tokenType: 'Bearer' })
      }
      if (path === '/api/auth/setup-status') return j({ administratorRequired: false })
      if (path === '/api/auth/me') return j(user)
      if (path === '/api/notifications/settings' && method === 'GET') return j(notificationSettings)
      if (path === '/api/notifications/settings' && method === 'PUT') {
        notificationSettings = { emailEnabled: Boolean(bodyJson?.emailEnabled) }
        return j(notificationSettings)
      }
      if (path === '/api/notifications/recipients' && method === 'GET') return j(recipients)
      if (path === '/api/notifications/recipients' && method === 'POST') {
        const email = String(bodyJson?.email ?? '').trim().toLowerCase()
        const existing = recipients.find((r) => r.email === email)
        if (existing) {
          return new Response(JSON.stringify({ status: 409, message: 'This email address is already a notification recipient' }), {
            status: 409,
            headers: { 'Content-Type': 'application/json' },
          })
        }
        const created = { id: Math.max(0, ...recipients.map((r) => r.id)) + 1, email, enabled: true, createdAt: iso(0) }
        recipients = [...recipients, created]
        return new Response(JSON.stringify(created), { status: 201, headers: { 'Content-Type': 'application/json' } })
      }
      if ((m = path.match(/^\/api\/notifications\/recipients\/(\d+)$/)) && method === 'PATCH') {
        recipients = recipients.map((r) => (r.id === +m[1] ? { ...r, enabled: Boolean(bodyJson?.enabled) } : r))
        return j(recipients.find((r) => r.id === +m[1]))
      }
      if (path.match(/^\/api\/notifications\/recipients\/\d+$/) && method === 'DELETE') {
        return new Response(null, { status: 204 })
      }
      if (path === '/api/notifications/test' && method === 'POST') return j({ succeeded: 2, failed: 0 })
      if (path === '/api/monitors' && method === 'POST') return j({ id: 6, ...bodyJson })
      if ((m = path.match(/^\/api\/monitors\/(\d+)$/)) && method === 'PATCH') return j(monitors.find((x) => x.id === +m[1]))
      if ((m = path.match(/^\/api\/monitors\/(\d+)$/)) && method === 'DELETE') {
        const removed = +m[1]
        const without = monitors.filter((mon) => mon.id !== removed)
        monitors.splice(0, monitors.length, ...without)
        delete statusById[removed]
        return new Response(null, { status: 204 })
      }
      if (path === '/api/monitors' && method === 'GET') return j(monitors)
      if ((m = path.match(/^\/api\/monitors\/(\d+)$/)) && method === 'GET') return j(monitors.find((x) => x.id === +m[1]) ?? null)
      if ((m = path.match(/^\/api\/monitors\/(\d+)\/status$/))) return j(statusById[+m[1]] ?? null)
      if ((m = path.match(/^\/api\/monitors\/(\d+)\/statistics$/))) return j(stats(+m[1]))
      if ((m = path.match(/^\/api\/monitors\/(\d+)\/results$/))) {
        const rows = seriesFor(+m[1])
        const p = Number(query.get('page') ?? 0)
        const size = Number(query.get('size') ?? 20)
        return j({
          content: rows.slice(p * size, (p + 1) * size),
          totalElements: rows.length,
          totalPages: Math.max(1, Math.ceil(rows.length / size)),
          number: p,
          size,
        })
      }
      if ((m = path.match(/^\/api\/monitors\/(\d+)\/incidents$/))) return j(page(incidents.filter((i) => i.monitorId === +m[1])))
    if (path === '/api/incidents') {
      const status = query.get('status')
      const filtered = status ? incidents.filter((i) => i.status === status) : incidents
      const p = Number(query.get('page') ?? 0)
      return j({ content: filtered, totalElements: filtered.length, totalPages: 1, number: p, size: 20 })
    }
  }
  return originalFetch(input, init)
}
