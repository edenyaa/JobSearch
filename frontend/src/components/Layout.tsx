import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Icon } from './Icon'
import type { IconName } from './Icon'

const LINKS: { to: string; label: string; icon: IconName }[] = [
  { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
  { to: '/jobs', label: 'Jobs', icon: 'jobs' },
  { to: '/skills', label: 'Skills', icon: 'skills' },
  { to: '/courses', label: 'Courses', icon: 'courses' },
  { to: '/profile', label: 'Profile', icon: 'profile' },
]

export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function signOut() {
    await logout()
    navigate('/login')
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">A</span> Auto ATS
        </div>
        <nav aria-label="Main">
          {LINKS.map((link) => (
            <NavLink key={link.to} to={link.to}>
              <Icon name={link.icon} />
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-user">
          <span className="avatar" aria-hidden="true">
            {user?.name.charAt(0).toUpperCase()}
          </span>
          <strong>{user?.name}</strong>
          <button type="button" className="btn btn-ghost" onClick={signOut}>
            <Icon name="logout" /> Sign out
          </button>
        </div>
      </aside>
      <main className="page">
        <Outlet />
      </main>
    </div>
  )
}
