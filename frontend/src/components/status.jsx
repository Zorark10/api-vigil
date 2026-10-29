import { fmtRelative } from '../lib/format.js'

export function StatusBadge({ status }) {
  if (!status) return <span className="dash">not checked yet</span>
  return (
    <span className={`status ${status === 'UP' ? 'status--up' : 'status--down'}`}>
      <span className="status-dot" aria-hidden="true" />
      {status}
    </span>
  )
}

export function IncidentBadge({ status }) {
  if (!status) return <span className="dash">—</span>
  const open = status === 'OPEN'
  return (
    <span className={`pill ${open ? 'pill--negative' : 'pill--neutral'}`}>
      {status.toLowerCase()}
      {open && <span className="pulse-dot" aria-hidden="true" />}
    </span>
  )
}

export function EnabledBadge({ enabled }) {
  return (
    <span className={`pill ${enabled ? 'pill--neutral' : 'pill--warning'}`}>
      {enabled ? 'enabled' : 'paused'}
    </span>
  )
}

const PULSE_STEPS = 16

export function StatusPulse({ results }) {
  if (!results || results.length === 0) return <span className="dash">—</span>
  const chronological = [...results].reverse()
  const filled = Array.from({ length: PULSE_STEPS }, (_, i) => chronological[i])
  return (
    <span className="status-pulse" aria-label={`Last ${filled.filter(Boolean).length} checks`}>
      {filled.map((entry, i) => (
        <span
          key={entry ? entry.id : `empty-${i}`}
          className={`status-cell ${entry ? (entry.status === 'UP' ? 'status-cell--up' : 'status-cell--down') : 'status-cell--empty'}`}
          title={
            entry
              ? `${fmtRelative(entry.checkedAt)} · ${entry.status}${entry.statusCode ? ` · ${entry.statusCode}` : ''}`
              : 'Not enough checks yet'
          }
        />
      ))}
    </span>
  )
}
