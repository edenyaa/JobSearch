import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { makeJob, makeSkill, mockApi, renderApp, USER } from '../test/helpers'

const NO_CV_USER = { ...USER, hasMainCv: false, mainCvFileName: null }

function statValue(label: string): string {
  return screen.getByText(label).previousElementSibling!.textContent ?? ''
}

describe('DashboardPage', () => {
  test('GivenJobsWithDifferentStatuses_WhenOpening_ThenCountsAreShown', async () => {
    mockApi({
      'GET /api/users/1/jobs': {
        body: [
          makeJob({ id: 1, status: 'INTERVIEW' }),
          makeJob({ id: 2, status: 'INTERVIEW' }),
          makeJob({ id: 3, status: 'OFFER' }),
          makeJob({ id: 4, status: 'REJECTED' }),
          makeJob({ id: 5, status: 'APPLIED' }),
        ],
      },
      'GET /api/users/1/skills': { body: [makeSkill()] },
    })
    renderApp('/')

    await screen.findByRole('heading', { name: 'Dashboard' })

    expect(statValue('Total jobs')).toBe('5')
    expect(statValue('Interviews')).toBe('2')
    expect(statValue('Offers')).toBe('1')
    expect(statValue('Rejections')).toBe('1')
  })

  test('GivenMoreThanFiveJobs_WhenOpening_ThenOnlyFiveRecentAreListed', async () => {
    const jobs = Array.from({ length: 7 }, (_, i) => makeJob({ id: i + 1, title: `Job ${i + 1}` }))
    mockApi({ 'GET /api/users/1/jobs': { body: jobs }, 'GET /api/users/1/skills': { body: [makeSkill()] } })
    renderApp('/')

    expect(await screen.findByText('Job 5')).toBeInTheDocument()

    expect(screen.queryByText('Job 6')).not.toBeInTheDocument()
  })

  test('GivenNoJobs_WhenOpening_ThenEmptyMessageIsShown', async () => {
    mockApi({ 'GET /api/users/1/jobs': { body: [] }, 'GET /api/users/1/skills': { body: [makeSkill()] } })
    renderApp('/')

    expect(await screen.findByText(/No jobs yet/)).toBeInTheDocument()
  })

  test('GivenNewUser_WhenOpening_ThenChecklistStartsAtZeroWithUploadStep', async () => {
    mockApi({ 'GET /api/users/1/jobs': { body: [] }, 'GET /api/users/1/skills': { body: [] } })
    renderApp('/', { user: NO_CV_USER })

    expect(await screen.findByText('0 of 4 done')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Upload your main CV' })).toHaveAttribute('href', '/profile')
    expect(screen.getByRole('link', { name: 'Review the skills we found' })).toHaveAttribute('href', '/skills')
    expect(screen.getByRole('link', { name: 'Add a job you are applying to' })).toHaveAttribute('href', '/jobs/new')
  })

  test('GivenCvAndSkillsButNoJobs_WhenOpening_ThenFirstTwoStepsAreDone', async () => {
    mockApi({ 'GET /api/users/1/jobs': { body: [] }, 'GET /api/users/1/skills': { body: [makeSkill()] } })
    renderApp('/')

    expect(await screen.findByText('2 of 4 done')).toBeInTheDocument()
    const cvStep = screen.getByRole('link', { name: 'Upload your main CV' }).closest('li')!
    expect(within(cvStep).getByText('Done')).toBeInTheDocument()
    const jobStep = screen.getByRole('link', { name: 'Add a job you are applying to' }).closest('li')!
    expect(within(jobStep).queryByText('Done')).not.toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Setup progress' })).toHaveAttribute('aria-valuenow', '2')
  })

  test('GivenFirstJobExists_WhenOpening_ThenLastStepLinksToItsAnalysis', async () => {
    mockApi({ 'GET /api/users/1/jobs': { body: [makeJob({ id: 5 })] }, 'GET /api/users/1/skills': { body: [makeSkill()] } })
    renderApp('/')

    expect(await screen.findByRole('link', { name: 'Analyze it and tailor your CV' })).toHaveAttribute('href', '/jobs/5/analysis')
    expect(screen.getByText('3 of 4 done')).toBeInTheDocument()
  })

  test('GivenEverythingDone_WhenOpening_ThenChecklistIsHidden', async () => {
    mockApi({
      'GET /api/users/1/jobs': { body: [makeJob({ hasTailoredResume: true })] },
      'GET /api/users/1/skills': { body: [makeSkill()] },
    })
    renderApp('/')

    await screen.findByRole('heading', { name: 'Dashboard' })

    expect(screen.queryByText('Get started')).not.toBeInTheDocument()
  })

  test('GivenJobs_WhenOpening_ThenPipelineChartDescribesStatusCounts', async () => {
    mockApi({
      'GET /api/users/1/jobs': {
        body: [makeJob({ id: 1, status: 'INTERVIEW' }), makeJob({ id: 2, status: 'INTERVIEW' }), makeJob({ id: 3, status: 'OFFER' })],
      },
      'GET /api/users/1/skills': { body: [makeSkill()] },
    })
    renderApp('/')

    expect(await screen.findByRole('img', { name: 'Jobs by status: Interview 2, Offer 1' })).toBeInTheDocument()
  })

  test('GivenNoJobs_WhenOpening_ThenPipelineSaysThereAreNoJobsYet', async () => {
    mockApi({ 'GET /api/users/1/jobs': { body: [] }, 'GET /api/users/1/skills': { body: [makeSkill()] } })
    renderApp('/')

    expect(await screen.findByText(/No jobs yet/)).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: /Jobs by status/ })).not.toBeInTheDocument()
  })

  test('GivenUser_WhenOpening_ThenGreetingUsesTheFirstName', async () => {
    mockApi({ 'GET /api/users/1/jobs': { body: [] }, 'GET /api/users/1/skills': { body: [makeSkill()] } })
    renderApp('/')

    expect(await screen.findByText('Welcome back, Dana.')).toBeInTheDocument()
  })

  test('GivenServerError_WhenOpening_ThenErrorIsShown', async () => {
    mockApi({
      'GET /api/users/1/jobs': { status: 500, body: {} },
      'GET /api/users/1/skills': { body: [] },
    })
    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong (500).')
  })
})

