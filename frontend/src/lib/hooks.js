import { useCallback, useEffect, useRef, useState } from 'react'

export function useHashRoute() {
  const [hash, setHash] = useState(window.location.hash || '#/')
  useEffect(() => {
    const onChange = () => setHash(window.location.hash || '#/')
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  const [page, parts] = hash.replace(/^#/, '').split('/').filter(Boolean)
  if (page === 'monitors' && parts) return { name: 'monitor-detail', id: Number(parts) }
  if (page === 'incidents') return { name: 'incidents' }
  return { name: 'monitors' }
}

export function navigate(hash) {
  window.location.hash = hash
}

export function usePolling(callback, intervalMs = 10000, enabled = true) {
  const cbRef = useRef(callback)
  cbRef.current = callback
  useEffect(() => {
    if (!enabled) return undefined
    const tick = () => cbRef.current()
    tick()
    const timer = window.setInterval(tick, intervalMs)
    return () => window.clearInterval(timer)
  }, [intervalMs, enabled])
}

export function useNow(intervalMs = 1000) {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), intervalMs)
    return () => window.clearInterval(timer)
  }, [intervalMs])
  return now
}

export function useAsync(run, deps, initialData = null) {
  const [state, setState] = useState({ data: initialData, loading: true, error: null })
  const runRef = useRef(run)
  runRef.current = run
  const reload = useCallback(() => {
    const promise = Promise.resolve(runRef.current())
    setState((prev) => ({ ...prev, loading: true, error: null }))
    promise
      .then((data) => setState({ data, loading: false, error: null }))
      .catch((error) => setState({ data: null, loading: false, error }))
    return promise
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  useEffect(() => {
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  return { ...state, reload }
}
