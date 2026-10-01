import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { messageOf } from '../api/client'
import { coursesApi, skillsApi } from '../api/endpoints'
import type { Course } from '../api/types'
import { useUserId } from '../auth/AuthContext'
import { ConfirmButton } from '../components/ConfirmButton'
import { ErrorMessage, Loading } from '../components/ErrorMessage'
import { useLoad } from '../hooks/useLoad'

export function CoursesPage() {
  const userId = useUserId()
  const courses = useLoad(() => coursesApi.list(userId), [userId])
  const skills = useLoad(() => skillsApi.list(userId), [userId])
  const [editingId, setEditingId] = useState<number | null>(null)
  const [courseNumber, setCourseNumber] = useState('')
  const [institution, setInstitution] = useState('')
  const [description, setDescription] = useState('')
  const [assignments, setAssignments] = useState('')
  const [skillIds, setSkillIds] = useState<number[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function reset() {
    setEditingId(null)
    setCourseNumber('')
    setInstitution('')
    setDescription('')
    setAssignments('')
    setSkillIds([])
    setError(null)
  }

  function edit(course: Course) {
    setEditingId(course.id)
    setCourseNumber(course.courseNumber)
    setInstitution(course.institution ?? '')
    setDescription(course.description ?? '')
    setAssignments(course.assignments.join('\n'))
    setSkillIds(course.skillIds)
    setError(null)
  }

  function toggleSkill(id: number) {
    setSkillIds((previous) => (previous.includes(id) ? previous.filter((s) => s !== id) : [...previous, id]))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (courseNumber.trim() === '') {
      setError('The course number is required.')
      return
    }
    setBusy(true)
    try {
      const input = {
        courseNumber: courseNumber.trim(),
        institution: institution.trim(),
        description: description.trim(),
        assignments: assignments
          .split('\n')
          .map((line) => line.trim())
          .filter((line) => line !== ''),
        skillIds,
      }
      if (editingId === null) {
        await coursesApi.create(userId, input)
      } else {
        await coursesApi.update(userId, editingId, input)
      }
      reset()
      courses.reload()
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  async function remove(id: number) {
    try {
      await coursesApi.remove(userId, id)
      if (editingId === id) reset()
      courses.reload()
    } catch (e) {
      setError(messageOf(e))
    }
  }

  const skillName = (id: number) => (skills.data ?? []).find((skill) => skill.id === id)?.name ?? `Skill ${id}`
  const allSkills = skills.data ?? []

  return (
    <div>
      <h1>Courses</h1>
      <p className="muted">
        Courses show what you studied and built. Link each one to the skills it taught, and describe the assignments so the
        AI can use them.
      </p>

      <form className="card form" onSubmit={submit}>
        <h2>{editingId === null ? 'Add a course' : 'Edit course'}</h2>
        <div className="grid-2">
          <div className="field">
            <label htmlFor="course-number">Course number</label>
            <input id="course-number" value={courseNumber} onChange={(e) => setCourseNumber(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="institution">Where you studied</label>
            <input id="institution" value={institution} onChange={(e) => setInstitution(e.target.value)} />
          </div>
        </div>
        <div className="field">
          <label htmlFor="course-description">Description</label>
          <textarea id="course-description" rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="assignments">Assignments (one per line)</label>
          <textarea id="assignments" rows={3} value={assignments} onChange={(e) => setAssignments(e.target.value)} />
        </div>
        <fieldset className="field">
          <legend>Skills learned</legend>
          {allSkills.length === 0 ? (
            <p className="muted">
              You have no skills yet. <Link to="/skills">Add skills</Link> first, then link them here.
            </p>
          ) : (
            <div className="checks">
              {allSkills.map((skill) => (
                <label key={skill.id} className="check">
                  <input type="checkbox" checked={skillIds.includes(skill.id)} onChange={() => toggleSkill(skill.id)} />
                  {skill.name}
                </label>
              ))}
            </div>
          )}
        </fieldset>
        <ErrorMessage message={error} />
        <div className="actions">
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {editingId === null ? 'Add course' : 'Save course'}
          </button>
          {editingId !== null && (
            <button type="button" className="btn" onClick={reset}>
              Cancel
            </button>
          )}
        </div>
      </form>

      {courses.loading && <Loading />}
      {!courses.loading && courses.error && <ErrorMessage message={courses.error} />}
      {!courses.loading && !courses.error && (courses.data ?? []).length === 0 && <p className="muted">No courses yet.</p>}
      <ul className="list">
        {(courses.data ?? []).map((course) => (
          <li key={course.id} className="card list-item column">
            <div className="row-between">
              <div>
                <strong>{course.courseNumber}</strong>
                {course.institution && <span className="muted"> · {course.institution}</span>}
              </div>
              <div className="actions">
                <button type="button" className="btn" aria-label={`Edit ${course.courseNumber}`} onClick={() => edit(course)}>
                  Edit
                </button>
                <ConfirmButton label="Delete" onConfirm={() => remove(course.id)} />
              </div>
            </div>
            {course.description && <p className="muted">{course.description}</p>}
            {course.assignments.length > 0 && (
              <ul className="bullets">
                {course.assignments.map((assignment) => (
                  <li key={assignment}>{assignment}</li>
                ))}
              </ul>
            )}
            {course.skillIds.length > 0 && (
              <div className="chips">
                {course.skillIds.map((id) => (
                  <span key={id} className="chip">
                    {skillName(id)}
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
