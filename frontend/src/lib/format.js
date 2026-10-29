const DATE_CUTOFF = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/

export function parseDate(value) {
  if (!value) return null
  const date = DATE_CUTOFF.test(value) ? new Date(value.replace(' ', 'T')) : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function fmtDateTime(value) {
  const date = parseDate(value)
  if (!date) return '—'
  const pad = (n) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

export function fmtRelative(value, now = new Date()) {
  const date = parseDate(value)
  if (!date) return '—'
  const seconds = Math.round((now.getTime() - date.getTime()) / 1000)
  if (seconds < 5) return 'just now'
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ${seconds % 60}s ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ${minutes % 60}m ago`
  return fmtDateTime(value)
}

export function fmtDuration(endedAt, startedAt) {
  const end = endedAt ? parseDate(endedAt) : null
  const start = parseDate(startedAt)
  if (!start) return '—'
  const seconds = Math.round(((end ?? new Date()).getTime() - start.getTime()) / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ${seconds % 60}s`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ${minutes % 60}m`
  return `${Math.floor(hours / 24)}d ${hours % 24}h`
}

export function fmtMs(value) {
  if (value === null || value === undefined) return '—'
  return `${Math.round(value)} ms`
}

export function fmtPercent(value) {
  if (value === null || value === undefined) return '—'
  return `${Number(value).toFixed(2)}%`
}
