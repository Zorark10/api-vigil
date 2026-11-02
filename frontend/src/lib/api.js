import { getToken, setToken } from './auth.js'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').trim().replace(/\/+$/, '')

function apiUrl(path) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  if (!API_BASE_URL) return normalizedPath

  // Accept either a backend origin or an origin that already ends in /api.
  const baseUrl = API_BASE_URL.replace(/\/api$/i, '')
  const apiPath = /^\/api(?:\/|$)/i.test(normalizedPath)
    ? normalizedPath
    : `/api${normalizedPath}`
  return `${baseUrl}${apiPath}`
}

class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.status = status
  }
}

async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(apiUrl(path), { ...options, headers })
  if (res.status === 401 && !path.startsWith('/api/auth/')) {
    // Session is no longer valid (expired or revoked token): drop it and let
    // the auth gate send the user back to the sign-in screen.
    setToken(null)
    window.location.hash = '#/login'
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`
    try {
      const body = await res.json()
      if (body && body.message) message = body.message
    } catch {
      /* response body was not error JSON */
    }
    throw new ApiError(message, res.status)
  }
  if (res.status === 204) return null
  return res.json()
}

export async function login(username, password) {
  return request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

// Registers a new account; the backend immediately authenticates it, so the
// response carries a Bearer token the same way login does.
export async function register(username, email, password) {
  return request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, email, password }),
  })
}

// Public: reports whether this instance still has no users (the next
// registration will create the initial administrator).
export const getSetupStatus = () => request('/api/auth/setup-status')

export const getMe = () => request('/api/auth/me')

export const getMonitors = () => request('/api/monitors')
export const getMonitor = (id) => request(`/api/monitors/${id}`)
export const createMonitor = (monitor) =>
  request('/api/monitors', { method: 'POST', body: JSON.stringify(monitor) })
export const updateMonitor = (id, monitor) =>
  request(`/api/monitors/${id}`, { method: 'PATCH', body: JSON.stringify(monitor) })
export const deleteMonitor = (id) =>
  request(`/api/monitors/${id}`, { method: 'DELETE' })

export const getMonitorStatus = (id) => request(`/api/monitors/${id}/status`)
export const getStatistics = (id) => request(`/api/monitors/${id}/statistics`)
export const getResults = (id, page = 0, size = 20) =>
  request(`/api/monitors/${id}/results?page=${page}&size=${size}`)

export const getIncidents = (status, page = 0, size = 20) => {
  const params = new URLSearchParams({ page, size, sort: 'startedAt,desc' })
  if (status) params.set('status', status)
  return request(`/api/incidents?${params}`)
}

export const getIncidentsForMonitor = (id, page = 0, size = 20) =>
  request(`/api/monitors/${id}/incidents?page=${page}&size=${size}`)

export const getNotificationSettings = () => request('/api/notifications/settings')
export const updateNotificationSettings = (emailEnabled) =>
  request('/api/notifications/settings', {
    method: 'PUT',
    body: JSON.stringify({ emailEnabled }),
  })

export const getRecipients = () => request('/api/notifications/recipients')
export const addRecipient = (email) =>
  request('/api/notifications/recipients', {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
export const updateRecipient = (id, enabled) =>
  request(`/api/notifications/recipients/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ enabled }),
  })
export const removeRecipient = (id) =>
  request(`/api/notifications/recipients/${id}`, { method: 'DELETE' })
export const sendTestEmail = () => request('/api/notifications/test', { method: 'POST' })
