import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import type { SearchSettings } from '../api/types'
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

const COMPANIES = [{ id: 1, name: 'Jfrog', board: 'greenhouse', slug: 'jfrog' }]

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

  test('GivenCareerPageLink_WhenAddingCompany_ThenItIsSentAndListReloads', async () => {
    const store = [...COMPANIES]
    const api = mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': () => ({ body: [...store] }),
      'POST /api/users/1/search/companies': (call) => {
        store.push({ id: 2, name: 'Acme', board: 'lever', slug: 'acme' })
        return { status: 201, body: store[1] || call.body }
      },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByText('Jfrog')

    await user.type(screen.getByLabelText('Company name or career page link'), 'https://jobs.lever.co/acme')
    await user.click(screen.getByRole('button', { name: 'Add company' }))

    expect(await screen.findByText('Acme')).toBeInTheDocument()
    expect(api.to('POST /api/users/1/search/companies')[0].body).toEqual({ company: 'https://jobs.lever.co/acme' })
  })

  test('GivenUnknownCompany_WhenAdding_ThenServerMessageIsShown', async () => {
    mockApi({
      'GET /api/users/1/search': { body: settings() },
      'GET /api/users/1/search/companies': { body: [] },
      'POST /api/users/1/search/companies': { status: 400, body: { message: 'No career page found for Nowhere.' } },
    })
    const user = userEvent.setup()
    renderApp('/search')
    await screen.findByLabelText('Company name or career page link')

    await user.type(screen.getByLabelText('Company name or career page link'), 'Nowhere')
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
})
