import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import type { Company } from '../api/types'
import { makeCompany, mockApi, renderApp } from '../test/helpers'

function statefulCompanies(initial: Company[] = []) {
  const store = [...initial]
  return mockApi({
    'GET /api/users/1/companies': () => ({ body: [...store] }),
    'POST /api/users/1/companies': (call) => {
      const created = { id: store.length + 10, jobs: [], ...(call.body as object) } as unknown as Company
      store.push(created)
      return { status: 201, body: created }
    },
    'PUT /api/users/1/companies/1': (call) => {
      store[0] = { id: 1, jobs: [], ...(call.body as object) } as unknown as Company
      return { body: store[0] }
    },
    'DELETE /api/users/1/companies/1': () => {
      store.splice(0, 1)
      return { status: 204 }
    },
  })
}

describe('CompaniesPage', () => {
  test('GivenNoCompanies_WhenOpening_ThenEmptyMessageIsShown', async () => {
    statefulCompanies()
    renderApp('/companies')

    expect(await screen.findByText('No companies yet.')).toBeInTheDocument()
  })

  test('GivenCompanyWithContactAndJobs_WhenOpening_ThenContactAndJobsAreShown', async () => {
    statefulCompanies([
      makeCompany({
        contacts: [
          {
            name: 'Dana',
            whereKnown: 'TAU',
            linkedin: 'linkedin.com/in/dana',
            phone: '050-1234567',
            description: 'Study partner in CS101',
          },
          { name: 'Lee', whereKnown: 'Army', linkedin: null, phone: null, description: null },
        ],
        jobs: [{ id: 7, jobNumber: 'GEN-7', title: 'Backend dev', status: 'APPLIED', appliedDate: '2026-01-01' }],
      }),
    ])
    renderApp('/companies')

    const card = (await screen.findByText('Acme')).closest('li')!

    expect(within(card).getByText('Dana')).toBeInTheDocument()
    expect(within(card).getByText('Lee')).toBeInTheDocument()
    expect(within(card).getAllByRole('link', { name: 'LinkedIn' })).toHaveLength(1)
    expect(within(card).getByText(/TAU/)).toBeInTheDocument()
    expect(within(card).getByText('Study partner in CS101')).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: 'LinkedIn' })).toHaveAttribute('href', 'https://linkedin.com/in/dana')
    expect(within(card).getByRole('link', { name: '050-1234567' })).toHaveAttribute('href', 'tel:050-1234567')
    expect(within(card).getByRole('link', { name: 'Backend dev' })).toHaveAttribute('href', '/jobs/7')
  })

  test('GivenNonHttpLinkedinValue_WhenOpening_ThenLinkIsNotClickable', async () => {
    statefulCompanies([
      makeCompany({
        contacts: [{ name: 'Eve', whereKnown: null, linkedin: 'javascript:alert(1)', phone: null, description: null }],
      }),
    ])
    renderApp('/companies')

    const card = (await screen.findByText('Acme')).closest('li')!

    expect(within(card).getByRole('link', { name: 'LinkedIn' })).toHaveAttribute('href', '#')
  })

  test('GivenCompanyWithoutContactOrJobs_WhenOpening_ThenNoContactsSectionAndEmptyJobsMessage', async () => {
    statefulCompanies([makeCompany()])
    renderApp('/companies')

    const card = (await screen.findByText('Acme')).closest('li')!

    expect(within(card).queryByText('Contacts')).not.toBeInTheDocument()
    expect(within(card).getByText('No jobs for this company yet.')).toBeInTheDocument()
  })

  test('GivenFilledForm_WhenAdding_ThenCompanyIsCreatedWithContact', async () => {
    const api = statefulCompanies()
    const user = userEvent.setup()
    renderApp('/companies')
    await screen.findByText('No companies yet.')

    await user.type(screen.getByLabelText('Company name'), '  Globex ')
    await user.click(screen.getByRole('button', { name: 'Add contact' }))
    await user.click(screen.getByRole('button', { name: 'Add contact' }))
    const first = within(screen.getByRole('group', { name: 'Contact 1' }))
    const second = within(screen.getByRole('group', { name: 'Contact 2' }))
    await user.type(first.getByLabelText('Contact name'), 'Lee')
    await user.type(first.getByLabelText('Where you know them from'), 'Army')
    await user.type(first.getByLabelText('Phone number'), '052')
    await user.type(first.getByLabelText('How you know them'), 'Served together')
    await user.type(second.getByLabelText('Contact name'), 'Noa')
    await user.click(screen.getByRole('button', { name: 'Add company' }))

    expect(await screen.findByText('Globex')).toBeInTheDocument()
    const post = api.calls.find((call) => call.method === 'POST')!
    expect(post.body).toEqual({
      name: 'Globex',
      contacts: [
        { name: 'Lee', whereKnown: 'Army', linkedin: '', phone: '052', description: 'Served together' },
        { name: 'Noa', whereKnown: '', linkedin: '', phone: '', description: '' },
      ],
    })
  })

  test('GivenBlankName_WhenAdding_ThenErrorIsShownAndNothingSent', async () => {
    const api = statefulCompanies()
    const user = userEvent.setup()
    renderApp('/companies')
    await screen.findByText('No companies yet.')

    await user.click(screen.getByRole('button', { name: 'Add company' }))

    expect(screen.getByText('The company name is required.')).toBeInTheDocument()
    expect(api.calls.some((call) => call.method === 'POST')).toBe(false)
  })

  test('GivenExistingCompany_WhenEditing_ThenFormIsPrefilledAndSaved', async () => {
    const api = statefulCompanies([
      makeCompany({
        contacts: [
          { name: 'Dana', whereKnown: 'TAU', linkedin: null, phone: null, description: null },
          { name: 'Sam', whereKnown: null, linkedin: null, phone: null, description: null },
        ],
      }),
    ])
    const user = userEvent.setup()
    renderApp('/companies')

    await user.click(await screen.findByRole('button', { name: 'Edit Acme' }))
    const first = within(screen.getByRole('group', { name: 'Contact 1' }))
    expect(first.getByLabelText('Contact name')).toHaveValue('Dana')
    await user.clear(first.getByLabelText('Contact name'))
    await user.type(first.getByLabelText('Contact name'), 'Dani')
    await user.click(screen.getByRole('button', { name: 'Remove contact 2' }))
    await user.click(screen.getByRole('button', { name: 'Save company' }))

    expect(await screen.findByText('Dani')).toBeInTheDocument()
    const put = api.calls.find((call) => call.method === 'PUT')!
    expect(put.body).toMatchObject({ name: 'Acme', contacts: [{ name: 'Dani', whereKnown: 'TAU' }] })
    expect((put.body as { contacts: unknown[] }).contacts).toHaveLength(1)
  })

  test('GivenExistingCompany_WhenDeleting_ThenItDisappears', async () => {
    statefulCompanies([makeCompany()])
    const user = userEvent.setup()
    renderApp('/companies')
    await screen.findByText('Acme')

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await user.click(await screen.findByRole('button', { name: 'Confirm delete' }))

    expect(await screen.findByText('No companies yet.')).toBeInTheDocument()
  })
})
