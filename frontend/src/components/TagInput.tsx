import { useId, useState } from 'react'
import type { KeyboardEvent } from 'react'

interface Props {
  label: string
  value: string[]
  onChange: (value: string[]) => void
  placeholder?: string
}

export function TagInput({ label, value, onChange, placeholder }: Props) {
  const id = useId()
  const [draft, setDraft] = useState('')

  function commit() {
    const cleaned = draft.trim().replace(/,$/, '').trim()
    if (cleaned && !value.some((tag) => tag.toLowerCase() === cleaned.toLowerCase())) {
      onChange([...value, cleaned])
    }
    setDraft('')
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      commit()
    } else if (event.key === 'Backspace' && draft === '' && value.length > 0) {
      onChange(value.slice(0, -1))
    }
  }

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="tags">
        {value.map((tag) => (
          <span key={tag} className="tag">
            {tag}
            <button type="button" aria-label={`Remove ${tag}`} onClick={() => onChange(value.filter((t) => t !== tag))}>
              ×
            </button>
          </span>
        ))}
        <input
          id={id}
          value={draft}
          placeholder={placeholder}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
          onBlur={commit}
        />
      </div>
    </div>
  )
}
