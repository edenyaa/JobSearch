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
    minMatchPercent: 20,
    daily: false,
    lastRunAt: null,
    lastRunSummary: null,
    running: false,
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
