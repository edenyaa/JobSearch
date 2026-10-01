import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { messageOf } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AuthShell } from '../components/AuthShell'
import { ErrorMessage } from '../components/ErrorMessage'

export const PASSWORD_RULE = 'Password must be at least 7 characters and include letters and numbers.'

export function isStrongPassword(password: string): boolean {
  return password.length >= 7 && /[A-Za-z]/.test(password) && /\d/.test(password)
}

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
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
    if (!isStrongPassword(password)) {
      setError(PASSWORD_RULE)
      return
    }
    setBusy(true)
    try {
      await register(name.trim(), email.trim(), password)
      navigate('/dashboard', { replace: true })
    } catch (e) {
      setError(messageOf(e))
      setBusy(false)
    }
  }

  return (
    <AuthShell>
      <form className="card auth-card" onSubmit={submit}>
        <h1>Create your account</h1>
        <div className="field">
          <label htmlFor="name">Name</label>
          <input id="name" value={name} onChange={(e) => setName(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <small className="muted">{PASSWORD_RULE}</small>
        </div>
        <ErrorMessage message={error} />
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Creating…' : 'Create account'}
        </button>
        <p className="muted">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </AuthShell>
  )
}
