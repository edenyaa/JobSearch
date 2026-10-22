import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { CommandPalette } from './CommandPalette'
import { Icon } from './Icon'
import type { IconName } from './Icon'

export const NAV_LINKS: { to: string; label: string; icon: IconName }[] = [
  { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
  { to: '/jobs', label: 'Jobs', icon: 'jobs' },
  { to: '/skills', label: 'Skills', icon: 'skills' },
  { to: '/companies', label: 'Companies', icon: 'companies' },
  { to: '/courses', label: 'Courses', icon: 'courses' },
  { to: '/profile', label: 'Profile', icon: 'profile' },
]

export function crumbsFor(pathname: string): string[] {
  const parts = pathname.split('/').filter(Boolean)
  if (parts.length === 0) return ['Dashboard']
  const section = NAV_LINKS.find((link) => link.to === `/${parts[0]}`)?.label ?? 'Page'
  const crumbs = [section]
  if (parts[0] === 'jobs' && parts[1] === 'new') crumbs.push('New job')
  else if (parts[0] === 'jobs' && parts[1]) crumbs.push(parts[2] === 'analysis' ? 'Analysis' : 'Job details')
  return crumbs
}

function useDismiss(open: boolean, close: () => void) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    function onPointer(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) close()
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') close()
    }
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open, close])
  return ref
}

export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [bellOpen, setBellOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const menuRef = useDismiss(menuOpen, () => setMenuOpen(false))
  const bellRef = useDismiss(bellOpen, () => setBellOpen(false))
  const crumbs = crumbsFor(location.pathname)

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setSearchOpen(true)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  async function signOut() {
    await logout()
    navigate('/login')
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <Link to="/dashboard" className="brand">
          <span className="brand-mark">
            <Icon name="target" size={16} />
          </span>
          <span className="brand-text">
            Auto <b>ATS</b>
          </span>
        </Link>
        <nav aria-label="Main">
          {NAV_LINKS.map((link) => (
            <NavLink key={link.to} to={link.to}>
              <Icon name={link.icon} />
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="user-card" ref={menuRef}>
          <button
            type="button"
            className="user-card-button"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            aria-label="Account menu"
            onClick={() => setMenuOpen((open) => !open)}
          >
            <span className="avatar" aria-hidden="true">
              {user?.name.charAt(0).toUpperCase()}
            </span>
            <span className="user-meta">
              <strong>{user?.name}</strong>
              <small>{user?.email}</small>
            </span>
            <Icon name="chevron" size={16} />
          </button>
          {menuOpen && (
            <div className="menu" role="menu">
              <Link to="/profile" role="menuitem" onClick={() => setMenuOpen(false)}>
                <Icon name="profile" /> Account settings
              </Link>
              <button type="button" role="menuitem" onClick={signOut}>
                <Icon name="logout" /> Sign out
              </button>
            </div>
          )}
        </div>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <nav className="crumbs" aria-label="Breadcrumb">
            <span>Workspace</span>
            {crumbs.map((crumb, index) => (
              <span key={crumb} className={index === crumbs.length - 1 ? 'crumb-current' : undefined}>
                <span className="crumb-sep" aria-hidden="true">
                  /
                </span>
                {crumb}
              </span>
            ))}
          </nav>
          <div className="topbar-actions">
            <button type="button" className="search-trigger" onClick={() => setSearchOpen(true)} aria-label="Search">
              <Icon name="search" size={16} />
              <span>Search</span>
              <kbd>Ctrl K</kbd>
            </button>
            <div className="popover-anchor" ref={bellRef}>
              <button
                type="button"
                className="icon-button"
                aria-label="Notifications"
                aria-expanded={bellOpen}
                onClick={() => setBellOpen((open) => !open)}
              >
                <Icon name="bell" />
              </button>
              {bellOpen && (
                <div className="popover" role="dialog" aria-label="Notifications">
                  <strong>Notifications</strong>
                  <p className="muted">You are all caught up.</p>
                </div>
              )}
            </div>
          </div>
        </header>
        <main className="page">
          <Outlet />
        </main>
      </div>

      {searchOpen && <CommandPalette onClose={() => setSearchOpen(false)} />}
    </div>
  )
}
