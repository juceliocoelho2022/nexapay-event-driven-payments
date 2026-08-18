import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  apiFetch,
  clearAccessToken,
  getAccessToken,
  publicApiFetch,
  setAccessToken,
} from '../lib/api'
import type { LoginResponse, MeResponse } from '../types'

type AuthContextValue = {
  user: MeResponse | null
  loading: boolean
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => void
  hasPermission: (permission: string) => boolean
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null)
  const [loading, setLoading] = useState(true)

  async function refreshUser() {
    if (!getAccessToken()) {
      setUser(null)
      return
    }

    try {
      const me = await apiFetch<MeResponse>('/api/v1/auth/me')
      setUser(me)
    } catch {
      clearAccessToken()
      setUser(null)
    }
  }

  useEffect(() => {
    void refreshUser().finally(() => setLoading(false))
  }, [])

  async function login(email: string, password: string) {
    clearAccessToken()
    const response = await publicApiFetch<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    setAccessToken(response.accessToken)
    await refreshUser()
  }

  async function register(email: string, password: string) {
    clearAccessToken()
    await publicApiFetch('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    await login(email, password)
  }

  function logout() {
    clearAccessToken()
    setUser(null)
  }

  const value = useMemo<AuthContextValue>(() => ({
    user,
    loading,
    isAuthenticated: Boolean(user),
    login,
    register,
    logout,
    hasPermission: (permission) => Boolean(user?.permissions.includes(permission)),
    refreshUser,
  }), [user, loading])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
