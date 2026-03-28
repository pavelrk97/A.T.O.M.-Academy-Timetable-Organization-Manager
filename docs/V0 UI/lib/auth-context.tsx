'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import type { AuthState } from './types'
import { clearAuthCredentials, meApi, setAuthCredentials } from './api'

interface StoredAuth {
  username: string
  password: string
}

interface AuthContextType extends AuthState {
  login: (username: string, password: string) => Promise<boolean>
  logout: () => void
  refreshUser: () => Promise<void>
  updateStoredPassword: (newPassword: string) => void
}

const AuthContext = createContext<AuthContextType | null>(null)
const AUTH_STORAGE_KEY = 'atom_v0_auth'

function readStoredAuth(): StoredAuth | null {
  if (typeof window === 'undefined') {
    return null
  }

  const raw = window.localStorage.getItem(AUTH_STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as StoredAuth
  } catch {
    window.localStorage.removeItem(AUTH_STORAGE_KEY)
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    isAuthenticated: false,
    isLoading: true,
  })

  const logout = useCallback(() => {
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem(AUTH_STORAGE_KEY)
    }
    clearAuthCredentials()
    setState({
      user: null,
      isAuthenticated: false,
      isLoading: false,
    })
  }, [])

  const refreshUser = useCallback(async () => {
    try {
      const user = await meApi.getProfile()
      setState((prev) => ({
        ...prev,
        user,
        isAuthenticated: true,
        isLoading: false,
      }))
    } catch {
      logout()
    }
  }, [logout])

  useEffect(() => {
    const stored = readStoredAuth()
    if (!stored) {
      setState((prev) => ({ ...prev, isLoading: false }))
      return
    }

    setAuthCredentials(stored.username, stored.password)
    meApi
      .getProfile()
      .then((user) => {
        setState({
          user,
          isAuthenticated: true,
          isLoading: false,
        })
      })
      .catch(() => {
        logout()
      })
  }, [logout])

  const login = useCallback(async (username: string, password: string) => {
    setState((prev) => ({ ...prev, isLoading: true }))
    setAuthCredentials(username, password)

    try {
      const user = await meApi.getProfile()
      window.localStorage.setItem(
        AUTH_STORAGE_KEY,
        JSON.stringify({ username, password })
      )
      setState({
        user,
        isAuthenticated: true,
        isLoading: false,
      })
      return true
    } catch {
      clearAuthCredentials()
      setState({
        user: null,
        isAuthenticated: false,
        isLoading: false,
      })
      return false
    }
  }, [])

  const updateStoredPassword = useCallback((newPassword: string) => {
    const stored = readStoredAuth()
    if (!stored) {
      return
    }

    const nextStored = { ...stored, password: newPassword }
    window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(nextStored))
    setAuthCredentials(nextStored.username, nextStored.password)
  }, [])

  return (
    <AuthContext.Provider
      value={{
        ...state,
        login,
        logout,
        refreshUser,
        updateStoredPassword,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
