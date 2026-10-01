import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { makeJob, mockApi, renderApp } from '../test/helpers'
import { crumbsFor } from './Layout'

const LISTS = {
  'GET /api/users/1/jobs': { body: [makeJob({ id: 1, title: 'Alpha' }), makeJob({ id: 2, title: 'Zeta' })] },
  'GET /api/users/1/skills': { body: [] },
}

describe('crumbsFor', () => {
  test('GivenKnownPaths_WhenBuildingCrumbs_ThenSectionAndDetailAreReturned', () => {
    expect(crumbsFor('/')).toEqual(['Dashboard'])
    expect(crumbsFor('/jobs')).toEqual(['Jobs'])
    expect(crumbsFor('/jobs/new')).toEqual(['Jobs', 'New job'])
    expect(crumbsFor('/jobs/5')).toEqual(['Jobs', 'Job details'])
    expect(crumbsFor('/jobs/5/analysis')).toEqual(['Jobs', 'Analysis'])
    expect(crumbsFor('/skills')).toEqual(['Skills'])
    expect(crumbsFor('/nowhere')).toEqual(['Page'])
  })
})

describe('Layout shell', () => {
  test('GivenJobsPage_WhenOpening_ThenBreadcrumbShowsWorkspaceAndPage', async () => {
    mockApi(LISTS)
    renderApp('/jobs')

    const crumbs = await screen.findByRole('navigation', { name: 'Breadcrumb' })

    expect(crumbs).toHaveTextContent('Workspace')
    expect(crumbs).toHaveTextContent('Jobs')
  })

  test('GivenSidebar_WhenReading_ThenUserNameAndEmailAreShownInTheAccountCard', async () => {
    mockApi(LISTS)
    renderApp('/jobs')

    const card = await screen.findByRole('button', { name: 'Account menu' })

    expect(card).toHaveTextContent('Dana Levi')
    expect(card).toHaveTextContent('dana@example.com')
  })

  test('GivenAccountMenu_WhenOpeningAndPressingEscape_ThenItCloses', async () => {
    mockApi(LISTS)
    const user = userEvent.setup()
    renderApp('/jobs')

    await user.click(await screen.findByRole('button', { name: 'Account menu' }))
    expect(screen.getByRole('menuitem', { name: 'Sign out' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Account settings' })).toHaveAttribute('href', '/profile')
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('menuitem', { name: 'Sign out' })).not.toBeInTheDocument()
  })

  test('GivenNotificationsButton_WhenClicking_ThenEmptyStateIsShown', async () => {
    mockApi(LISTS)
    const user = userEvent.setup()
    renderApp('/jobs')

    await user.click(await screen.findByRole('button', { name: 'Notifications' }))

    expect(within(screen.getByRole('dialog', { name: 'Notifications' })).getByText('You are all caught up.')).toBeInTheDocument()
  })

  test('GivenSearchButton_WhenClicking_ThenPaletteOpensAndEscapeClosesIt', async () => {
    mockApi(LISTS)
    const user = userEvent.setup()
    renderApp('/jobs')

    await user.click(await screen.findByRole('button', { name: 'Search' }))
    expect(screen.getByRole('dialog', { name: 'Search' })).toBeInTheDocument()
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: 'Search' })).not.toBeInTheDocument()
  })

  test('GivenControlK_WhenPressed_ThenPaletteOpensWithFocusedInput', async () => {
    mockApi(LISTS)
    const user = userEvent.setup()
    renderApp('/jobs')
    await screen.findByRole('button', { name: 'Search' })

    await user.keyboard('{Control>}k{/Control}')

    expect(screen.getByLabelText('Search pages and actions')).toHaveFocus()
  })

  test('GivenPalette_WhenTypingAndPressingEnter_ThenFirstMatchOpens', async () => {
    mockApi({ ...LISTS, 'GET /api/users/1/courses': { body: [] } })
    const user = userEvent.setup()
    renderApp('/jobs')
    await user.click(await screen.findByRole('button', { name: 'Search' }))

    await user.type(screen.getByLabelText('Search pages and actions'), 'courses{Enter}')

    expect(await screen.findByRole('heading', { name: 'Courses' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Search' })).not.toBeInTheDocument()
  })

  test('GivenPalette_WhenTypingUnknownText_ThenNoResultsIsShown', async () => {
    mockApi(LISTS)
    const user = userEvent.setup()
    renderApp('/jobs')
    await user.click(await screen.findByRole('button', { name: 'Search' }))

    await user.type(screen.getByLabelText('Search pages and actions'), 'zzzz')

    expect(screen.getByText('No results')).toBeInTheDocument()
  })

  test('GivenPalette_WhenUsingArrowKeysAndEnter_ThenTheHighlightedActionRuns', async () => {
    mockApi(LISTS)
    const user = userEvent.setup()
    renderApp('/jobs')
    await user.click(await screen.findByRole('button', { name: 'Search' }))

    await user.keyboard('{ArrowDown}{ArrowDown}{Enter}')

    expect(await screen.findByRole('heading', { name: 'Add job' })).toBeInTheDocument()
  })
})
