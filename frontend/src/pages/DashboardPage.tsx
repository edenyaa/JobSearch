import { Link } from 'react-router-dom'
import { jobsApi, skillsApi } from '../api/endpoints'
import { JOB_STATUSES, STATUS_LABELS } from '../api/types'
import type { JobStatus } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ChartLegend, DonutChart } from '../components/charts'
import type { Segment } from '../components/charts'
import { ErrorState, Loading } from '../components/ErrorMessage'
import { Icon } from '../components/Icon'
import { StatusBadge } from '../components/StatusBadge'
import { useLoad } from '../hooks/useLoad'

const STATUS_COLORS: Record<JobStatus, string> = {
  SAVED: 'var(--st-saved)',
  APPLIED: 'var(--st-applied)',
  INTERVIEW: 'var(--st-interview)',
  OFFER: 'var(--st-offer)',
  REJECTED: 'var(--st-rejected)',
  WITHDRAWN: 'var(--st-withdrawn)',
}

export function DashboardPage() {
  const { user } = useAuth()
  const userId = user!.id
  const jobs = useLoad(() => jobsApi.list(userId), [userId])
  const skills = useLoad(() => skillsApi.list(userId), [userId])

  if (jobs.loading || skills.loading) return <Loading />
  if (jobs.error) {
    return (
      <ErrorState
        title="We could not load your dashboard"
        message={jobs.error}
        onRetry={() => {
          jobs.reload()
          skills.reload()
        }}
      />
    )
  }

  const allJobs = jobs.data ?? []
  const count = (status: string) => allJobs.filter((job) => job.status === status).length
  const recent = allJobs.slice(0, 5)
  const firstJob = allJobs[0]
  const firstName = user!.name.split(' ')[0]

  const steps = [
    { label: 'Upload your main CV', to: '/profile', done: user!.hasMainCv, hint: 'We pick your skills out of it.' },
    { label: 'Review the skills we found', to: '/skills', done: (skills.data ?? []).length > 0, hint: 'Edit, add keywords, remove mistakes.' },
    { label: 'Add a job you are applying to', to: '/jobs/new', done: allJobs.length > 0, hint: 'Paste the job description.' },
    {
      label: 'Analyze it and tailor your CV',
      to: firstJob ? `/jobs/${firstJob.id}/analysis` : '/jobs/new',
      done: allJobs.some((job) => job.hasTailoredResume),
      hint: 'See your match in charts, then let the AI rewrite your CV.',
    },
  ]
  const doneCount = steps.filter((step) => step.done).length

  const pipeline: Segment[] = JOB_STATUSES.map((status) => ({
    label: STATUS_LABELS[status],
    value: count(status),
    color: STATUS_COLORS[status],
  }))

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Dashboard</h1>
          <p className="muted">Welcome back, {firstName}.</p>
        </div>
        <Link to="/jobs/new" className="btn btn-primary">
          Add job
        </Link>
      </div>

      {doneCount < steps.length && (
        <section className="card checklist">
          <div className="row-between">
            <h2>Get started</h2>
            <span className="muted">
              {doneCount} of {steps.length} done
            </span>
          </div>
          <div className="progress" role="progressbar" aria-label="Setup progress" aria-valuemin={0} aria-valuemax={steps.length} aria-valuenow={doneCount}>
            <div className="progress-fill" style={{ width: `${(doneCount / steps.length) * 100}%` }} />
          </div>
          <ol>
            {steps.map((step) => (
              <li key={step.label} className={step.done ? 'step-done' : ''}>
                <span className="step-check" aria-hidden="true">
                  {step.done && <Icon name="check" size={14} />}
                </span>
                <div className="grow">
                  <Link to={step.to}>{step.label}</Link>
                  <div className="muted">{step.hint}</div>
                </div>
                {step.done && <span className="muted">Done</span>}
              </li>
            ))}
          </ol>
        </section>
      )}

      <div className="stats">
        <div className="card stat">
          <span className="stat-number">{allJobs.length}</span>
          <span className="muted">Total jobs</span>
        </div>
        <div className="card stat">
          <span className="stat-number">{count('INTERVIEW')}</span>
          <span className="muted">Interviews</span>
        </div>
        <div className="card stat">
          <span className="stat-number">{count('OFFER')}</span>
          <span className="muted">Offers</span>
        </div>
        <div className="card stat">
          <span className="stat-number">{count('REJECTED')}</span>
          <span className="muted">Rejections</span>
        </div>
      </div>

      <div className="grid-2 charts-row">
        <section className="card">
          <h2>Your pipeline</h2>
          {allJobs.length === 0 ? (
            <p className="muted">No jobs yet. Add the first one you are applying to.</p>
          ) : (
            <div className="chart-with-legend">
              <DonutChart
                segments={pipeline}
                centerValue={String(allJobs.length)}
                centerLabel="jobs"
                ariaLabel={`Jobs by status: ${pipeline.filter((s) => s.value > 0).map((s) => `${s.label} ${s.value}`).join(', ')}`}
              />
              <ChartLegend segments={pipeline.filter((segment) => segment.value > 0)} />
            </div>
          )}
        </section>

        <section className="card">
          <div className="row-between">
            <h2>Recent jobs</h2>
            <Link to="/jobs">See all</Link>
          </div>
          {recent.length === 0 ? (
            <p className="muted">Nothing here yet.</p>
          ) : (
            <ul className="list compact">
              {recent.map((job) => (
                <li key={job.id} className="list-item">
                  <div className="grow">
                    <Link to={`/jobs/${job.id}`}>{job.title ?? 'Untitled job'}</Link>
                    <div className="muted">{job.company}</div>
                  </div>
                  <StatusBadge status={job.status} />
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
