import { useState } from 'react'
import { Icon } from './Icon'

export function ErrorMessage({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <p className="error" role="alert">
      {message}
    </p>
  )
}

interface ErrorStateProps {
  title: string
  message: string | null
  onRetry: () => void
}

export function ErrorState({ title, message, onRetry }: ErrorStateProps) {
  const [showDetails, setShowDetails] = useState(false)
  if (!message) return null
  return (
    <div className="error-state" role="alert">
      <span className="error-state-badge">
        <Icon name="alert" size={22} />
      </span>
      <div className="error-state-body">
        <strong>{title}</strong>
        <p>{message}</p>
        {showDetails && (
          <p className="error-details" data-testid="error-details">
            Make sure the backend is running on port 8080, then try again. If you were signed in for a long time, sign in
            again.
          </p>
        )}
        <div className="error-state-actions">
          <button type="button" className="btn btn-primary" onClick={onRetry}>
            <Icon name="refresh" size={16} /> Retry connection
          </button>
          <button type="button" className="btn btn-ghost" aria-expanded={showDetails} onClick={() => setShowDetails((v) => !v)}>
            {showDetails ? 'Hide details' : 'Show details'}
          </button>
        </div>
      </div>
    </div>
  )
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <p className="muted" role="status">
      {label}
    </p>
  )
}
