import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import type { SearchedCompany, SearchSettings } from '../api/types'
import { mockApi, renderApp } from '../test/helpers'

function settings(overrides: Partial<SearchSettings> = {}): SearchSettings {
  return {
    titles: ['developer'],
    excludedTitles: ['senior'],
    locations: ['israel'],
    priorityLocations: ['rehovot'],
    minMatchPercent: 20,
    minFitPercent: 40,
    daily: false,
    lastRunAt: null,
    lastRunSummary: null,
    running: false,
    aggregatorEnabled: false,
    importing: false,
    importStatus: null,
    ...overrides,
  }
}

const COMPANIES: SearchedCompany[] = [
  { id: 1, name: 'Jfrog', board: 'greenhouse', slug: 'jfrog', link: null, openings: null },
]

describe('SearchPage', () => {
  test('GivenSettings_WhenOpening_ThenTheyAndTheSearchedCompaniesAreShown', async () => {
    mockApi({
      'GET /api/users/1/search': { body: settings({ lastRunAt: '2026-01-01T06:00:00Z', lastRunSummary: 'Saved 3.' }) },
      'GET /api/users/1/search/companies': { body: COMPANIES },
    })
    renderApp('/search')

    expect(await screen.findByRole('heading', { name: 'Job search' })).toBeInTheDocument()
    expect(screen.getByText('developer')).toBeInTheDocument()
    expect(screen.getByLabelText('Minimum skill match (%)')).toHaveValue(20)
    expect(await screen.findByText('Jfrog')).toBeInTheDocument()
    expect(screen.getByText(/Saved 3\./)).toBeInTheDocument()
  })

  test('GivenAggregatorKey_WhenOpening_ThenLinkedinGlassdoorSourceIsShownAsOn', async () => {
    mockApi({
      'GET /api/users/1/search': { body: settings({ aggregatorEnabled: true }) },
      'GET /api/users/1/search/companies': { body: [] },
    })
    renderApp('/search')

    expect(await screen.findByText(/LinkedIn, Glassdoor and Indeed listings are included/)).toBeInTheDocument()
  })

  test('GivenNoAggregatorKey_WhenOpening_ThenHowToTurnItOnIsShown', async () => {
    mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': { body: [] },
    })
    renderApp('/search')

    expect(await screen.findByText(/JSEARCH_API_KEY/)).toBeInTheDocument()
  })

  test('GivenChangedSettings_WhenRunningNow_ThenTheyAreSavedFirstAndTheSearchStarts', async () => {
    const api = mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': { body: [] },
      'PUT /api/users/1/search': (call) => ({ body: settings(call.body as Partial<SearchSettings>) }),
      'POST /api/users/1/search/run': { status: 202, body: settings({ running: true }) },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByLabelText('Minimum skill match (%)')

    await user.click(screen.getByLabelText(/Also search by itself/))
    await user.click(screen.getByRole('button', { name: 'Run search now' }))

    expect(await screen.findByText('Searching…', { selector: 'strong' })).toBeInTheDocument()
    expect(api.to('PUT /api/users/1/search')[0].body).toMatchObject({ daily: true, titles: ['developer'] })
    expect(api.to('POST /api/users/1/search/run')).toHaveLength(1)
  })

  test('GivenSettings_WhenOpening_ThenPriorityPlacesAndMinimumFitAreShown', async () => {
    mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': { body: [] },
    })
    renderApp('/search')

    expect(await screen.findByText('rehovot')).toBeInTheDocument()
    expect(screen.getByLabelText('Minimum fit rated by the AI (%)')).toHaveValue(40)
  })

  test('GivenEditedSettings_WhenUsingRecommended_ThenServerSettingsReplaceTheForm', async () => {
    const api = mockApi({
      'GET /api/users/1/search': { body: settings({ titles: ['x'] }) },
      'GET /api/users/1/search/companies': { body: [] },
      'POST /api/users/1/search/reset': { body: settings({ titles: ['qa', 'junior'] }) },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByText('x')

    await user.click(screen.getByRole('button', { name: 'Use recommended settings' }))

    expect(await screen.findByText('qa')).toBeInTheDocument()
    expect(screen.getByText('junior')).toBeInTheDocument()
    expect(screen.queryByText('x')).not.toBeInTheDocument()
    expect(api.to('POST /api/users/1/search/reset')).toHaveLength(1)
  })

  test('GivenPastedNames_WhenImporting_ThenTheyAreSentAndProgressIsShown', async () => {
    const api = mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': { body: [] },
      'POST /api/users/1/search/companies/import': {
        status: 202,
        body: settings({ importing: true, importStatus: 'Looked up 1 of 2 companies, added 0 career pages so far...' }),
      },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByLabelText('Company names to import')

    await user.type(screen.getByLabelText('Company names to import'), 'Alphaco{Enter}Betaco, Gammaco')
    await user.click(screen.getByRole('button', { name: 'Import these companies' }))

    expect(await screen.findByText(/Looked up 1 of 2 companies/)).toBeInTheDocument()
    expect(api.to('POST /api/users/1/search/companies/import')[0].body).toEqual({
      names: ['Alphaco', 'Betaco', 'Gammaco'],
    })
  })

  test('GivenBuiltInList_WhenImporting_ThenNoNamesAreSent', async () => {
    const api = mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': { body: [] },
      'POST /api/users/1/search/companies/import': { status: 202, body: settings({ importing: true }) },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByLabelText('Company names to import')

    await user.click(screen.getByRole('button', { name: 'Add well-known Israeli tech companies' }))

    expect(await screen.findByRole('button', { name: 'Looking up careers pages…' })).toBeDisabled()
    expect(api.to('POST /api/users/1/search/companies/import')[0].body).toEqual({ names: [] })
  })

  test('GivenFinishedImport_WhenOpening_ThenItsResultIsShown', async () => {
    mockApi({
      'GET /api/users/1/search': { body: settings({ importStatus: 'Looked up 100 companies: added 31 career pages.' }) },
      'GET /api/users/1/search/companies': { body: [] },
    })
    renderApp('/search')

    expect(await screen.findByText(/added 31 career pages/)).toBeInTheDocument()
  })

  test('GivenNameAndLink_WhenAddingCompany_ThenBothAreSentAndListReloads', async () => {
    const store = [...COMPANIES]
    const api = mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': () => ({ body: [...store] }),
      'POST /api/users/1/search/companies': () => {
        const added: SearchedCompany = { id: 2, name: 'Acme', board: 'lever', slug: 'acme', link: null, openings: 7 }
        store.push(added)
        return { status: 201, body: added }
      },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByText('Jfrog')

    await user.type(screen.getByLabelText('Company name'), 'Acme')
    await user.type(screen.getByLabelText('Careers page link (optional)'), 'https://jobs.lever.co/acme')
    await user.click(screen.getByRole('button', { name: 'Add company' }))

    expect(await screen.findByText('Added Acme: 7 openings found on its page.')).toBeInTheDocument()
    expect(await screen.findByText('Lever')).toBeInTheDocument()
    expect(api.to('POST /api/users/1/search/companies')[0].body).toEqual({
      name: 'Acme',
      link: 'https://jobs.lever.co/acme',
    })
  })

  test('GivenLongAmazonLinkOnly_WhenAddingCompany_ThenTheWholeLinkIsSentAsIs', async () => {
    const link =
      'https://amazon.jobs/content/en/teams/annapurna-labs?country%5B%5D=IL&cmpid=SA_GOAG200485C&gad_source=1' +
      '&gad_campaignid=23946687794&gbraid=0AAAABD62HsNhMqA9ww&gclid=CjwKCAiAqabXBhBUEiwAw_SQpcCnMcK0tj9jI#search'
    const api = mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': { body: [] },
      'POST /api/users/1/search/companies': {
        status: 201,
        body: { id: 3, name: 'Amazon', board: 'amazon', slug: 'ISR', link: null, openings: 173 },
      },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByLabelText('Company name')

    await user.click(screen.getByLabelText('Careers page link (optional)'))
    await user.paste(link)
    await user.click(screen.getByRole('button', { name: 'Add company' }))

    expect(await screen.findByText('Added Amazon: 173 openings found on its page.')).toBeInTheDocument()
    expect(api.to('POST /api/users/1/search/companies')[0].body).toEqual({ name: '', link })
  })

  test('GivenPageWithNoReadableJobs_WhenAddingCompany_ThenItIsAddedWithAClearWarning', async () => {
    mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': { body: [] },
      'POST /api/users/1/search/companies': {
        status: 201,
        body: { id: 4, name: 'Acme Corp', board: 'page', slug: 'p1', link: 'https://acme.com/careers', openings: 0 },
      },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByLabelText('Company name')

    await user.type(screen.getByLabelText('Company name'), 'Acme Corp')
    await user.type(screen.getByLabelText('Careers page link (optional)'), 'https://acme.com/careers')
    await user.click(screen.getByRole('button', { name: 'Add company' }))

    expect(await screen.findByText(/Added Acme Corp, but no job listings could be read/)).toBeInTheDocument()
  })

  test('GivenNothingTyped_WhenAddingCompany_ThenNothingIsSentAndItSaysWhatToEnter', async () => {
    const api = mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': { body: [] },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByLabelText('Company name')

    await user.click(screen.getByRole('button', { name: 'Add company' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter the company name, the link of its careers page, or both.')
    expect(api.to('POST /api/users/1/search/companies')).toHaveLength(0)
  })

  test('GivenUnknownCompany_WhenAdding_ThenServerMessageIsShownNextToTheForm', async () => {
    mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': { body: [] },
      'POST /api/users/1/search/companies': { status: 400, body: { message: 'No career page found for Nowhere.' } },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByLabelText('Company name')

    await user.type(screen.getByLabelText('Company name'), 'Nowhere')
    await user.click(screen.getByRole('button', { name: 'Add company' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No career page found for Nowhere.')
  })

  test('GivenSearchedCompany_WhenRemovingWithConfirmation_ThenItIsDeletedAndListReloads', async () => {
    const store = [...COMPANIES]
    const api = mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': () => ({ body: [...store] }),
      'DELETE /api/users/1/search/companies/1': () => {
        store.splice(0, 1)
        return { status: 204 }
      },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByText('Jfrog')

    await user.click(screen.getByRole('button', { name: 'Remove' }))
    await user.click(screen.getByRole('button', { name: 'Confirm remove' }))

    await waitFor(() => expect(screen.queryByText('Jfrog')).not.toBeInTheDocument())
    expect(api.to('DELETE /api/users/1/search/companies/1')).toHaveLength(1)
  })

  test('GivenCompaniesWithoutPage_WhenOpening_ThenTheyAreListedAndAddLinkFillsTheForm', async () => {
    mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': {
        body: [
          { id: 5, name: 'Globex', board: null, slug: null, link: null, openings: null },
          { id: 6, name: 'Initech', board: null, slug: null, link: 'https://initech.test/jobs', openings: null },
        ] as SearchedCompany[],
      },
    })
    const user = userEvent.setup()
    renderApp('/search')

    expect(await screen.findByText('Globex')).toBeInTheDocument()
    expect(screen.getByText('No careers page')).toBeInTheDocument()
    expect(screen.getByText('No jobs could be read from its page')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Remove' })).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: 'Add careers page link for Globex' }))
    expect(screen.getByLabelText('Company name')).toHaveValue('Globex')
    expect(screen.getByLabelText('Careers page link (optional)')).toHaveFocus()
  })
})
