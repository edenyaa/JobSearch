import { useState } from 'react'

interface Props {
  label: string
  confirmLabel?: string
  onConfirm: () => void
  disabled?: boolean
}

export function ConfirmButton({ label, confirmLabel = 'Confirm delete', onConfirm, disabled }: Props) {
  const [confirming, setConfirming] = useState(false)

  if (!confirming) {
    return (
      <button type="button" className="btn btn-danger-outline" disabled={disabled} onClick={() => setConfirming(true)}>
        {label}
      </button>
    )
  }

  return (
    <span className="confirm">
      <button
        type="button"
        className="btn btn-danger"
        onClick={() => {
          setConfirming(false)
          onConfirm()
        }}
      >
        {confirmLabel}
      </button>
      <button type="button" className="btn" onClick={() => setConfirming(false)}>
        Cancel
      </button>
    </span>
  )
}
