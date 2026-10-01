import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Icon } from './Icon'

const POINTS = [
  'Your skills are found in your CV for you',
  'See what each job asks for, in charts',
  'AI rewrites your CV for the job, truthfully',
]

export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="auth-shell">
      <aside className="auth-brand">
        <Link to="/" className="brand">
          <span className="brand-mark">A</span> Auto ATS
        </Link>
        <h2>Get your CV past the ATS</h2>
        <ul>
          {POINTS.map((point) => (
            <li key={point}>
              <Icon name="check" /> {point}
            </li>
          ))}
        </ul>
      </aside>
      <div className="auth-form">{children}</div>
    </div>
  )
}
