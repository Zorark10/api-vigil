export function ErrorBlock({ error, onRetry, context = 'Failed to load data' }) {
  return (
    <div className="block-empty" role="alert">
      <p>
        {context}
        {error?.message ? `: ${error.message}` : '.'}
      </p>
      {onRetry && (
        <button type="button" className="btn btn--ghost" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  )
}

export function EmptyState({ children, action = null }) {
  return (
    <div className="block-empty">
      <p>{children}</p>
      {action}
    </div>
  )
}

const BAR_WIDTHS = [64, 96, 40, 80, 56]

export function LoadingRows({ rows = 4, valueSize = 28 }) {
  return (
    <div className="loading" role="status" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="loading-row">
          <span className="loading-bar loading-bar--value" style={{ height: i === 0 ? valueSize : 14, width: BAR_WIDTHS[i % BAR_WIDTHS.length] }} />
          <span className="loading-bar" style={{ width: BAR_WIDTHS[(i + 2) % BAR_WIDTHS.length] }} />
        </div>
      ))}
    </div>
  )
}
