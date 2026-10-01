import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { mockApi, renderApp } from './test/helpers'

const EMPTY_LISTS = {
  'GET /api/users/1/jobs': { body: [] },
  'GET /api/users/1/skills': { body: [] },
}

describe('Layout and routing', () => {
  test('GivenSignedIn_WhenOpeningDashboard_ThenNavigationAndUserNameAreShown', async () => {
    mockApi(EMPTY_LISTS)
    renderApp('/')

    const nav = await screen.findByRole('navigation', { name: 'Main' })
    for (const name of ['Dashboard', 'Jobs', 'Skills', 'Courses', 'Profile']) {
      expect(nav).toHaveTextContent(name)
    }
    expect(screen.getByText('Dana Levi')).toBeInTheDocument()
  })

  test('GivenNavigation_WhenClickingLinks_ThenPagesChange', async () => {
    mockApi({
      ...EMPTY_LISTS,
      'GET /api/users/1/courses': { body: [] },
    })
    const user = userEvent.setup()
    renderApp('/')

    await user.click(await screen.findByRole('link', { name: 'Skills' }))
    expect(await screen.findByRole('heading', { name: 'Skills' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Courses' }))
    expect(await screen.findByRole('heading', { name: 'Courses' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Profile' }))
    expect(await screen.findByRole('heading', { name: 'Profile' })).toBeInTheDocument()
  })

  test('GivenSignedIn_WhenSigningOut_ThenServerSessionEndsAndLoginPageOpens', async () => {
    const api = mockApi({ ...EMPTY_LISTS, 'POST /api/users/1/logout': { status: 204 } })
    const user = userEvent.setup()
    renderApp('/')

    await user.click(await screen.findByRole('button', { name: 'Account menu' }))
    await user.click(screen.getByRole('menuitem', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(api.to('POST /api/users/1/logout')).toHaveLength(1)
    expect(localStorage.getItem('ats.session')).toBeNull()
  })

  test('GivenExpiredSession_WhenPageLoads_ThenUserIsSentToLogin', async () => {
    mockApi({
      'GET /api/users/1/jobs': { status: 401, body: { message: 'Missing or invalid session' } },
      'GET /api/users/1/skills': { status: 401, body: {} },
    })
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your session expired. Please sign in again.')
    await waitFor(() => expect(localStorage.getItem('ats.session')).toBeNull())
  })

  test('GivenSignedOut_WhenOpeningTheRoot_ThenLandingPageIsShown', () => {
    mockApi({})
    renderApp('/', { signedIn: false })

    expect(screen.getByRole('heading', { level: 1, name: 'Get your CV past the ATS' })).toBeInTheDocument()
  })

  test('GivenSignedIn_WhenOpeningTheRoot_ThenDashboardIsShown', async () => {
    mockApi(EMPTY_LISTS)
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Get your CV past the ATS' })).not.toBeInTheDocument()
  })

  test('GivenUnknownAddress_WhenOpening_ThenNotFoundPageIsShown', async () => {
    mockApi({ ...EMPTY_LISTS })
    renderApp('/no/such/page')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the dashboard' })).toHaveAttribute('href', '/')
  })
})
