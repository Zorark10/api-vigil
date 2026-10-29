class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.status = status
  }
}

async function request(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
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
