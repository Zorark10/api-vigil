import { useState } from 'react'
import { useAuth } from '../lib/auth-context.jsx'
import { navigate } from '../lib/hooks.js'
import { Mark } from './mark.jsx'

export function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
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
      <section className="login-card" aria-label="Sign in to ApiVigil">
        <a className="login-brand" href="#/" onClick={(e) => e.preventDefault()}>
          <Mark />
          <span><b>Api</b>Vigil</span>
        </a>
        <h1 className="login-title">Sign in</h1>
        <p className="login-finding">
          Sign in with your ApiVigil account. New here? Create one below.
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
              data-testid="login-username"
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="login-password">Password</label>
            <div className="field-secret">
              <input
                id="login-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={busy}
                data-testid="login-password"
              />
              <button
                type="button"
                className="btn btn--ghost btn--small field-secret-toggle"
                onClick={() => setShowPassword((v) => !v)}
                aria-pressed={showPassword}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                disabled={busy}
                data-testid="toggle-login-password"
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
          </div>
          {error && (
            <p className="login-error" role="alert" data-testid="login-error">
              {error}
            </p>
          )}
          <button
            className="btn btn--primary btn--block"
            type="submit"
            disabled={busy}
            data-testid="login-submit"
          >
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="login-foot">
          Uptime checks, incident tracking and email alerts are private to your account.{' '}
          <a className="auth-link" href="#/register" data-testid="create-account-link">
            Create account
          </a>
        </p>
      </section>
    </div>
  )
}
