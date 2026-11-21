import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { api, getToken, setToken, setUnauthorizedHandler } from './api'

export type Role =
  | 'ADMIN' | 'RECEPTIONIST' | 'DOCTOR' | 'NURSE' | 'PHARMACIST'
  | 'CASHIER' | 'PATIENT' | 'LAB_TECH' | 'WARD_STAFF'

export interface User {
  username: string
  fullName: string
  role: Role
}

interface LoginResponse {
  accessToken: string
  expiresInSeconds: number
  username: string
  fullName: string
  role: Role
}

interface AuthState {
  user: User | null
  notice: string | null
  login: (username: string, password: string) => Promise<void>
  logout: (reason?: string) => void
}

const USER_KEY = 'caresync_user'
const EXPIRY_KEY = 'caresync_expires'
const IDLE_MS = 15 * 60 * 1000

function loadUser(): User | null {
  try {
    const raw = sessionStorage.getItem(USER_KEY)
    const expires = Number(sessionStorage.getItem(EXPIRY_KEY) ?? 0)
    if (!raw || !getToken() || Date.now() > expires) return null
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(loadUser)
  const [notice, setNotice] = useState<string | null>(null)
  const lastActivity = useRef(Date.now())

  const logout = useCallback((reason?: string) => {
    setToken(null)
    sessionStorage.removeItem(USER_KEY)
    sessionStorage.removeItem(EXPIRY_KEY)
    setUser(null)
    setNotice(reason ?? null)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const r = await api<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: { username, password },
    })
    setToken(r.accessToken)
    const u: User = { username: r.username, fullName: r.fullName, role: r.role }
    sessionStorage.setItem(USER_KEY, JSON.stringify(u))
    sessionStorage.setItem(EXPIRY_KEY, String(Date.now() + r.expiresInSeconds * 1000))
    lastActivity.current = Date.now()
    setNotice(null)
    setUser(u)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => logout('Your session has ended. Please sign in again.'))
    return () => setUnauthorizedHandler(null)
  }, [logout])

  // automatic session timeout: expiry of the token, or 15 minutes without activity
  useEffect(() => {
    if (!user) return
    const touch = () => { lastActivity.current = Date.now() }
    const events = ['mousemove', 'keydown', 'click', 'touchstart', 'scroll'] as const
    events.forEach((e) => window.addEventListener(e, touch, { passive: true }))
    const timer = setInterval(() => {
      const expires = Number(sessionStorage.getItem(EXPIRY_KEY) ?? 0)
      if (Date.now() > expires) {
        logout('Your session has expired. Please sign in again.')
      } else if (Date.now() - lastActivity.current > IDLE_MS) {
        logout('You were signed out after 15 minutes of inactivity.')
      }
    }, 15000)
    return () => {
      events.forEach((e) => window.removeEventListener(e, touch))
      clearInterval(timer)
    }
  }, [user, logout])

  const value = useMemo(() => ({ user, notice, login, logout }), [user, notice, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}