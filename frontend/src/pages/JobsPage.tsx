import { useState } from 'react'
import { Link } from 'react-router-dom'
import { jobsApi } from '../api/endpoints'
import { JOB_STATUSES, STATUS_LABELS } from '../api/types'
import type { JobStatus } from '../api/types'
import { useUserId } from '../auth/AuthContext'
import { ErrorMessage, Loading } from '../components/ErrorMessage'
import { StatusBadge } from '../components/StatusBadge'
import { useLoad } from '../hooks/useLoad'

export function JobsPage() {
  const userId = useUserId()
  const [status, setStatus] = useState<JobStatus | ''>('')
  const jobs = useLoad(() => jobsApi.list(userId, status || undefined), [userId, status])

  return (
    <div>
      <div className="row-between">
        <h1>Jobs</h1>
        <Link to="/jobs/new" className="btn btn-primary">
          Add job
        </Link>
      </div>

      <div className="field inline">
        <label htmlFor="status-filter">Filter by status</label>
        <select id="status-filter" value={status} onChange={(e) => setStatus(e.target.value as JobStatus | '')}>
          <option value="">All</option>
          {JOB_STATUSES.map((s) => (
            <option key={s} value={s}>
              {STATUS_LABELS[s]}
            </option>
          ))}
        </select>
      </div>

      {jobs.loading && <Loading />}
      <ErrorMessage message={jobs.error} />
      {!jobs.loading && !jobs.error && (jobs.data ?? []).length === 0 && <p className="muted">No jobs found.</p>}
      <ul className="list">
        {(jobs.data ?? []).map((job) => (
          <li key={job.id} className="card list-item">
            <div className="grow">
              <Link to={`/jobs/${job.id}`}>{job.title ?? 'Untitled job'}</Link>
              <div className="muted">
                {[job.company, job.jobNumber, job.appliedDate].filter(Boolean).join(' · ')}
              </div>
            </div>
            {job.hasTailoredResume && <span className="muted">Tailored CV ready</span>}
            <Link to={`/jobs/${job.id}/analysis`} className="btn btn-ghost" aria-label={`Analysis for ${job.title ?? 'Untitled job'}`}>
              Analysis
            </Link>
            <StatusBadge status={job.status} />
          </li>
        ))}
      </ul>
    </div>
  )
}
