import { useState } from 'react'
import type { ChangeEvent } from 'react'
import { Icon } from './Icon'

interface Props {
  id: string
  label: string
  accept: string
  onSelect: (file: File | undefined) => void
}

export function FileInput({ id, label, accept, onSelect }: Props) {
  const [fileName, setFileName] = useState<string | null>(null)

  function change(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    setFileName(file ? file.name : null)
    onSelect(file)
  }

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="file-row">
        <input id={id} type="file" className="file-native" accept={accept} onChange={change} />
        <label htmlFor={id} className="btn file-button">
          <Icon name="upload" /> Choose file
        </label>
        <span className="muted file-name">{fileName ?? 'No file chosen'}</span>
      </div>
    </div>
  )
}
