import { useState } from 'react'
import { useAuth } from '../lib/auth-context.jsx'
import { navigate } from '../lib/hooks.js'
import { Mark } from './mark.jsx'

export function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(event) {
    event.preventDefault()
    if (busy) return
    setError(null)
    setBusy(true)
    try {
      await login(username.trim(), password)
      navigate('#/')
    } catch (err) {
      setError(err.message || 'Sign-in failed. Please try again.')
      setBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <section className="login-card" aria-label="Sign in to api-monitor">
        <a className="login-brand" href="#/" onClick={(e) => e.preventDefault()}>
          <Mark />
          <span>api-monitor</span>
        </a>
        <h1 className="login-title">Sign in</h1>
        <p className="login-finding">
          Use the administrator account configured for this api-monitor instance.
        </p>
        <form className="login-form" onSubmit={onSubmit}>
          <div className="field">
            <label className="field-label" htmlFor="login-username">Username</label>
            <input
              id="login-username"
              name="username"
              type="text"
              autoComplete="username"
              spellCheck="false"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={busy}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="login-password">Password</label>
            <input
              id="login-password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={busy}
            />
          </div>
          {error && (
            <p className="login-error" role="alert">
              {error}
            </p>
          )}
          <button className="btn btn--primary btn--block" type="submit" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </section>
    </div>
  )
}