describe('JobsPage', () => {
  test('GivenJobs_WhenOpening_ThenTheyAreListedWithDetailsAndLinks', async () => {
    mockApi({
      'GET /api/users/1/jobs': {
        body: [
          makeJob({ id: 5, title: 'Backend Engineer', company: 'Acme', jobNumber: 'REQ-5', hasTailoredResume: true }),
          makeJob({ id: 6, title: null, company: null, jobNumber: 'GEN-6', status: 'OFFER' }),
        ],
      },
    })
    renderApp('/jobs')

    const link = await screen.findByRole('link', { name: 'Backend Engineer' })

    expect(link).toHaveAttribute('href', '/jobs/5')
    expect(screen.getByText('Acme · REQ-5 · 2026-03-01')).toBeInTheDocument()
    expect(screen.getByText('Tailored CV ready')).toBeInTheDocument()
    const untitled = screen.getByRole('link', { name: 'Untitled job' })
    expect(untitled).toHaveAttribute('href', '/jobs/6')
    expect(within(untitled.closest('li')!).getByText('Offer')).toBeInTheDocument()
  })

  test('GivenNoJobs_WhenOpening_ThenEmptyMessageIsShown', async () => {
    mockApi({ 'GET /api/users/1/jobs': { body: [] } })
    renderApp('/jobs')

    expect(await screen.findByText('No jobs found.')).toBeInTheDocument()
  })

  test('GivenStatusFilter_WhenChoosingAStatus_ThenOnlyThatStatusIsRequested', async () => {
    const api = mockApi({
      'GET /api/users/1/jobs': { body: [makeJob({ id: 1, title: 'A' }), makeJob({ id: 2, title: 'B', status: 'INTERVIEW' })] },
      'GET /api/users/1/jobs?status=INTERVIEW': { body: [makeJob({ id: 2, title: 'B', status: 'INTERVIEW' })] },
    })
    const user = userEvent.setup()
    renderApp('/jobs')
    await screen.findByText('A')

    await user.selectOptions(screen.getByLabelText('Filter by status'), 'INTERVIEW')

    await screen.findByText('B')
    expect(screen.queryByRole('link', { name: 'A' })).not.toBeInTheDocument()
    expect(api.to('GET /api/users/1/jobs?status=INTERVIEW')).toHaveLength(1)
  })

  test('GivenServerError_WhenOpening_ThenErrorIsShown', async () => {
    mockApi({ 'GET /api/users/1/jobs': { status: 500, body: { message: 'Database is down' } } })
    renderApp('/jobs')

    expect(await screen.findByRole('alert')).toHaveTextContent('Database is down')
  })

  test('GivenJobRow_WhenReading_ThenItHasALinkToItsAnalysis', async () => {
    mockApi({ 'GET /api/users/1/jobs': { body: [makeJob({ id: 5, title: 'Backend Engineer' })] } })
    renderApp('/jobs')

    expect(await screen.findByRole('link', { name: 'Analysis for Backend Engineer' })).toHaveAttribute('href', '/jobs/5/analysis')
  })

  test('GivenJobsPage_WhenClickingAddJob_ThenFormOpens', async () => {
    mockApi({ 'GET /api/users/1/jobs': { body: [] } })
    const user = userEvent.setup()
    renderApp('/jobs')

    await user.click(await screen.findByRole('link', { name: 'Add job' }))

    expect(screen.getByRole('heading', { name: 'Add job' })).toBeInTheDocument()
  })

  test('GivenJobCard_WhenReadingIt_ThenStatusBadgeIsInsideTheSameRow', async () => {
    mockApi({ 'GET /api/users/1/jobs': { body: [makeJob({ status: 'INTERVIEW' })] } })
    renderApp('/jobs')

    const row = (await screen.findByRole('link', { name: 'Backend Engineer' })).closest('li')!

    expect(within(row).getByText('Interview')).toBeInTheDocument()
  })
})
