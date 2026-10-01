import { STATUS_LABELS } from '../api/types'
import type { JobStatus } from '../api/types'

export function StatusBadge({ status }: { status: JobStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{STATUS_LABELS[status]}</span>
}
