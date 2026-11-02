import { useEffect, useState } from 'react'
import { useAuth } from '../lib/auth-context.jsx'
import { getSetupStatus } from '../lib/api.js'
import { navigate } from '../lib/hooks.js'
import { Mark } from './mark.jsx'

export function RegisterPage() {
  const { register } = useAuth()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [error, setError] = useState(null)
  const [confirmError, setConfirmError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [adminSetup, setAdminSetup] = useState(false)

  useEffect(() => {
    // Public bootstrap status: on an empty instance the account created here
    // becomes the initial administrator. Silent best-effort — if the check
    // fails the server-side rule still applies.
    let cancelled = false
    getSetupStatus()
      .then((status) => {
        if (!cancelled) setAdminSetup(Boolean(status.administratorRequired))
      })
      .catch(() => {
        if (!cancelled) setAdminSetup(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  function validateConfirm() {
    if (confirm && confirm !== password) {
      setConfirmError('The two passwords do not match.')
      return false
    }
    setConfirmError(null)
    return true
  }

  function validatePassword() {
    if (password.length > 0 && password.length < 8) {
      setError('Password must be at least 8 characters long.')
      return false
    }
    return true
  }

  async function onSubmit(event) {
    event.preventDefault()
    if (busy) return
    setError(null)
    if (!validateConfirm() || !validatePassword()) return
    setBusy(true)
    try {
      await register(username, email, password)
      navigate('#/')
    } catch (err) {
      setError(err.message || 'Registration failed. Please try again.')
      setBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <section className="login-card" aria-label="Create an ApiVigil account">
        <a className="login-brand" href="#/" onClick={(e) => e.preventDefault()}>
          <Mark />
          <span><b>Api</b>Vigil</span>
        </a>
        <h1 className="login-title">Create account</h1>
        <p className="login-finding">
          Each account gets its own workspace: monitors, check history, incidents and
          notification settings are private to it.
        </p>
        {adminSetup && (
          <p className="login-note" role="status">
            No administrator exists on this instance yet — the account you create now
            will become the initial administrator.
          </p>
        )}
        <form className="login-form" onSubmit={onSubmit}>
          <div className="field">
            <label className="field-label" htmlFor="register-username">Username</label>
            <input
              id="register-username"
              name="username"
              type="text"
              autoComplete="username"
              spellCheck="false"
              minLength={3}
              maxLength={32}
              pattern="[A-Za-z0-9][A-Za-z0-9_.\-]*"
              title="3–32 characters: letters, digits, dots, dashes and underscores"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={busy}
              data-testid="register-username"
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="register-email">Email</label>
            <input
              id="register-email"
              name="email"
              type="email"
              autoComplete="email"
              spellCheck="false"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={busy}
              data-testid="register-email"
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="register-password">Password</label>
            <div className="field-secret">
              <input
                id="register-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                minLength={8}
                maxLength={128}
                required
                value={password}
                onChange={(e) => { setPassword(e.target.value); validateConfirm() }}
                onBlur={validatePassword}
                disabled={busy}
                data-testid="register-password"
              />
              <button
                type="button"
                className="btn btn--ghost btn--small field-secret-toggle"
                onClick={() => setShowPassword((v) => !v)}
                aria-pressed={showPassword}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                disabled={busy}
                data-testid="toggle-register-password"
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
          </div>
          <div className="field">
            <label className="field-label" htmlFor="register-confirm">Confirm password</label>
            <div className="field-secret">
              <input
                id="register-confirm"
                name="confirm-password"
                type={showConfirm ? 'text' : 'password'}
                autoComplete="new-password"
                required
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                onBlur={validateConfirm}
                aria-invalid={Boolean(confirmError)}
                disabled={busy}
                data-testid="register-confirm"
              />
              <button
                type="button"
                className="btn btn--ghost btn--small field-secret-toggle"
                onClick={() => setShowConfirm((v) => !v)}
                aria-pressed={showConfirm}
                aria-label={showConfirm ? 'Hide password confirmation' : 'Show password confirmation'}
                disabled={busy}
                data-testid="toggle-register-confirm"
              >
                {showConfirm ? 'Hide' : 'Show'}
              </button>
            </div>
            {confirmError && (
              <p className="field-error">{confirmError}</p>
            )}
          </div>
          {error && (
            <p className="login-error" role="alert" data-testid="register-error">
              {error}
            </p>
          )}
          <button
            className="btn btn--primary btn--block"
            type="submit"
            disabled={busy}
            data-testid="register-submit"
          >
            {busy ? 'Creating account…' : 'Create account'}
          </button>
        </form>
        <p className="login-foot">
          Already have an account? <a className="auth-link" href="#/login" data-testid="goto-login-link">Sign in</a>
        </p>
      </section>
    </div>
  )
}
