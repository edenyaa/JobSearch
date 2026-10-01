import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { messageOf, saveBlob } from '../api/client'
import { jobsApi } from '../api/endpoints'
import { JOB_STATUSES, STATUS_LABELS } from '../api/types'
import type { Job, JobStatus, TailorResult } from '../api/types'
import { useUserId } from '../auth/AuthContext'
import { ConfirmButton } from '../components/ConfirmButton'
import { CoverageBar } from '../components/CoverageBar'
import { ErrorMessage, Loading } from '../components/ErrorMessage'
import { FileInput } from '../components/FileInput'
import { Icon } from '../components/Icon'
import { StatusBadge } from '../components/StatusBadge'
import { useLoad } from '../hooks/useLoad'

function TermList({ title, terms, tone }: { title: string; terms: string[]; tone: 'good' | 'bad' | 'warn' }) {
  if (terms.length === 0) return null
  return (
    <div className="terms">
      <strong>{title}</strong>
      <div className="chips">
        {terms.map((term) => (
          <span key={term} className={`chip chip-${tone}`}>
            {term}
          </span>
        ))}
      </div>
    </div>
  )
}

export function JobDetailPage() {
  const userId = useUserId()
  const navigate = useNavigate()
  const jobId = Number(useParams().jobId)
  const { data: job, error, loading, setData } = useLoad(() => jobsApi.get(userId, jobId), [userId, jobId])

  const [notes, setNotes] = useState('')
  const [tailor, setTailor] = useState<TailorResult | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState<'tailor' | 'notes' | 'upload' | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    if (job) setNotes(job.notes ?? '')
  }, [job])

  if (loading) return <Loading />
  if (error || !job) return <ErrorMessage message={error ?? 'Job not found.'} />

  async function run<T>(kind: NonNullable<typeof busy>, action: () => Promise<T>): Promise<T | undefined> {
    setActionError(null)
    setBusy(kind)
    try {
      return await action()
    } catch (e) {
      setActionError(messageOf(e))
      return undefined
    } finally {
      setBusy(null)
    }
  }

  async function changeStatus(status: JobStatus) {
    const updated = await run('notes', () => jobsApi.changeStatus(userId, jobId, status))
    if (updated) setData(updated)
  }

  async function saveNotes() {
    const current = job as Job
    const updated = await run('notes', () =>
      jobsApi.update(userId, jobId, {
        jobNumber: current.jobNumber,
        title: current.title ?? undefined,
        company: current.company ?? undefined,
        description: current.description,
        link: current.link ?? undefined,
        appliedDate: current.appliedDate,
        status: current.status,
        notes,
      }),
    )
    if (updated) {
      setData(updated)
      setSaved(true)
    }
  }

  async function tailorCv() {
    const result = await run('tailor', () => jobsApi.tailor(userId, jobId))
    if (result) {
      setTailor(result)
      setData(result.job)
    }
  }

  async function downloadResume() {
    const file = await run('upload', () => jobsApi.downloadResume(userId, jobId))
    if (file) saveBlob(file)
  }

  async function uploadResume(file: File | undefined) {
    if (!file) return
    const updated = await run('upload', () => jobsApi.uploadResume(userId, jobId, file))
    if (updated) setData(updated)
  }

  async function remove() {
    const done = await run('notes', async () => {
      await jobsApi.remove(userId, jobId)
      return true
    })
    if (done) navigate('/jobs')
  }

  return (
    <div>
      <div className="row-between">
        <div>
          <h1>{job.title ?? 'Untitled job'}</h1>
          <p className="muted">
            {[job.company, `#${job.jobNumber}`, job.appliedDate].filter(Boolean).join(' · ')}
            {job.link && (
              <>
                {' · '}
                <a href={job.link} target="_blank" rel="noopener noreferrer">
                  Open posting
                </a>
              </>
            )}
          </p>
        </div>
        <div className="actions">
          <StatusBadge status={job.status} />
          <Link to={`/jobs/${job.id}/edit`} className="btn">
            Edit
          </Link>
        </div>
      </div>

      <ErrorMessage message={actionError} />

      <section className="card">
        <div className="field inline">
          <label htmlFor="status">Status</label>
          <select id="status" value={job.status} onChange={(e) => changeStatus(e.target.value as JobStatus)}>
            {JOB_STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
        <h2>Description</h2>
        <p className="prewrap">{job.description}</p>
      </section>

      <section className="card">
        <h2>Notes</h2>
        <div className="field">
          <label htmlFor="notes">Notes</label>
          <textarea
            id="notes"
            rows={3}
            value={notes}
            onChange={(e) => {
              setNotes(e.target.value)
              setSaved(false)
            }}
          />
        </div>
        <div className="actions">
          <button type="button" className="btn" onClick={saveNotes} disabled={busy === 'notes'}>
            Save notes
          </button>
          {saved && <span className="muted">Notes saved</span>}
        </div>
      </section>

      <section className="card analysis-card">
        <div className="grow">
          <h2>Analysis</h2>
          <p className="muted">
            See which skills this job asks for, which ones your CV shows, and what is missing, with charts for each skill category.
          </p>
        </div>
        <Link to={`/jobs/${job.id}/analysis`} className="btn btn-primary">
          <Icon name="chart" /> Open analysis
        </Link>
      </section>

      <section className="card">
        <h2>Tailored CV</h2>
        <p className="muted">
          The AI rewrites your main CV for this job using only facts from your CV, skills and courses. It can take up to a
          minute.
        </p>
        <div className="actions">
          <button type="button" className="btn btn-primary" onClick={tailorCv} disabled={busy === 'tailor'}>
            {busy === 'tailor' ? 'Tailoring… this can take a minute' : 'Tailor my CV with AI'}
          </button>
          {job.hasTailoredResume && (
            <button type="button" className="btn" onClick={downloadResume} disabled={busy === 'upload'}>
              Download tailored CV
            </button>
          )}
        </div>
        {job.hasTailoredResume && <p className="muted">Current file: {job.tailoredResumeFileName}</p>}

        {tailor && (
          <div className="result">
            <CoverageBar label="Coverage before" percent={tailor.coverageBefore} />
            <CoverageBar label="Coverage after" percent={tailor.coverageAfter} />
            <TermList title="Still missing" terms={tailor.missingTerms} tone="bad" />
            {tailor.unsupportedSkills.length > 0 && (
              <div className="warning" role="alert">
                <strong>Check these skills before you send the CV.</strong> The AI added them, but they are not in your CV
                or your skills list:
                <TermList title="" terms={tailor.unsupportedSkills} tone="warn" />
              </div>
            )}
          </div>
        )}

        <FileInput id="own-resume" label="Upload your own tailored resume" accept=".pdf,.docx,.txt" onSelect={uploadResume} />
      </section>

      <section className="danger-zone">
        <ConfirmButton label="Delete job" onConfirm={remove} />
      </section>
    </div>
  )
}
