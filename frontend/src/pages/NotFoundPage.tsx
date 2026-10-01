import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="auth-page">
      <div className="card auth-card">
        <h1>Page not found</h1>
        <p className="muted">That page does not exist.</p>
        <Link to="/" className="btn btn-primary">
          Back to the dashboard
        </Link>
      </div>
    </div>
  )
}
