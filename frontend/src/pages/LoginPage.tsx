import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { messageOf } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AuthShell } from '../components/AuthShell'
import { ErrorMessage } from '../components/ErrorMessage'

export function LoginPage() {
  const { user, login, notice } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as { notice?: string; from?: string } | null
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) {
    return <Navigate to="/dashboard" replace />
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(email.trim(), password)
      navigate(state?.from ?? '/dashboard', { replace: true })
    } catch (e) {
      setError(messageOf(e))
      setBusy(false)
    }
  }

  return (
    <AuthShell>
      <form className="card auth-card" onSubmit={submit}>
        <h1>Sign in</h1>
        {(state?.notice ?? notice) && (
          <p className="notice" role="status">
            {state?.notice ?? notice}
          </p>
        )}
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        <ErrorMessage message={error} />
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <p className="muted">
          New here? <Link to="/register">Create an account</Link>
        </p>
      </form>
    </AuthShell>
  )
}
