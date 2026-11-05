import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { messageOf } from '../api/client'
import { jobsApi } from '../api/endpoints'
import { JOB_STATUSES, STATUS_LABELS } from '../api/types'
import type { Job, JobStatus } from '../api/types'
import { useUserId } from '../auth/AuthContext'
import { ErrorMessage, ErrorState, Loading } from '../components/ErrorMessage'
import { Icon } from '../components/Icon'
import { useLoad } from '../hooks/useLoad'
import { levelOf } from './jobLevel'
import type { JobLevel } from './jobLevel'

type SortKey = 'fit' | 'newest' | 'oldest' | 'title'
type View = 'cards' | 'table'
type Tab = 'all' | JobLevel

const TABS: { id: Tab; label: string }[] = [
  { id: 'all', label: 'All jobs' },
  { id: 'student', label: 'Student & intern' },
  { id: 'junior', label: 'Junior & graduate' },
  { id: 'other', label: 'Other' },
]

function sortJobs(jobs: Job[], sort: SortKey): Job[] {
  const copy = [...jobs]
  if (sort === 'title') {
    return copy.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''))
  }
  if (sort === 'fit') {
    // best fit first; jobs without a fit score (added by hand) follow, newest first
    return copy.sort(
      (a, b) =>
        (b.fitScore ?? -1) - (a.fitScore ?? -1) ||
        (b.appliedDate ?? '').localeCompare(a.appliedDate ?? '') ||
        a.id - b.id,
    )
  }
  const direction = sort === 'newest' ? -1 : 1
  return copy.sort((a, b) => direction * ((a.appliedDate ?? '').localeCompare(b.appliedDate ?? '') || a.id - b.id))
}

function titleOf(job: Job) {
  return job.title ?? 'Untitled job'
}

function metaOf(job: Job) {
  return [job.company, job.location, job.jobNumber, job.appliedDate].filter(Boolean).join(' · ')
}

function FitTags({ job }: { job: Job }) {
  return (
    <>
      {job.fitScore !== null && (
        <span className={`tag ${job.fitScore >= 70 ? 'tag-good' : ''}`} title={job.fitNote ?? undefined}>
          Fit {job.fitScore}%
        </span>
      )}
      {job.priorityPlace && <span className="tag tag-good">Priority place</span>}
    </>
  )
}

function StatusSelect({ job, busy, onChange }: { job: Job; busy: boolean; onChange: (status: JobStatus) => void }) {
  return (
    <select
      className={`badge status-select badge-${job.status.toLowerCase()}`}
      aria-label={`Status of ${titleOf(job)}`}
      value={job.status}
      disabled={busy}
      onChange={(e) => onChange(e.target.value as JobStatus)}
    >
      {JOB_STATUSES.map((s) => (
        <option key={s} value={s}>
          {STATUS_LABELS[s]}
        </option>
      ))}
    </select>
  )
}

export function JobsPage() {
  const userId = useUserId()
  const [status, setStatus] = useState<JobStatus | ''>('')
  const [sort, setSort] = useState<SortKey>('fit')
  const [view, setView] = useState<View>('cards')
  const jobs = useLoad(() => jobsApi.list(userId, status || undefined), [userId, status])
  // jobs whose status was just changed here, so the list updates without a reload
  const [changed, setChanged] = useState<Record<number, Job>>({})
  const [savingId, setSavingId] = useState<number | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)
  const shown = useMemo(() => (jobs.data ?? []).map((j) => changed[j.id] ?? j), [jobs.data, changed])
  const [params, setParams] = useSearchParams()
  const tab: Tab = TABS.some((t) => t.id === params.get('level')) ? (params.get('level') as Tab) : 'all'
  const counts = useMemo(() => {
    const result: Record<Tab, number> = { all: shown.length, student: 0, junior: 0, other: 0 }
    shown.forEach((j) => {
      result[levelOf(j)]++
    })
    return result
  }, [shown])
  const sorted = useMemo(
    () => sortJobs(tab === 'all' ? shown : shown.filter((j) => levelOf(j) === tab), sort),
    [shown, sort, tab],
  )

  async function changeStatus(job: Job, next: JobStatus) {
    if (next === job.status) return
    setStatusError(null)
    setSavingId(job.id)
    try {
      const saved = await jobsApi.changeStatus(userId, job.id, next)
      setChanged((previous) => ({ ...previous, [job.id]: saved }))
    } catch (e) {
      setStatusError(`Could not change the status of ${titleOf(job)}: ${messageOf(e)}`)
    } finally {
      setSavingId(null)
    }
  }
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

      <div className="tabs" role="tablist" aria-label="Kind of job">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            className={`tab ${tab === t.id ? 'tab-active' : ''}`}
            onClick={() => setParams(t.id === 'all' ? {} : { level: t.id })}
          >
            {t.label} {jobs.loading ? '' : <span className="muted">({counts[t.id]})</span>}
          </button>
        ))}
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
            <option value="fit">Best fit first</option>
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

      <ErrorMessage message={statusError} />
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
                <StatusSelect job={job} busy={savingId === job.id} onChange={(next) => void changeStatus(job, next)} />
              </div>
              <div className="job-card-foot">
                <div className="tags">
                  <FitTags job={job} />
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
                <th>Fit</th>
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
                    <StatusSelect job={job} busy={savingId === job.id} onChange={(next) => void changeStatus(job, next)} />
                  </td>
                  <td>
                    <FitTags job={job} />
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
