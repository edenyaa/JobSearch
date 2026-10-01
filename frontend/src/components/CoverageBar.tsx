export function CoverageBar({ label, percent }: { label: string; percent: number }) {
  return (
    <div className="coverage">
      <div className="coverage-label">
        <span>{label}</span>
        <strong>{percent}%</strong>
      </div>
      <div
        className="coverage-track"
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
      >
        <div className="coverage-fill" style={{ width: `${percent}%` }} />
      </div>
    </div>
  )
}
