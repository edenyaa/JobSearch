import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { messageOf } from '../api/client'
import { companiesApi } from '../api/endpoints'
import type { Company, ContactInput, ContactPerson } from '../api/types'
import { useUserId } from '../auth/AuthContext'
import { ConfirmButton } from '../components/ConfirmButton'
import { ErrorMessage, Loading } from '../components/ErrorMessage'
import { StatusBadge } from '../components/StatusBadge'
import { useLoad } from '../hooks/useLoad'

const EMPTY_CONTACT: ContactInput = { name: '', whereKnown: '', linkedin: '', phone: '', description: '' }

export function CompaniesPage() {
  const userId = useUserId()
  const companies = useLoad(() => companiesApi.list(userId), [userId])
  const [editingId, setEditingId] = useState<number | null>(null)
  const [name, setName] = useState('')
  const [contacts, setContacts] = useState<ContactInput[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function reset() {
    setEditingId(null)
    setName('')
    setContacts([])
    setError(null)
  }

  function edit(company: Company) {
    setEditingId(company.id)
    setName(company.name)
    setContacts(
      company.contacts.map((c) => ({
        name: c.name ?? '',
        whereKnown: c.whereKnown ?? '',
        linkedin: c.linkedin ?? '',
        phone: c.phone ?? '',
        description: c.description ?? '',
      })),
    )
    setError(null)
  }

  function setField(index: number, field: keyof ContactInput, value: string) {
    setContacts((previous) => previous.map((c, i) => (i === index ? { ...c, [field]: value } : c)))
  }

  function addContact() {
    setContacts((previous) => [...previous, EMPTY_CONTACT])
  }

  function removeContact(index: number) {
    setContacts((previous) => previous.filter((_, i) => i !== index))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (name.trim() === '') {
      setError('The company name is required.')
      return
    }
    setBusy(true)
    try {
      const input = {
        name: name.trim(),
        contacts: contacts.map((c) => ({
          name: c.name.trim(),
          whereKnown: c.whereKnown.trim(),
          linkedin: c.linkedin.trim(),
          phone: c.phone.trim(),
          description: c.description.trim(),
        })),
      }
      if (editingId === null) {
        await companiesApi.create(userId, input)
      } else {
        await companiesApi.update(userId, editingId, input)
      }
      reset()
      companies.reload()
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  async function remove(id: number) {
    try {
      await companiesApi.remove(userId, id)
      if (editingId === id) reset()
      companies.reload()
    } catch (e) {
      setError(messageOf(e))
    }
  }

  return (
    <div>
      <h1>Companies</h1>
      <p className="muted">
        Keep a contact at each company you apply to. A company lists every job whose company name matches it.
      </p>

      <form className="card form" onSubmit={submit}>
        <h2>{editingId === null ? 'Add a company' : 'Edit company'}</h2>
        <div className="field">
          <label htmlFor="company-name">Company name</label>
          <input id="company-name" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <fieldset className="field">
          <legend>People you know here</legend>
          {contacts.map((contact, index) => (
            <div key={index} className="card column" role="group" aria-label={`Contact ${index + 1}`}>
              <div className="grid-2">
                <div className="field">
                  <label htmlFor={`contact-name-${index}`}>Contact name</label>
                  <input
                    id={`contact-name-${index}`}
                    value={contact.name}
                    onChange={(e) => setField(index, 'name', e.target.value)}
                  />
                </div>
                <div className="field">
                  <label htmlFor={`contact-where-${index}`}>Where you know them from</label>
                  <input
                    id={`contact-where-${index}`}
                    value={contact.whereKnown}
                    onChange={(e) => setField(index, 'whereKnown', e.target.value)}
                  />
                </div>
                <div className="field">
                  <label htmlFor={`contact-linkedin-${index}`}>LinkedIn profile</label>
                  <input
                    id={`contact-linkedin-${index}`}
                    value={contact.linkedin}
                    onChange={(e) => setField(index, 'linkedin', e.target.value)}
                  />
                </div>
                <div className="field">
                  <label htmlFor={`contact-phone-${index}`}>Phone number</label>
                  <input
                    id={`contact-phone-${index}`}
                    value={contact.phone}
                    onChange={(e) => setField(index, 'phone', e.target.value)}
                  />
                </div>
              </div>
              <div className="field">
                <label htmlFor={`contact-description-${index}`}>How you know them</label>
                <textarea
                  id={`contact-description-${index}`}
                  rows={3}
                  value={contact.description}
                  onChange={(e) => setField(index, 'description', e.target.value)}
                />
              </div>
              <div className="actions">
                <button type="button" className="btn" onClick={() => removeContact(index)}>
                  Remove contact {index + 1}
                </button>
              </div>
            </div>
          ))}
          <div className="actions">
            <button type="button" className="btn" onClick={addContact}>
              Add contact
            </button>
          </div>
        </fieldset>
        <ErrorMessage message={error} />
        <div className="actions">
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {editingId === null ? 'Add company' : 'Save company'}
          </button>
          {editingId !== null && (
            <button type="button" className="btn" onClick={reset}>
              Cancel
            </button>
          )}
        </div>
      </form>

      {companies.loading && <Loading />}
      {!companies.loading && companies.error && <ErrorMessage message={companies.error} />}
      {!companies.loading && !companies.error && (companies.data ?? []).length === 0 && (
        <p className="muted">No companies yet.</p>
      )}
      <ul className="list">
        {(companies.data ?? []).map((company) => (
          <li key={company.id} className="card list-item column">
            <div className="row-between">
              <strong>{company.name}</strong>
              <div className="actions">
                <button type="button" className="btn" aria-label={`Edit ${company.name}`} onClick={() => edit(company)}>
                  Edit
                </button>
                <ConfirmButton label="Delete" onConfirm={() => remove(company.id)} />
              </div>
            </div>
            {company.contacts.length > 0 && (
              <div>
                <h3>Contacts</h3>
                <ul className="list">
                  {company.contacts.map((contact, index) => (
                    <li key={index}>
                      <ContactView contact={contact} />
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <div>
              <h3>Jobs submitted</h3>
              {company.jobs.length === 0 ? (
                <p className="muted">No jobs for this company yet.</p>
              ) : (
                <ul className="bullets">
                  {company.jobs.map((job) => (
                    <li key={job.id}>
                      <Link to={`/jobs/${job.id}`}>{job.title || job.jobNumber}</Link> <StatusBadge status={job.status} />
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

function ContactView({ contact }: { contact: ContactPerson }) {
  return (
    <div>
      <p>
        {contact.name && <strong>{contact.name}</strong>}
        {contact.whereKnown && <span className="muted"> · {contact.whereKnown}</span>}
      </p>
      {contact.description && <p className="muted">{contact.description}</p>}
      <p>
        {contact.linkedin && (
          <a href={safeUrl(contact.linkedin)} target="_blank" rel="noreferrer noopener">
            LinkedIn
          </a>
        )}
        {contact.linkedin && contact.phone && ' · '}
        {contact.phone && <a href={`tel:${contact.phone}`}>{contact.phone}</a>}
      </p>
    </div>
  )
}

/** Only http(s) links are clickable; a bare "linkedin.com/in/x" gets https:// added. */
function safeUrl(value: string): string {
  if (/^https?:\/\//i.test(value)) return value
  if (/^[a-z][a-z0-9+.-]*:/i.test(value)) return '#'
  return `https://${value}`
}
