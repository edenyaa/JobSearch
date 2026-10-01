import { createContext, useCallback, useContext, useLayoutEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { configureClient } from '../api/client'
import { usersApi } from '../api/endpoints'
import type { User } from '../api/types'

interface Session {
  token: string
  user: User
}

interface AuthValue {
  user: User | null
  login: (email: string, password: string) => Promise<void>
  register: (name: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  expire: (notice?: string) => void
  updateUser: (user: User) => void
  notice: string | null
}

const SESSION_EXPIRED = 'Your session expired. Please sign in again.'

const STORAGE_KEY = 'ats.session'

const AuthContext = createContext<AuthValue | null>(null)

function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

function writeSession(session: Session | null) {
  try {
    if (session) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    return
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(readSession)
  const [notice, setNotice] = useState<string | null>(null)

  const expire = useCallback((message?: string) => {
    writeSession(null)
    setNotice(message ?? null)
    setSession(null)
  }, [])

  useLayoutEffect(() => {
    configureClient(session?.token ?? null, () => expire(SESSION_EXPIRED))
  }, [session, expire])

  const login = useCallback(async (email: string, password: string) => {
    const response = await usersApi.login(email, password)
    const next = { token: response.token, user: response.user }
    writeSession(next)
    setNotice(null)
    setSession(next)
  }, [])

  const register = useCallback(
    async (name: string, email: string, password: string) => {
      await usersApi.register(name, email, password)
      await login(email, password)
    },
    [login],
  )

  const logout = useCallback(async () => {
    if (session) {
      try {
        await usersApi.logout(session.user.id)
      } catch {
        expire()
        return
      }
    }
    expire()
  }, [session, expire])

  const updateUser = useCallback((user: User) => {
    setSession((previous) => {
      if (!previous) return previous
      const next = { token: previous.token, user }
      writeSession(next)
      return next
    })
  }, [])

  const value = useMemo<AuthValue>(
    () => ({ user: session?.user ?? null, login, register, logout, expire, updateUser, notice }),
    [session, login, register, logout, expire, updateUser, notice],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return value
}

export function useUserId(): number {
  const { user } = useAuth()
  if (!user) {
    throw new Error('useUserId needs a signed-in user')
  }
  return user.id
}
