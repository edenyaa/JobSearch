export interface Segment {
  label: string
  value: number
  color: string
}

interface DonutProps {
  segments: Segment[]
  ariaLabel: string
  centerValue?: string
  centerLabel?: string
  size?: number
  thickness?: number
  max?: number
}

export function DonutChart({ segments, ariaLabel, centerValue, centerLabel, size = 160, thickness = 16, max }: DonutProps) {
  const radius = (size - thickness) / 2
  const circumference = 2 * Math.PI * radius
  const sum = segments.reduce((total, segment) => total + segment.value, 0)
  const whole = max ?? sum
  const center = size / 2
  let offset = 0

  return (
    <svg className="donut" width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={ariaLabel}>
      <circle cx={center} cy={center} r={radius} fill="none" stroke="var(--track)" strokeWidth={thickness} />
      {whole > 0 &&
        segments
          .filter((segment) => segment.value > 0)
          .map((segment) => {
            const length = Math.min((segment.value / whole) * circumference, circumference)
            const arc = (
              <circle
                key={segment.label}
                cx={center}
                cy={center}
                r={radius}
                fill="none"
                stroke={segment.color}
                strokeWidth={thickness}
                strokeDasharray={`${length} ${circumference - length}`}
                strokeDashoffset={-offset}
                transform={`rotate(-90 ${center} ${center})`}
              />
            )
            offset += length
            return arc
          })}
      {centerValue !== undefined && (
        <text x={center} y={centerLabel ? center - 2 : center} textAnchor="middle" dominantBaseline="central" className="donut-value">
          {centerValue}
        </text>
      )}
      {centerLabel && (
        <text x={center} y={center + size * 0.15} textAnchor="middle" dominantBaseline="central" className="donut-label">
          {centerLabel}
        </text>
      )}
    </svg>
  )
}

export function ChartLegend({ segments }: { segments: Segment[] }) {
  const total = segments.reduce((sum, segment) => sum + segment.value, 0)
  return (
    <ul className="legend">
      {segments.map((segment) => (
        <li key={segment.label}>
          <span className="legend-dot" style={{ background: segment.color }} aria-hidden="true" />
          <span className="legend-label">{segment.label}</span>
          <strong>{segment.value}</strong>
          <span className="muted">{total === 0 ? '0%' : `${Math.round((segment.value / total) * 100)}%`}</span>
        </li>
      ))}
    </ul>
  )
}

export function StackedBar({ segments, ariaLabel }: { segments: Segment[]; ariaLabel: string }) {
  const total = segments.reduce((sum, segment) => sum + segment.value, 0)
  return (
    <div className="stacked" role="img" aria-label={ariaLabel}>
      {total > 0 &&
        segments
          .filter((segment) => segment.value > 0)
          .map((segment) => (
            <span key={segment.label} style={{ width: `${(segment.value / total) * 100}%`, background: segment.color }} />
          ))}
    </div>
  )
}

export const CATEGORY_COLORS = [
  '#c0743a',
  '#f2a516',
  '#3f9b5f',
  '#e5604f',
  '#e0a063',
  '#6bb5a0',
  '#d98aa0',
  '#8c6a4f',
  '#f4c95d',
  '#a65d28',
]
