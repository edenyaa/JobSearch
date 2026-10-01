import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import type { Analysis } from '../api/types'
import { makeAnalysis, makeJob, mockApi, renderApp } from '../test/helpers'

function open(analysis: Analysis = makeAnalysis()) {
  return mockApi({
    'GET /api/users/1/jobs/5': { body: makeJob() },
    'GET /api/users/1/jobs/5/analysis': { body: analysis },
  })
}

const ALL_GOOD = {
  inProfile: 0,
  missing: 0,
  keywords: [{ term: 'ledger', inCv: true, mentions: 3 }],
  requirementChecks: [{ id: 'title', label: 'Title', result: 'PASS' as const, detail: 'Title ok' }],
  readinessChecks: [{ id: 'email', label: 'Email', result: 'PASS' as const, detail: 'Email ok' }],
}

describe('AnalysisPage overall estimate', () => {
  test('GivenAnalysis_WhenOpening_ThenOverallEstimateAndHonestyNoteAreShown', async () => {
    open()
    renderApp('/jobs/5/analysis')

    expect(await screen.findByRole('img', { name: 'ATS readiness estimate: 72 percent' })).toBeInTheDocument()
    expect(screen.getByText(/No tool can predict what an ATS or a recruiter will decide/)).toBeInTheDocument()
  })

  test('GivenComponents_WhenOpening_ThenEachShowsItsScoreAndItsWeight', async () => {
    open()
    renderApp('/jobs/5/analysis')

    expect(await screen.findByRole('progressbar', { name: 'Skills score' })).toHaveAttribute('aria-valuenow', '67')
    expect(screen.getByRole('progressbar', { name: 'Other keywords score' })).toHaveAttribute('aria-valuenow', '50')
    expect(screen.getByRole('progressbar', { name: 'Requirements score' })).toHaveAttribute('aria-valuenow', '50')
    expect(screen.getByRole('progressbar', { name: 'CV readiness score' })).toHaveAttribute('aria-valuenow', '50')
    expect(screen.getByText('50% of the score')).toBeInTheDocument()
    expect(screen.getByText('10% of the score')).toBeInTheDocument()
  })

  test('GivenAnalysis_WhenOpening_ThenHeaderLinksBackToTheJobAndToTailoring', async () => {
    open()
    renderApp('/jobs/5/analysis')

    expect(await screen.findByRole('link', { name: '← Back to job' })).toHaveAttribute('href', '/jobs/5')
    expect(screen.getByRole('link', { name: /Tailor CV for this job/ })).toHaveAttribute('href', '/jobs/5')
    expect(screen.getByText(/Backend Engineer · Acme/)).toBeInTheDocument()
  })
})

