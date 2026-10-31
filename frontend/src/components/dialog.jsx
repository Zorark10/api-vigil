import { useEffect, useRef } from 'react'

export function Dialog({ title, onClose, children, busy = false }) {
  const ref = useRef(null)
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape' && !busy) onClose()
    }
    document.body.classList.add('dialog-open')
    window.addEventListener('keydown', onKey)
    const first = ref.current?.querySelector('input, select, button')
    first?.focus()
    return () => {
      document.body.classList.remove('dialog-open')
      window.removeEventListener('keydown', onKey)
    }
  }, [onClose, busy])

  return (
    <div className="dialog-backdrop" onMouseDown={(e) => e.target === e.currentTarget && !busy && onClose()}>
      <div className="dialog" ref={ref} role="dialog" aria-modal="true" aria-label={title}>
        <div className="dialog-head">
          <h2>{title}</h2>
          <button type="button" className="btn btn--ghost btn--small" onClick={onClose} disabled={busy} aria-label="Close">
            Close
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
