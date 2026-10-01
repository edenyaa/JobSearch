import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { messageOf, saveBlob } from '../api/client'
import { usersApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { ErrorMessage } from '../components/ErrorMessage'
import { FileInput } from '../components/FileInput'
import { PASSWORD_RULE, isStrongPassword } from './RegisterPage'

interface FeedbackProps {
  error: string | null
  success: string | null
}

function Feedback({ error, success }: FeedbackProps) {
  return (
    <>
      <ErrorMessage message={error} />
      {success && (
        <p className="success" role="status">
          {success}
        </p>
      )}
    </>
  )
}

export function ProfilePage() {
  const { user, updateUser, expire } = useAuth()
  const account = user!

  const [newName, setNewName] = useState('')
  const [nameFeedback, setNameFeedback] = useState<FeedbackProps>({ error: null, success: null })
  const [newEmail, setNewEmail] = useState('')
  const [emailFeedback, setEmailFeedback] = useState<FeedbackProps>({ error: null, success: null })
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordFeedback, setPasswordFeedback] = useState<FeedbackProps>({ error: null, success: null })
  const [cvFeedback, setCvFeedback] = useState<FeedbackProps>({ error: null, success: null })
  const [cvText, setCvText] = useState<string | null>(null)
  const [imported, setImported] = useState(0)
  const [confirmEmail, setConfirmEmail] = useState('')
  const [deleteError, setDeleteError] = useState<string | null>(null)

  async function changeName(event: FormEvent) {
    event.preventDefault()
    setNameFeedback({ error: null, success: null })
    try {
      updateUser(await usersApi.changeName(account.id, account.name, newName.trim()))
      setNewName('')
      setNameFeedback({ error: null, success: 'Name updated.' })
    } catch (e) {
      setNameFeedback({ error: messageOf(e), success: null })
    }
  }

  async function changeEmail(event: FormEvent) {
    event.preventDefault()
    setEmailFeedback({ error: null, success: null })
    try {
      updateUser(await usersApi.changeEmail(account.id, account.email, newEmail.trim()))
      setNewEmail('')
      setEmailFeedback({ error: null, success: 'Email updated.' })
    } catch (e) {
      setEmailFeedback({ error: messageOf(e), success: null })
    }
  }

  async function changePassword(event: FormEvent) {
    event.preventDefault()
    setPasswordFeedback({ error: null, success: null })
    if (!isStrongPassword(newPassword)) {
      setPasswordFeedback({ error: PASSWORD_RULE, success: null })
      return
    }
    try {
      await usersApi.changePassword(account.id, oldPassword, newPassword)
      expire('Password changed. Please sign in again.')
    } catch (e) {
      setPasswordFeedback({ error: messageOf(e), success: null })
    }
  }

  async function uploadCv(file: File | undefined) {
    if (!file) return
    setCvFeedback({ error: null, success: null })
    try {
      const { importedSkills, ...uploaded } = await usersApi.uploadCv(account.id, file)
      updateUser(uploaded)
      setCvText(null)
      setImported(importedSkills)
      setCvFeedback({ error: null, success: 'Main CV uploaded.' })
    } catch (e) {
      setCvFeedback({ error: messageOf(e), success: null })
    }
  }

  async function downloadCv() {
    setCvFeedback({ error: null, success: null })
    try {
      saveBlob(await usersApi.downloadCv(account.id))
    } catch (e) {
      setCvFeedback({ error: messageOf(e), success: null })
    }
  }

  async function toggleCvText() {
    if (cvText !== null) {
      setCvText(null)
      return
    }
    setCvFeedback({ error: null, success: null })
    try {
      setCvText((await usersApi.cvText(account.id)).text)
    } catch (e) {
      setCvFeedback({ error: messageOf(e), success: null })
    }
  }

  async function deleteAccount() {
    setDeleteError(null)
    try {
      await usersApi.remove(account.id)
      expire('Your account was deleted.')
    } catch (e) {
      setDeleteError(messageOf(e))
    }
  }

  return (
    <div>
      <h1>Profile</h1>

      <section className="card">
        <h2>Account</h2>
        <p>
          <strong>{account.name}</strong>
          <br />
          <span className="muted">{account.email}</span>
        </p>
      </section>

      <section className="card">
        <h2>Main CV</h2>
        <p className="muted">
          Your main CV is the base every tailored version is built from.{' '}
          {account.hasMainCv ? `Current file: ${account.mainCvFileName}` : 'You have not uploaded one yet.'}
        </p>
        <FileInput id="cv-file" label="Upload CV (PDF, DOCX or TXT)" accept=".pdf,.docx,.txt" onSelect={uploadCv} />
        {account.hasMainCv && (
          <div className="actions">
            <button type="button" className="btn" onClick={downloadCv}>
              Download CV
            </button>
            <button type="button" className="btn" onClick={toggleCvText}>
              {cvText === null ? 'Show extracted text' : 'Hide extracted text'}
            </button>
          </div>
        )}
        <Feedback {...cvFeedback} />
        {imported > 0 && (
          <p className="notice" role="status">
            We found {imported} skills in your CV and added them to your <Link to="/skills">Skills</Link>. You can edit or remove them there.
          </p>
        )}
        {cvText !== null && <pre className="cv-text">{cvText}</pre>}
      </section>

      <section className="card">
        <h2>Name</h2>
        <form className="form" onSubmit={changeName}>
          <div className="field">
            <label htmlFor="new-name">New name</label>
            <input id="new-name" value={newName} onChange={(e) => setNewName(e.target.value)} required />
          </div>
          <Feedback {...nameFeedback} />
          <button type="submit" className="btn">
            Update name
          </button>
        </form>
      </section>

      <section className="card">
        <h2>Email</h2>
        <form className="form" onSubmit={changeEmail}>
          <div className="field">
            <label htmlFor="new-email">New email</label>
            <input id="new-email" type="email" value={newEmail} onChange={(e) => setNewEmail(e.target.value)} required />
          </div>
          <Feedback {...emailFeedback} />
          <button type="submit" className="btn">
            Update email
          </button>
        </form>
      </section>

      <section className="card">
        <h2>Password</h2>
        <form className="form" onSubmit={changePassword}>
          <div className="field">
            <label htmlFor="old-password">Current password</label>
            <input
              id="old-password"
              type="password"
              autoComplete="current-password"
              value={oldPassword}
              onChange={(e) => setOldPassword(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="new-password">New password</label>
            <input
              id="new-password"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
            <small className="muted">{PASSWORD_RULE} You will be signed out everywhere.</small>
          </div>
          <Feedback {...passwordFeedback} />
          <button type="submit" className="btn">
            Change password
          </button>
        </form>
      </section>

      <section className="card danger-zone">
        <h2>Delete account</h2>
        <p className="muted">This permanently deletes your account, jobs, courses, skills and files.</p>
        <div className="field">
          <label htmlFor="confirm-email">Type your email to confirm</label>
          <input id="confirm-email" value={confirmEmail} onChange={(e) => setConfirmEmail(e.target.value)} />
        </div>
        <ErrorMessage message={deleteError} />
        <button
          type="button"
          className="btn btn-danger"
          disabled={confirmEmail.trim().toLowerCase() !== account.email.toLowerCase()}
          onClick={deleteAccount}
        >
          Delete my account
        </button>
      </section>
    </div>
  )
}
