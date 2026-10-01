import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { jobsApi } from '../api/endpoints'
import { JOB_STATUSES, STATUS_LABELS } from '../api/types'
import type { Job, JobStatus } from '../api/types'
import { useUserId } from '../auth/AuthContext'
import { ErrorState, Loading } from '../components/ErrorMessage'
import { Icon } from '../components/Icon'
import { StatusBadge } from '../components/StatusBadge'
import { useLoad } from '../hooks/useLoad'

type SortKey = 'newest' | 'oldest' | 'title'
type View = 'cards' | 'table'

function sortJobs(jobs: Job[], sort: SortKey): Job[] {
  const copy = [...jobs]
  if (sort === 'title') {
    return copy.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''))
  }
  const direction = sort === 'newest' ? -1 : 1
  return copy.sort((a, b) => direction * ((a.appliedDate ?? '').localeCompare(b.appliedDate ?? '') || a.id - b.id))
}

function titleOf(job: Job) {
  return job.title ?? 'Untitled job'
}

function metaOf(job: Job) {
  return [job.company, job.jobNumber, job.appliedDate].filter(Boolean).join(' · ')
}

export function JobsPage() {
  const userId = useUserId()
  const [status, setStatus] = useState<JobStatus | ''>('')
  const [sort, setSort] = useState<SortKey>('newest')
  const [view, setView] = useState<View>('cards')
  const jobs = useLoad(() => jobsApi.list(userId, status || undefined), [userId, status])
  const sorted = useMemo(() => sortJobs(jobs.data ?? [], sort), [jobs.data, sort])
  const count = sorted.length

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Jobs</h1>
          <p className="muted">Track every application, analyze the posting and tailor your CV.</p>
        </div>
        <Link to="/jobs/new" className="btn btn-primary">
          <Icon name="plus" size={16} /> Add job
        </Link>
      </div>

      <div className="toolbar">
        <div className="field inline">
          <label htmlFor="status-filter" className="sr-only">
            Filter by status
          </label>
          <select id="status-filter" value={status} onChange={(e) => setStatus(e.target.value as JobStatus | '')}>
            <option value="">All</option>
            {JOB_STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
        <div className="field inline">
          <label htmlFor="sort-jobs" className="sr-only">
            Sort jobs
          </label>
          <select id="sort-jobs" value={sort} onChange={(e) => setSort(e.target.value as SortKey)}>
            <option value="newest">Newest first</option>
            <option value="oldest">Oldest first</option>
            <option value="title">Title A to Z</option>
          </select>
        </div>
        <span className="toolbar-count muted">{jobs.loading ? '' : `${count} ${count === 1 ? 'job' : 'jobs'}`}</span>
        <div className="segmented" role="group" aria-label="View">
          <button type="button" aria-pressed={view === 'cards'} aria-label="Card view" onClick={() => setView('cards')}>
            <Icon name="grid" size={16} />
          </button>
          <button type="button" aria-pressed={view === 'table'} aria-label="Table view" onClick={() => setView('table')}>
            <Icon name="list" size={16} />
          </button>
        </div>
      </div>

      {jobs.loading && <Loading />}
      <ErrorState title="We could not load your jobs" message={jobs.error} onRetry={jobs.reload} />
      {!jobs.loading && !jobs.error && count === 0 && <p className="muted empty-note">No jobs found.</p>}

      {view === 'cards' && (
        <ul className="list job-cards">
          {sorted.map((job) => (
            <li key={job.id} className="card job-card">
              <div className="job-card-main">
                <div className="grow">
                  <Link to={`/jobs/${job.id}`} className="job-title">
                    {titleOf(job)}
                  </Link>
                  <div className="muted">{metaOf(job)}</div>
                </div>
                <StatusBadge status={job.status} />
              </div>
              <div className="job-card-foot">
                <div className="tags">
                  {job.hasTailoredResume && <span className="tag tag-good">Tailored CV ready</span>}
                </div>
                <div className="quick-actions">
                  <Link to={`/jobs/${job.id}/analysis`} className="btn btn-ghost" aria-label={`Analysis for ${titleOf(job)}`}>
                    <Icon name="chart" size={16} /> Analysis
                  </Link>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {view === 'table' && count > 0 && (
        <div className="card table-card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Job</th>
                <th>Details</th>
                <th>Status</th>
                <th>CV</th>
                <th>
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((job) => (
                <tr key={job.id}>
                  <td>
                    <Link to={`/jobs/${job.id}`} className="job-title">
                      {titleOf(job)}
                    </Link>
                  </td>
                  <td className="muted">{metaOf(job)}</td>
                  <td>
                    <StatusBadge status={job.status} />
                  </td>
                  <td>{job.hasTailoredResume ? <span className="tag tag-good">Tailored CV ready</span> : <span className="muted">-</span>}</td>
                  <td>
                    <Link to={`/jobs/${job.id}/analysis`} className="btn btn-ghost" aria-label={`Analysis for ${titleOf(job)}`}>
                      Analysis
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
