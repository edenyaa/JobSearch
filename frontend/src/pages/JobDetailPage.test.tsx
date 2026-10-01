import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import type { Job } from '../api/types'
import { makeJob, mockApi, renderApp, stubDownloads } from '../test/helpers'

const JOB_URL = 'GET /api/users/1/jobs/5'

const TAILORED: Partial<Job> = { hasTailoredResume: true, tailoredResumeFileName: 'tailored-cv-REQ-5.docx' }

const TAILOR_REPLY = {
  job: makeJob(TAILORED),
  coverageBefore: 40,
  coverageAfter: 90,
  matchedTerms: ['Java', 'Docker'],
  missingTerms: ['Kubernetes'],
  unsupportedSkills: ['Rust'],
}

describe('JobDetailPage details', () => {
  test('GivenJob_WhenOpening_ThenAllDetailsAreShown', async () => {
    mockApi({
      [JOB_URL]: { body: makeJob({ link: 'https://acme.com/j/5', description: 'Build APIs with Java' }) },
    })
    renderApp('/jobs/5')

    expect(await screen.findByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument()
    expect(screen.getByText(/Acme · #REQ-5 · 2026-03-01/)).toBeInTheDocument()
    expect(screen.getByText('Build APIs with Java')).toBeInTheDocument()
    const link = screen.getByRole('link', { name: 'Open posting' })
    expect(link).toHaveAttribute('href', 'https://acme.com/j/5')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'))
  })

  test('GivenJobWithoutTitleAndLink_WhenOpening_ThenUntitledIsShownAndNoLink', async () => {
    mockApi({ [JOB_URL]: { body: makeJob({ title: null, link: null }) } })
    renderApp('/jobs/5')

    expect(await screen.findByRole('heading', { name: 'Untitled job' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Open posting' })).not.toBeInTheDocument()
  })

  test('GivenUnknownJob_WhenOpening_ThenErrorIsShown', async () => {
    mockApi({ [JOB_URL]: { status: 404, body: { message: 'Job 5 not found' } } })
    renderApp('/jobs/5')

    expect(await screen.findByRole('alert')).toHaveTextContent('Job 5 not found')
  })

  test('GivenJob_WhenClickingEdit_ThenEditFormOpens', async () => {
    mockApi({ [JOB_URL]: { body: makeJob() } })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.click(await screen.findByRole('link', { name: 'Edit' }))

    expect(await screen.findByRole('heading', { name: 'Edit job' })).toBeInTheDocument()
  })
})

describe('JobDetailPage status and notes', () => {
  test('GivenJob_WhenChangingStatus_ThenServerIsUpdatedAndBadgeChanges', async () => {
    const api = mockApi({
      [JOB_URL]: { body: makeJob() },
      'PUT /api/users/1/jobs/5/status': { body: makeJob({ status: 'INTERVIEW' }) },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')
    const select = await screen.findByLabelText('Status')

    await user.selectOptions(select, 'INTERVIEW')

    await waitFor(() => expect(select).toHaveValue('INTERVIEW'))
    expect(api.to('PUT /api/users/1/jobs/5/status')[0].body).toEqual({ status: 'INTERVIEW' })
    expect(document.querySelector('.badge-interview')).toBeInTheDocument()
  })

  test('GivenStatusChangeFails_WhenChangingStatus_ThenErrorIsShownAndStatusUnchanged', async () => {
    mockApi({
      [JOB_URL]: { body: makeJob() },
      'PUT /api/users/1/jobs/5/status': { status: 500, body: {} },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')
    const select = await screen.findByLabelText('Status')

    await user.selectOptions(select, 'OFFER')

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong (500).')
    expect(select).toHaveValue('APPLIED')
  })

  test('GivenEditedNotes_WhenSaving_ThenFullJobIsSentWithTheNotes', async () => {
    const api = mockApi({
      [JOB_URL]: { body: makeJob({ link: 'https://x.com' }) },
      'PUT /api/users/1/jobs/5': { body: makeJob({ notes: 'Call Sam' }) },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.type(await screen.findByLabelText('Notes'), 'Call Sam')
    await user.click(screen.getByRole('button', { name: 'Save notes' }))

    expect(await screen.findByText('Notes saved')).toBeInTheDocument()
    expect(api.to('PUT /api/users/1/jobs/5')[0].body).toEqual({
      jobNumber: 'REQ-5',
      title: 'Backend Engineer',
      company: 'Acme',
      description: 'Build APIs with Java',
      link: 'https://x.com',
      appliedDate: '2026-03-01',
      status: 'APPLIED',
      notes: 'Call Sam',
    })
  })

  test('GivenExistingNotes_WhenOpening_ThenTheyArePrefilled', async () => {
    mockApi({ [JOB_URL]: { body: makeJob({ notes: 'Referred by Sam' }) } })
    renderApp('/jobs/5')

    expect(await screen.findByLabelText('Notes')).toHaveValue('Referred by Sam')
  })
})

describe('JobDetailPage analysis link', () => {
  test('GivenJob_WhenOpening_ThenAnalysisCardLinksToTheAnalysisPage', async () => {
    mockApi({ [JOB_URL]: { body: makeJob() } })
    renderApp('/jobs/5')

    const link = await screen.findByRole('link', { name: 'Open analysis' })

    expect(link).toHaveAttribute('href', '/jobs/5/analysis')
  })

  test('GivenAnalysisCard_WhenClickingOpenAnalysis_ThenAnalysisPageOpens', async () => {
    mockApi({
      [JOB_URL]: { body: makeJob() },
      'GET /api/users/1/jobs/5/analysis': { status: 400, body: { message: 'Upload a main CV first.' } },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.click(await screen.findByRole('link', { name: 'Open analysis' }))

    expect(await screen.findByRole('heading', { name: 'Analysis' })).toBeInTheDocument()
  })
})

describe('JobDetailPage AI tailoring', () => {
  test('GivenTailoring_WhenRunning_ThenBusyLabelIsShownAndButtonDisabled', async () => {
    mockApi({
      [JOB_URL]: { body: makeJob() },
      'POST /api/users/1/jobs/5/tailor': { body: TAILOR_REPLY, delay: 150 },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.click(await screen.findByRole('button', { name: 'Tailor my CV with AI' }))

    expect(screen.getByRole('button', { name: /Tailoring/ })).toBeDisabled()
    await screen.findByRole('progressbar', { name: 'Coverage after' })
    expect(screen.getByRole('button', { name: 'Tailor my CV with AI' })).toBeEnabled()
  })

  test('GivenTailorResult_WhenFinished_ThenBeforeAfterCoverageAndDownloadAreShown', async () => {
    mockApi({
      [JOB_URL]: { body: makeJob() },
      'POST /api/users/1/jobs/5/tailor': { body: TAILOR_REPLY },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')
    expect(screen.queryByRole('button', { name: 'Download tailored CV' })).not.toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: 'Tailor my CV with AI' }))

    expect(await screen.findByRole('progressbar', { name: 'Coverage before' })).toHaveAttribute('aria-valuenow', '40')
    expect(screen.getByRole('progressbar', { name: 'Coverage after' })).toHaveAttribute('aria-valuenow', '90')
    expect(screen.getByRole('button', { name: 'Download tailored CV' })).toBeInTheDocument()
    expect(screen.getByText('Current file: tailored-cv-REQ-5.docx')).toBeInTheDocument()
    expect(screen.getByText('Still missing').parentElement).toHaveTextContent('Kubernetes')
  })

  test('GivenUnsupportedSkillsFromAi_WhenFinished_ThenWarningNamesThem', async () => {
    mockApi({
      [JOB_URL]: { body: makeJob() },
      'POST /api/users/1/jobs/5/tailor': { body: TAILOR_REPLY },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.click(await screen.findByRole('button', { name: 'Tailor my CV with AI' }))

    const warning = await screen.findByRole('alert')
    expect(warning).toHaveTextContent('Check these skills before you send the CV.')
    expect(within(warning).getByText('Rust')).toBeInTheDocument()
  })

  test('GivenNoUnsupportedSkills_WhenFinished_ThenNoWarningIsShown', async () => {
    mockApi({
      [JOB_URL]: { body: makeJob() },
      'POST /api/users/1/jobs/5/tailor': { body: { ...TAILOR_REPLY, unsupportedSkills: [] } },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.click(await screen.findByRole('button', { name: 'Tailor my CV with AI' }))

    await screen.findByRole('progressbar', { name: 'Coverage after' })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('GivenAiUnavailable_WhenTailoring_ThenServerMessageIsShown', async () => {
    mockApi({
      [JOB_URL]: { body: makeJob() },
      'POST /api/users/1/jobs/5/tailor': {
        status: 502,
        body: { message: 'Gemini returned HTTP 503: This model is currently experiencing high demand.' },
      },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.click(await screen.findByRole('button', { name: 'Tailor my CV with AI' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('high demand')
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  test('GivenTailoredResume_WhenDownloading_ThenFileIsFetchedAndSaved', async () => {
    const downloads = stubDownloads()
    const api = mockApi({
      [JOB_URL]: { body: makeJob(TAILORED) },
      'GET /api/users/1/jobs/5/resume': {
        text: 'docx-bytes',
        headers: { 'Content-Disposition': 'attachment; filename="tailored-cv-REQ-5.docx"' },
      },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.click(await screen.findByRole('button', { name: 'Download tailored CV' }))

    await waitFor(() => expect(downloads.click).toHaveBeenCalledOnce())
    expect(api.to('GET /api/users/1/jobs/5/resume')).toHaveLength(1)
    expect(downloads.createObjectURL).toHaveBeenCalledOnce()
  })

  test('GivenOwnResumeFile_WhenUploading_ThenItIsSentAndFileNameIsShown', async () => {
    const api = mockApi({
      [JOB_URL]: { body: makeJob() },
      'PUT /api/users/1/jobs/5/resume': {
        body: makeJob({ hasTailoredResume: true, tailoredResumeFileName: 'mine.txt' }),
      },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.upload(
      await screen.findByLabelText('Upload your own tailored resume'),
      new File(['my resume'], 'mine.txt', { type: 'text/plain' }),
    )

    expect(await screen.findByText('Current file: mine.txt')).toBeInTheDocument()
    const sent = api.to('PUT /api/users/1/jobs/5/resume')[0].body as FormData
    expect((sent.get('file') as File).name).toBe('mine.txt')
  })
})

describe('JobDetailPage delete', () => {
  test('GivenJob_WhenConfirmingDelete_ThenJobIsDeletedAndJobsListOpens', async () => {
    const api = mockApi({
      [JOB_URL]: { body: makeJob() },
      'DELETE /api/users/1/jobs/5': { status: 204 },
      'GET /api/users/1/jobs': { body: [] },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.click(await screen.findByRole('button', { name: 'Delete job' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete' }))

    expect(await screen.findByRole('heading', { name: 'Jobs' })).toBeInTheDocument()
    expect(api.to('DELETE /api/users/1/jobs/5')).toHaveLength(1)
  })

  test('GivenDeleteAsked_WhenCancelling_ThenNothingIsDeleted', async () => {
    const api = mockApi({ [JOB_URL]: { body: makeJob() } })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.click(await screen.findByRole('button', { name: 'Delete job' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(api.to('DELETE /api/users/1/jobs/5')).toHaveLength(0)
    expect(screen.getByRole('button', { name: 'Delete job' })).toBeInTheDocument()
  })

  test('GivenDeleteFails_WhenConfirming_ThenErrorIsShownAndUserStaysOnJob', async () => {
    mockApi({
      [JOB_URL]: { body: makeJob() },
      'DELETE /api/users/1/jobs/5': { status: 500, body: {} },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5')

    await user.click(await screen.findByRole('button', { name: 'Delete job' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong (500).')
    expect(screen.getByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument()
  })
})
