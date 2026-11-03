import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { messageOf } from '../api/client'
import { searchApi } from '../api/endpoints'
import type { SearchSettingsInput } from '../api/types'
import { useUserId } from '../auth/AuthContext'
import { ConfirmButton } from '../components/ConfirmButton'
import { ErrorMessage, Loading } from '../components/ErrorMessage'
import { TagInput } from '../components/TagInput'
import { useLoad } from '../hooks/useLoad'

const POLL_MILLIS = 3000

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString()
}

export function SearchPage() {
  const userId = useUserId()
  const settings = useLoad(() => searchApi.get(userId), [userId])
  const companies = useLoad(() => searchApi.companies(userId), [userId])
  const [form, setForm] = useState<SearchSettingsInput | null>(null)
  const [company, setCompany] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)
  const [adding, setAdding] = useState(false)
  const wasRunning = useRef(false)

  const data = settings.data
  const running = data?.running ?? false
  const importing = data?.importing ?? false
  const [names, setNames] = useState('')

  useEffect(() => {
    if (data && form === null) {
      setForm({
        titles: data.titles,
        excludedTitles: data.excludedTitles,
        locations: data.locations,
        priorityLocations: data.priorityLocations,
        minMatchPercent: data.minMatchPercent,
        minFitPercent: data.minFitPercent,
        daily: data.daily,
      })
    }
  }, [data, form])

  // While a search runs, keep asking for its state; when it ends the new companies are listed too.
  useEffect(() => {
    if (running || importing) {
      const timer = setTimeout(settings.reload, POLL_MILLIS)
      wasRunning.current = true
      return () => clearTimeout(timer)
    }
    if (wasRunning.current) {
      wasRunning.current = false
      companies.reload()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [running, importing, data])

  function set<K extends keyof SearchSettingsInput>(key: K, value: SearchSettingsInput[K]) {
    setSaved(false)
    setForm((previous) => (previous ? { ...previous, [key]: value } : previous))
  }

  async function save(): Promise<boolean> {
    if (!form) return false
    setError(null)
    try {
      settings.setData(await searchApi.update(userId, form))
      setSaved(true)
      return true
    } catch (e) {
      setError(messageOf(e))
      return false
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    await save()
    setBusy(false)
  }

  async function runNow() {
    setBusy(true)
    if (await save()) {
      try {
        settings.setData(await searchApi.run(userId))
      } catch (e) {
        setError(messageOf(e))
      }
    }
    setBusy(false)
  }

  async function resetSettings() {
    setBusy(true)
    setError(null)
    try {
      const reset = await searchApi.reset(userId)
      settings.setData(reset)
      setForm(null) // reloaded from the reset settings by the effect above
      setSaved(false)
    } catch (e) {
      setError(messageOf(e))
    }
    setBusy(false)
  }

  async function addCompany(event: FormEvent) {
    event.preventDefault()
    if (company.trim() === '') return
    setError(null)
    setAdding(true)
    try {
      await searchApi.addCompany(userId, company.trim())
      setCompany('')
      companies.reload()
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setAdding(false)
    }
  }

  async function importNames(list: string[]) {
    setError(null)
    try {
      settings.setData(await searchApi.importCompanies(userId, list))
      setNames('')
    } catch (e) {
      setError(messageOf(e))
    }
  }

  function importPasted() {
    const list = names
      .split(/[\n,;]+/)
      .map((n) => n.trim())
      .filter(Boolean)
    if (list.length > 0) void importNames(list)
  }

  async function removeCompany(id: number) {
    try {
      await searchApi.removeCompany(userId, id)
      companies.reload()
    } catch (e) {
      setError(messageOf(e))
    }
  }

  if (!data || !form) {
    return settings.error ? <ErrorMessage message={settings.error} /> : <Loading />
  }

  return (
    <div>
      <h1>Job search</h1>
      <p className="muted">
        Finds openings on job boards and on your companies' career pages, and saves the ones that fit your skills as{' '}
        <strong>Saved</strong> jobs with their link. You apply yourself and change the status to Applied.
      </p>

      <div className="card column">
        <div className="row-between">
          <div>
            <strong>{running ? 'Searching…' : 'Search now'}</strong>
            <p className="muted">
              {running
                ? 'This takes a minute or two. You can leave this page; the search keeps going.'
                : data.lastRunAt
                  ? `Last search: ${formatTime(data.lastRunAt)}. ${data.lastRunSummary ?? ''}`
                  : 'No search has run yet.'}
            </p>
          </div>
          <button type="button" className="btn btn-primary" disabled={busy || running} onClick={runNow}>
            {running ? 'Searching…' : 'Run search now'}
          </button>
        </div>
      </div>

      <div className="card column">
        <h2>Where it looks</h2>
        <ul className="bullets">
          <li>Remote job boards (Remotive, Arbeitnow).</li>
          <li>The career pages of the companies below (Greenhouse, Lever, Ashby, or any careers page that lists jobs).</li>
          <li>
            {data.aggregatorEnabled ? (
              <>LinkedIn, Glassdoor and Indeed listings are included, through JSearch.</>
            ) : (
              <>
                LinkedIn, Glassdoor and Indeed listings: off. To turn them on, add a JSearch (RapidAPI) key as{' '}
                <code>JSEARCH_API_KEY</code> on the server.
              </>
            )}
          </li>
        </ul>
      </div>

      <form className="card form" onSubmit={submit}>
        <h2>What to look for</h2>
        <TagInput
          label="Job titles to search"
          value={form.titles}
          onChange={(value) => set('titles', value)}
          placeholder="e.g. backend, qa engineer"
        />
        <TagInput
          label="Skip titles containing"
          value={form.excludedTitles}
          onChange={(value) => set('excludedTitles', value)}
          placeholder="e.g. senior, manager"
        />
        <TagInput
          label="Places (only jobs here are saved)"
          value={form.locations}
          onChange={(value) => set('locations', value)}
          placeholder="e.g. israel, tel aviv (empty means anywhere)"
        />
        <TagInput
          label="Priority places (ranked first)"
          value={form.priorityLocations}
          onChange={(value) => set('priorityLocations', value)}
          placeholder="e.g. rehovot"
        />
        <div className="field">
          <label htmlFor="min-match">Minimum skill match (%)</label>
          <input
            id="min-match"
            type="number"
            min={0}
            max={100}
            value={form.minMatchPercent}
            onChange={(e) => set('minMatchPercent', Number(e.target.value))}
          />
          <small className="muted">
            Of the skills in your profile that an opening mentions, this share must be in your CV.
          </small>
        </div>
        <div className="field">
          <label htmlFor="min-fit">Minimum fit rated by the AI (%)</label>
          <input
            id="min-fit"
            type="number"
            min={0}
            max={100}
            value={form.minFitPercent}
            onChange={(e) => set('minFitPercent', Number(e.target.value))}
          />
          <small className="muted">
            The AI reads your CV against each of the best openings and rates the fit. Openings below this are dropped. Jobs in
            priority places get a bonus.
          </small>
        </div>
        <div className="field">
          <label htmlFor="daily">
            <input id="daily" type="checkbox" checked={form.daily} onChange={(e) => set('daily', e.target.checked)} /> Also
            search by itself every 3 hours, even when I am not on the website
          </label>
        </div>
        <ErrorMessage message={error} />
        <div className="actions">
          <button type="submit" className="btn" disabled={busy}>
            Save settings
          </button>
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={resetSettings}>
            Use recommended settings
          </button>
          {saved && <span className="muted">Saved.</span>}
        </div>
      </form>

      <div className="card column">
        <h2>Companies searched</h2>
        <p className="muted">
          Every search reads these companies' career pages. Companies found along the way are added here too. Add a company
          by its name, or paste the link of its careers page (Greenhouse, Lever, Ashby, or the company's own site).
        </p>
        <form className="actions" onSubmit={addCompany}>
          <input
            aria-label="Company name or career page link"
            placeholder="Company name or careers page link"
            value={company}
            onChange={(e) => setCompany(e.target.value)}
          />
          <button type="submit" className="btn" disabled={adding}>
            {adding ? 'Looking…' : 'Add company'}
          </button>
        </form>
        <div className="field">
          <label htmlFor="import-names">Company names to import</label>
          <textarea
            id="import-names"
            rows={3}
            value={names}
            placeholder="One company per line, or separated by commas"
            onChange={(e) => setNames(e.target.value)}
          />
          <small className="muted">
            Each name is looked up once, and its careers page is added when it has one on Greenhouse, Lever or Ashby. It runs
            in the background.
          </small>
        </div>
        <div className="actions">
          <button type="button" className="btn" disabled={importing || names.trim() === ''} onClick={importPasted}>
            Import these companies
          </button>
          <button type="button" className="btn" disabled={importing} onClick={() => void importNames([])}>
            {importing ? 'Looking up careers pages…' : 'Add well-known Israeli tech companies'}
          </button>
        </div>
        {data.importStatus && <p className="muted">{data.importStatus}</p>}
        {companies.loading && !companies.data && <Loading />}
        {companies.error && <ErrorMessage message={companies.error} />}
        <ul className="list">
          {(companies.data ?? []).map((c) => (
            <li key={c.id} className="row-between">
              <span>
                <strong>{c.name}</strong> <span className="muted">{c.board}</span>
              </span>
              <ConfirmButton label="Remove" confirmLabel="Confirm remove" onConfirm={() => removeCompany(c.id)} />
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
