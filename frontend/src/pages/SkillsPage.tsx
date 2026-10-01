import { useState } from 'react'
import type { FormEvent } from 'react'
import { messageOf } from '../api/client'
import { skillsApi } from '../api/endpoints'
import type { Skill } from '../api/types'
import { useUserId } from '../auth/AuthContext'
import { ConfirmButton } from '../components/ConfirmButton'
import { ErrorMessage, Loading } from '../components/ErrorMessage'
import { TagInput } from '../components/TagInput'
import { useLoad } from '../hooks/useLoad'

export function SkillsPage() {
  const userId = useUserId()
  const skills = useLoad(() => skillsApi.list(userId), [userId])
  const [editingId, setEditingId] = useState<number | null>(null)
  const [name, setName] = useState('')
  const [explanation, setExplanation] = useState('')
  const [keywords, setKeywords] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function reset() {
    setEditingId(null)
    setName('')
    setExplanation('')
    setKeywords([])
    setError(null)
  }

  function edit(skill: Skill) {
    setEditingId(skill.id)
    setName(skill.name)
    setExplanation(skill.explanation ?? '')
    setKeywords(skill.keywords)
    setError(null)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (name.trim() === '') {
      setError('The skill name is required.')
      return
    }
    setBusy(true)
    try {
      const input = { name: name.trim(), explanation: explanation.trim(), keywords }
      if (editingId === null) {
        await skillsApi.create(userId, input)
      } else {
        await skillsApi.update(userId, editingId, input)
      }
      reset()
      skills.reload()
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  async function remove(id: number) {
    try {
      await skillsApi.remove(userId, id)
      if (editingId === id) reset()
      skills.reload()
    } catch (e) {
      setError(messageOf(e))
    }
  }

  return (
    <div>
      <h1>Skills</h1>
      <p className="muted">
        Skills found in your CV are added here automatically when you upload it. Add the rest yourself, with an explanation and
        the keywords and buzzwords ATS systems look for.
      </p>

      <form className="card form" onSubmit={submit}>
        <h2>{editingId === null ? 'Add a skill' : 'Edit skill'}</h2>
        <div className="field">
          <label htmlFor="skill-name">Skill name</label>
          <input id="skill-name" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="skill-explanation">Explanation</label>
          <textarea id="skill-explanation" rows={2} value={explanation} onChange={(e) => setExplanation(e.target.value)} />
        </div>
        <TagInput label="Keywords" value={keywords} onChange={setKeywords} placeholder="Type a keyword and press Enter" />
        <ErrorMessage message={error} />
        <div className="actions">
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {editingId === null ? 'Add skill' : 'Save skill'}
          </button>
          {editingId !== null && (
            <button type="button" className="btn" onClick={reset}>
              Cancel
            </button>
          )}
        </div>
      </form>

      {skills.loading && <Loading />}
      {!skills.loading && skills.error && <ErrorMessage message={skills.error} />}
      {!skills.loading && !skills.error && (skills.data ?? []).length === 0 && <p className="muted">No skills yet.</p>}
      <ul className="list">
        {(skills.data ?? []).map((skill) => (
          <li key={skill.id} className="card list-item column">
            <div className="row-between">
              <div>
                <strong>{skill.name}</strong>
                {skill.source === 'CV' && <span className="badge badge-cv">From your CV</span>}
              </div>
              <div className="actions">
                <button type="button" className="btn" aria-label={`Edit ${skill.name}`} onClick={() => edit(skill)}>
                  Edit
                </button>
                <ConfirmButton label="Delete" onConfirm={() => remove(skill.id)} />
              </div>
            </div>
            {skill.explanation && <p className="muted">{skill.explanation}</p>}
            {skill.keywords.length > 0 && (
              <div className="chips">
                {skill.keywords.map((keyword) => (
                  <span key={keyword} className="chip">
                    {keyword}
                  </span>
                ))}
              </div>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
