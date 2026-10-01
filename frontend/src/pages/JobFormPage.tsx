import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { messageOf } from '../api/client'
import { jobsApi } from '../api/endpoints'
import { JOB_STATUSES, STATUS_LABELS } from '../api/types'
import type { JobInput, JobStatus } from '../api/types'
import { useUserId } from '../auth/AuthContext'
import { ErrorMessage, Loading } from '../components/ErrorMessage'

interface FormState {
  title: string
  company: string
  jobNumber: string
  link: string
  appliedDate: string
  status: JobStatus
  description: string
  notes: string
}

const EMPTY: FormState = {
  title: '',
  company: '',
  jobNumber: '',
  link: '',
  appliedDate: '',
  status: 'APPLIED',
  description: '',
  notes: '',
}

function orUndefined(value: string): string | undefined {
  const trimmed = value.trim()
  return trimmed === '' ? undefined : trimmed
}

export function toJobInput(form: FormState): JobInput {
  return {
    title: orUndefined(form.title),
    company: orUndefined(form.company),
    jobNumber: orUndefined(form.jobNumber),
    link: orUndefined(form.link),
    appliedDate: orUndefined(form.appliedDate),
    status: form.status,
    description: form.description.trim(),
    notes: form.notes,
  }
}

export function JobFormPage() {
  const userId = useUserId()
  const navigate = useNavigate()
  const { jobId } = useParams()
  const editing = jobId !== undefined
  const [form, setForm] = useState<FormState>(EMPTY)
  const [loading, setLoading] = useState(editing)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!editing) return
    let active = true
    jobsApi
      .get(userId, Number(jobId))
      .then((job) => {
        if (!active) return
        setForm({
          title: job.title ?? '',
          company: job.company ?? '',
          jobNumber: job.jobNumber,
          link: job.link ?? '',
          appliedDate: job.appliedDate,
          status: job.status,
          description: job.description,
          notes: job.notes ?? '',
        })
        setLoading(false)
      })
      .catch((e) => {
        if (!active) return
        setError(messageOf(e))
        setLoading(false)
      })
    return () => {
      active = false
    }
  }, [editing, jobId, userId])

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (form.description.trim() === '') {
      setError('The job description is required.')
      return
    }
    setBusy(true)
    try {
      const input = toJobInput(form)
      const saved = editing ? await jobsApi.update(userId, Number(jobId), input) : await jobsApi.create(userId, input)
      navigate(`/jobs/${saved.id}`)
    } catch (e) {
      setError(messageOf(e))
      setBusy(false)
    }
  }

  if (loading) return <Loading />

  return (
    <div>
      <h1>{editing ? 'Edit job' : 'Add job'}</h1>
      <form className="card form" onSubmit={submit}>
        <div className="grid-2">
          <div className="field">
            <label htmlFor="title">Job title</label>
            <input id="title" value={form.title} onChange={(e) => set('title', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="company">Company</label>
            <input id="company" value={form.company} onChange={(e) => set('company', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="jobNumber">Job number</label>
            <input id="jobNumber" value={form.jobNumber} onChange={(e) => set('jobNumber', e.target.value)} />
            <small className="muted">Leave empty and one is generated for you.</small>
          </div>
          <div className="field">
            <label htmlFor="link">Link to the posting</label>
            <input id="link" type="url" value={form.link} onChange={(e) => set('link', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="appliedDate">Date</label>
            <input id="appliedDate" type="date" value={form.appliedDate} onChange={(e) => set('appliedDate', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="status">Status</label>
            <select id="status" value={form.status} onChange={(e) => set('status', e.target.value as JobStatus)}>
              {JOB_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABELS[s]}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="field">
          <label htmlFor="description">Job description</label>
          <textarea id="description" rows={10} value={form.description} onChange={(e) => set('description', e.target.value)} />
          <small className="muted">Paste the full posting. The keyword match and the AI tailoring read this text.</small>
        </div>
        <div className="field">
          <label htmlFor="notes">Notes</label>
          <textarea id="notes" rows={3} value={form.notes} onChange={(e) => set('notes', e.target.value)} />
        </div>
        <ErrorMessage message={error} />
        <div className="actions">
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Save job'}
          </button>
          <button type="button" className="btn" onClick={() => navigate(-1)}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  )
}
