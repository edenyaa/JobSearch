import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { makeJob, mockApi, renderApp } from '../test/helpers'
import { toJobInput } from './JobFormPage'

describe('JobFormPage create', () => {
  test('GivenDescriptionAndTitle_WhenSaving_ThenJobIsCreatedWithoutEmptyFieldsAndDetailOpens', async () => {
    const api = mockApi({
      'POST /api/users/1/jobs': { status: 201, body: makeJob({ id: 9, title: 'Dev' }) },
      'GET /api/users/1/jobs/9': { body: makeJob({ id: 9, title: 'Dev' }) },
    })
    const user = userEvent.setup()
    renderApp('/jobs/new')

    await user.type(screen.getByLabelText('Job title'), 'Dev')
    await user.type(screen.getByLabelText('Job description'), 'Build things')
    await user.click(screen.getByRole('button', { name: 'Save job' }))

    expect(await screen.findByRole('heading', { name: 'Dev' })).toBeInTheDocument()
    expect(api.to('POST /api/users/1/jobs')[0].body).toEqual({
      title: 'Dev',
      status: 'APPLIED',
      description: 'Build things',
      notes: '',
    })
  })

  test('GivenAllFields_WhenSaving_ThenEverythingIsSent', async () => {
    const api = mockApi({
      'POST /api/users/1/jobs': { status: 201, body: makeJob({ id: 9 }) },
      'GET /api/users/1/jobs/9': { body: makeJob({ id: 9 }) },
    })
    const user = userEvent.setup()
    renderApp('/jobs/new')

    await user.type(screen.getByLabelText('Job title'), 'Backend')
    await user.type(screen.getByLabelText('Company'), 'Acme')
    await user.type(screen.getByLabelText('Job number'), 'REQ-77')
    await user.type(screen.getByLabelText('Link to the posting'), 'https://acme.com/j/77')
    await user.type(screen.getByLabelText('Date'), '2026-01-15')
    await user.selectOptions(screen.getByLabelText('Status'), 'INTERVIEW')
    await user.type(screen.getByLabelText('Job description'), 'Build APIs')
    await user.type(screen.getByLabelText('Notes'), 'Referred by Sam')
    await user.click(screen.getByRole('button', { name: 'Save job' }))

    await screen.findByRole('heading', { name: 'Backend Engineer' })
    expect(api.to('POST /api/users/1/jobs')[0].body).toEqual({
      title: 'Backend',
      company: 'Acme',
      jobNumber: 'REQ-77',
      link: 'https://acme.com/j/77',
      appliedDate: '2026-01-15',
      status: 'INTERVIEW',
      description: 'Build APIs',
      notes: 'Referred by Sam',
    })
  })

  test('GivenBlankDescription_WhenSaving_ThenErrorIsShownAndNothingIsSent', async () => {
    const api = mockApi({})
    const user = userEvent.setup()
    renderApp('/jobs/new')

    await user.type(screen.getByLabelText('Job description'), '   ')
    await user.click(screen.getByRole('button', { name: 'Save job' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The job description is required.')
    expect(api.calls).toHaveLength(0)
  })

  test('GivenServerError_WhenSaving_ThenMessageIsShownAndFormStaysOpen', async () => {
    mockApi({ 'POST /api/users/1/jobs': { status: 400, body: { message: 'Bad job' } } })
    const user = userEvent.setup()
    renderApp('/jobs/new')

    await user.type(screen.getByLabelText('Job description'), 'Build things')
    await user.click(screen.getByRole('button', { name: 'Save job' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Bad job')
    expect(screen.getByRole('button', { name: 'Save job' })).toBeEnabled()
  })

  test('GivenForm_WhenReading_ThenJobNumberExplainsItIsGeneratedWhenEmpty', () => {
    mockApi({})
    renderApp('/jobs/new')

    expect(screen.getByText('Leave empty and one is generated for you.')).toBeInTheDocument()
  })
})

describe('JobFormPage edit', () => {
  test('GivenExistingJob_WhenOpeningEdit_ThenFieldsArePrefilled', async () => {
    mockApi({
      'GET /api/users/1/jobs/5': {
        body: makeJob({ link: 'https://x.com', notes: 'call back', status: 'OFFER', description: 'Java role' }),
      },
    })
    renderApp('/jobs/5/edit')

    expect(await screen.findByLabelText('Job title')).toHaveValue('Backend Engineer')
    expect(screen.getByLabelText('Company')).toHaveValue('Acme')
    expect(screen.getByLabelText('Job number')).toHaveValue('REQ-5')
    expect(screen.getByLabelText('Link to the posting')).toHaveValue('https://x.com')
    expect(screen.getByLabelText('Date')).toHaveValue('2026-03-01')
    expect(screen.getByLabelText('Status')).toHaveValue('OFFER')
    expect(screen.getByLabelText('Job description')).toHaveValue('Java role')
    expect(screen.getByLabelText('Notes')).toHaveValue('call back')
  })

  test('GivenChangedTitle_WhenSaving_ThenJobIsUpdatedWithKeptNumberAndDate', async () => {
    const api = mockApi({
      'GET /api/users/1/jobs/5': { body: makeJob() },
      'PUT /api/users/1/jobs/5': { body: makeJob({ title: 'Staff Engineer' }) },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5/edit')
    const title = await screen.findByLabelText('Job title')

    await user.clear(title)
    await user.type(title, 'Staff Engineer')
    await user.click(screen.getByRole('button', { name: 'Save job' }))

    await screen.findByRole('heading', { name: 'Backend Engineer' })
    expect(api.to('PUT /api/users/1/jobs/5')[0].body).toMatchObject({
      title: 'Staff Engineer',
      jobNumber: 'REQ-5',
      appliedDate: '2026-03-01',
      description: 'Build APIs with Java',
    })
  })

  test('GivenUnknownJob_WhenOpeningEdit_ThenErrorIsShown', async () => {
    mockApi({ 'GET /api/users/1/jobs/5': { status: 404, body: { message: 'Job 5 not found' } } })
    renderApp('/jobs/5/edit')

    expect(await screen.findByRole('alert')).toHaveTextContent('Job 5 not found')
  })

  test('GivenEditForm_WhenCancelling_ThenUserGoesBack', async () => {
    mockApi({
      'GET /api/users/1/jobs/5': { body: makeJob() },
      'GET /api/users/1/jobs': { body: [] },
    })
    const user = userEvent.setup()
    renderApp(['/jobs', '/jobs/5/edit'])
    await screen.findByLabelText('Job title')

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(await screen.findByRole('heading', { name: 'Jobs' })).toBeInTheDocument()
  })
})

describe('toJobInput', () => {
  test('GivenBlankOptionalFields_WhenConverting_ThenTheyBecomeUndefined', () => {
    const input = toJobInput({
      title: '  ',
      company: '',
      jobNumber: '',
      link: '',
      appliedDate: '',
      status: 'SAVED',
      description: '  text  ',
      notes: '',
    })

    expect(input.title).toBeUndefined()
    expect(input.company).toBeUndefined()
    expect(input.jobNumber).toBeUndefined()
    expect(input.link).toBeUndefined()
    expect(input.appliedDate).toBeUndefined()
    expect(input.description).toBe('text')
    expect(input.status).toBe('SAVED')
    expect(input.notes).toBe('')
  })
})
