import { useEffect, useMemo, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Icon } from './Icon'
import type { IconName } from './Icon'

interface Command {
  label: string
  to: string
  icon: IconName
}

const COMMANDS: Command[] = [
  { label: 'Go to Dashboard', to: '/dashboard', icon: 'dashboard' },
  { label: 'Go to Jobs', to: '/jobs', icon: 'jobs' },
  { label: 'Add a job', to: '/jobs/new', icon: 'plus' },
  { label: 'Go to Skills', to: '/skills', icon: 'skills' },
  { label: 'Go to Courses', to: '/courses', icon: 'courses' },
  { label: 'Go to Profile', to: '/profile', icon: 'profile' },
]

export function CommandPalette({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate()
  const input = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)

  const matches = useMemo(
    () => COMMANDS.filter((command) => command.label.toLowerCase().includes(query.trim().toLowerCase())),
    [query],
  )

  useEffect(() => {
    input.current?.focus()
  }, [])

  function go(command: Command) {
    onClose()
    navigate(command.to)
  }

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      onClose()
    } else if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive((index) => Math.min(index + 1, matches.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((index) => Math.max(index - 1, 0))
    } else if (event.key === 'Enter' && matches[active]) {
      go(matches[active])
    }
  }

  return (
    <div className="overlay" onMouseDown={onClose}>
      <div
        className="palette"
        role="dialog"
        aria-modal="true"
        aria-label="Search"
        onMouseDown={(event) => event.stopPropagation()}
        onKeyDown={onKeyDown}
      >
        <div className="palette-input">
          <Icon name="search" />
          <input
            ref={input}
            value={query}
            placeholder="Search pages and actions"
            aria-label="Search pages and actions"
            onChange={(event) => {
              setQuery(event.target.value)
              setActive(0)
            }}
          />
        </div>
        <ul role="listbox" aria-label="Results">
          {matches.map((command, index) => (
            <li key={command.to} role="option" aria-selected={index === active}>
              <button type="button" className={index === active ? 'active' : undefined} onClick={() => go(command)}>
                <Icon name={command.icon} /> {command.label}
              </button>
            </li>
          ))}
          {matches.length === 0 && <li className="muted palette-empty">No results</li>}
        </ul>
      </div>
    </div>
  )
}