describe('AnalysisPage skills', () => {
  test('GivenAnalysis_WhenOpening_ThenSkillsMatchAndPotentialRingsAreShown', async () => {
    open()
    renderApp('/jobs/5/analysis')

    expect(await screen.findByRole('img', { name: 'Skills match: 67 percent' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Potential skills match: 100 percent' })).toBeInTheDocument()
    expect(screen.getByText(/2 of the 3 skills this job asks for are in your CV/)).toBeInTheDocument()
  })

  test('GivenAnalysis_WhenOpening_ThenStatusDonutDescribesTheCounts', async () => {
    open()
    renderApp('/jobs/5/analysis')

    expect(
      await screen.findByRole('img', {
        name: 'Skills asked by the job: 2 in your CV, 1 in your profile only, 0 missing',
      }),
    ).toBeInTheDocument()
  })

  test('GivenCategories_WhenOpening_ThenEachHasItsPercentAndCounts', async () => {
    open()
    renderApp('/jobs/5/analysis')

    expect(await screen.findByRole('img', { name: 'Languages: 100 percent in your CV' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Cloud & DevOps: 50 percent in your CV' })).toBeInTheDocument()
    expect(screen.getByText('1 of 2 in your CV')).toBeInTheDocument()
  })

  test('GivenTerms_WhenOpening_ThenChipsShowStatusAndMarkNiceToHaveSkills', async () => {
    open()
    renderApp('/jobs/5/analysis')

    const java = await screen.findByText('Java', { selector: '.chip' })
    const kubernetes = screen.getByText('Kubernetes', { selector: '.chip' })

    expect(java).toHaveClass('chip-good')
    expect(java).not.toHaveClass('chip-soft')
    expect(kubernetes).toHaveClass('chip-warn')
    expect(kubernetes).toHaveClass('chip-soft')
    expect(kubernetes).toHaveTextContent('In your profile only, nice to have')
  })

  test('GivenMissingSkill_WhenOpening_ThenItIsShownAsMissing', async () => {
    open(
      makeAnalysis({
        missing: 1,
        terms: [{ name: 'Terraform', category: 'CLOUD_DEVOPS', status: 'MISSING', importance: 'REQUIRED' }],
      }),
    )
    renderApp('/jobs/5/analysis')

    expect(await screen.findByText('Terraform', { selector: '.chip' })).toHaveClass('chip-bad')
  })

  test('GivenCvSkills_WhenOpening_ThenCompositionDonutAndExtraSkillsAreShown', async () => {
    open()
    renderApp('/jobs/5/analysis')

    expect(
      await screen.findByRole('img', { name: 'Skills found in your CV by category: Languages 2, Cloud & DevOps 1' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Git', { selector: '.chip' })).toBeInTheDocument()
  })

  test('GivenNoSkillsInCv_WhenOpening_ThenCompositionSaysSo', async () => {
    open(makeAnalysis({ cvCategories: [], extraCvSkills: [] }))
    renderApp('/jobs/5/analysis')

    expect(await screen.findByText('We did not find known skills in your CV.')).toBeInTheDocument()
    expect(screen.queryByText('Other skills in your CV')).not.toBeInTheDocument()
  })
})

describe('AnalysisPage keywords and checks', () => {
  test('GivenKeywords_WhenOpening_ThenMissingAndPresentOnesAreMarkedWithMentions', async () => {
    open()
    renderApp('/jobs/5/analysis')

    const missing = await screen.findByText('payment systems', { selector: '.chip' })
    const present = screen.getByText('ledger', { selector: '.chip' })

    expect(missing).toHaveClass('chip-bad')
    expect(missing).toHaveTextContent('×2')
    expect(missing).toHaveTextContent('missing from your CV')
    expect(present).toHaveClass('chip-good')
  })

  test('GivenNoKeywords_WhenOpening_ThenKeywordsSectionIsHidden', async () => {
    open(makeAnalysis({ keywords: [] }))
    renderApp('/jobs/5/analysis')

    await screen.findByRole('heading', { name: 'Analysis' })

    expect(screen.queryByText('Other keywords in the posting')).not.toBeInTheDocument()
  })

  test('GivenRequirementChecks_WhenOpening_ThenResultsAndDetailsAreListed', async () => {
    open()
    renderApp('/jobs/5/analysis')

    const section = (await screen.findByRole('heading', { name: 'Requirements check' })).closest('section')!

    expect(within(section).getByText('Years of experience')).toBeInTheDocument()
    expect(within(section).getByText(/asks for 8\+ years; your CV shows about 6 years/)).toBeInTheDocument()
    expect(within(section).getByText(/Failed/)).toBeInTheDocument()
    expect(within(section).getByText(/Passed/)).toBeInTheDocument()
  })

  test('GivenNoRequirementChecks_WhenOpening_ThenRequirementsSectionIsHidden', async () => {
    open(makeAnalysis({ requirementChecks: [] }))
    renderApp('/jobs/5/analysis')

    await screen.findByRole('heading', { name: 'Analysis' })

    expect(screen.queryByRole('heading', { name: 'Requirements check' })).not.toBeInTheDocument()
  })

  test('GivenReadinessChecks_WhenOpening_ThenChecklistIsShown', async () => {
    open()
    renderApp('/jobs/5/analysis')

    const section = (await screen.findByRole('heading', { name: 'Is your CV easy for an ATS to read?' })).closest('section')!

    expect(within(section).getByText('Email address is present')).toBeInTheDocument()
    expect(within(section).getByText('Has a Skills section')).toBeInTheDocument()
  })
})

describe('AnalysisPage next steps', () => {
  test('GivenGaps_WhenOpening_ThenNextStepsMentionProfileSkillsMissingKeywordsAndFailedChecks', async () => {
    open(makeAnalysis({ missing: 2 }))
    renderApp('/jobs/5/analysis')

    const tips = (await screen.findByRole('heading', { name: 'What to do next' })).closest('section')!

    expect(within(tips).getByText(/already have/)).toBeInTheDocument()
    expect(within(tips).getByText(/skills are missing/)).toBeInTheDocument()
    expect(within(tips).getByRole('link', { name: 'Skills page' })).toHaveAttribute('href', '/skills')
    expect(within(tips).getByText('payment systems')).toBeInTheDocument()
    expect(within(tips).getByText(/asks for 8\+ years/)).toBeInTheDocument()
    expect(within(tips).getByText(/Add a "Skills" section/)).toBeInTheDocument()
  })

  test('GivenNothingToFix_WhenOpening_ThenNextStepsSaysEverythingLooksGood', async () => {
    open(makeAnalysis(ALL_GOOD))
    renderApp('/jobs/5/analysis')

    const tips = (await screen.findByRole('heading', { name: 'What to do next' })).closest('section')!

    expect(within(tips).getByText(/Everything we can check looks good/)).toBeInTheDocument()
    expect(within(tips).getByText(/a recruiter still reads your CV/)).toBeInTheDocument()
  })
})

describe('AnalysisPage special cases', () => {
  test('GivenNoKnownSkillsInPosting_WhenOpening_ThenExplanationShowsButOtherChecksStillAppear', async () => {
    open(makeAnalysis({ total: 0, matched: 0, inProfile: 0, missing: 0, terms: [], categories: [] }))
    renderApp('/jobs/5/analysis')

    expect(await screen.findByRole('heading', { name: 'No known skills found in this posting' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Edit the job description' })).toHaveAttribute('href', '/jobs/5/edit')
    expect(screen.getByRole('heading', { name: 'Is your CV easy for an ATS to read?' })).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: /Skills match/ })).not.toBeInTheDocument()
  })

  test('GivenNoMainCv_WhenOpening_ThenUploadPromptIsShown', async () => {
    mockApi({
      'GET /api/users/1/jobs/5': { body: makeJob() },
      'GET /api/users/1/jobs/5/analysis': { status: 400, body: { message: 'Upload a main CV first.' } },
    })
    renderApp('/jobs/5/analysis')

    expect(await screen.findByRole('heading', { name: 'Upload your main CV first' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Upload your CV' })).toHaveAttribute('href', '/profile')
  })

  test('GivenOtherServerError_WhenOpening_ThenMessageIsShown', async () => {
    mockApi({
      'GET /api/users/1/jobs/5': { body: makeJob() },
      'GET /api/users/1/jobs/5/analysis': { status: 500, body: {} },
    })
    renderApp('/jobs/5/analysis')

    expect(await screen.findByRole('alert')).toHaveTextContent('The server hit an unexpected error')
  })

  test('GivenUnknownJob_WhenOpening_ThenMessageIsShown', async () => {
    mockApi({
      'GET /api/users/1/jobs/5': { status: 404, body: { message: 'Job 5 not found' } },
      'GET /api/users/1/jobs/5/analysis': { status: 404, body: { message: 'Job 5 not found' } },
    })
    renderApp('/jobs/5/analysis')

    expect(await screen.findByRole('alert')).toHaveTextContent('Job 5 not found')
  })

  test('GivenAnalysisPage_WhenClickingTailor_ThenJobPageOpens', async () => {
    mockApi({
      'GET /api/users/1/jobs/5': { body: makeJob() },
      'GET /api/users/1/jobs/5/analysis': { body: makeAnalysis() },
    })
    const user = userEvent.setup()
    renderApp('/jobs/5/analysis')

    await user.click(await screen.findByRole('link', { name: /Tailor CV for this job/ }))

    expect(await screen.findByRole('button', { name: 'Tailor my CV with AI' })).toBeInTheDocument()
  })
})
