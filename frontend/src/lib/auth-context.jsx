import { createContext, useContext, useEffect, useState } from 'react'
import { getMe, login as apiLogin, register as apiRegister } from './api.js'
import { getToken, setToken } from './auth.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [initializing, setInitializing] = useState(Boolean(getToken()))

  useEffect(() => {
    // A token from the previous reload is only worth keeping if the backend
    // still accepts it; otherwise the user just sees the sign-in screen.
    let cancelled = false
    if (!getToken()) return
    getMe()
      .then((me) => {
        if (cancelled) return
        setUser(me)
        setInitializing(false)
      })
      .catch(() => {
        if (cancelled) return
        setToken(null)
        setInitializing(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const login = async (username, password) => {
    const result = await apiLogin(username, password)
    setToken(result.token)
    const me = await getMe()
    setUser(me)
    return me
  }

  // Registration auto-authenticates: the backend's response already carries
  // the token, so the new account lands in its (empty) workspace directly.
  const register = async (username, email, password) => {
    const result = await apiRegister(username.trim(), email.trim(), password)
    setToken(result.token)
    const me = await getMe()
    setUser(me)
    return me
  }

  const logout = () => {
    // Drop the session AND every account-specific in-memory value; pages
    // refetch their workspace data on mount only, so a stale token or stale
    // user must not survive a sign-out / account switch.
    setToken(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, initializing, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
