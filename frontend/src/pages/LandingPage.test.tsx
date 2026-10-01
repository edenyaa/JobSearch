import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { mockApi, renderApp } from '../test/helpers'

describe('LandingPage', () => {
  test('GivenVisitor_WhenOpening_ThenHeadlineAndPitchAreShown', () => {
    mockApi({})
    renderApp('/', { signedIn: false })

    expect(screen.getByRole('heading', { level: 1, name: 'Get your CV past the ATS' })).toBeInTheDocument()
    expect(screen.getByText(/Upload your CV once/)).toBeInTheDocument()
  })

  test('GivenVisitor_WhenOpening_ThenCallsToActionLeadToRegisterAndSignIn', () => {
    mockApi({})
    renderApp('/', { signedIn: false })

    expect(screen.getByRole('link', { name: 'Get started free' })).toHaveAttribute('href', '/register')
    expect(screen.getByRole('link', { name: 'Get started' })).toHaveAttribute('href', '/register')
    expect(screen.getByRole('link', { name: 'Create your free account' })).toHaveAttribute('href', '/register')
    for (const link of screen.getAllByRole('link', { name: 'Sign in' })) {
      expect(link).toHaveAttribute('href', '/login')
    }
  })

  test('GivenVisitor_WhenReadingHowItWorks_ThenFourStepsAreListedInOrder', () => {
    mockApi({})
    renderApp('/', { signedIn: false })

    const steps = within(screen.getByRole('list', { name: '' })).getAllByRole('listitem')

    expect(steps).toHaveLength(4)
    expect(steps[0]).toHaveTextContent('Upload your CV')
    expect(steps[1]).toHaveTextContent('Add a job')
    expect(steps[2]).toHaveTextContent('See your match')
    expect(steps[3]).toHaveTextContent('Tailor with AI')
  })

  test('GivenVisitor_WhenReadingTrustPoints_ThenPrivacyAndTruthfulnessAreMentioned', () => {
    mockApi({})
    renderApp('/', { signedIn: false })

    expect(screen.getByText('Never invent experience')).toBeInTheDocument()
    expect(screen.getByText('Your details stay private')).toBeInTheDocument()
  })

  test('GivenVisitor_WhenClickingGetStarted_ThenRegisterPageOpens', async () => {
    mockApi({})
    const user = userEvent.setup()
    renderApp('/', { signedIn: false })

    await user.click(screen.getByRole('link', { name: 'Get started free' }))

    expect(screen.getByRole('heading', { name: 'Create your account' })).toBeInTheDocument()
  })

  test('GivenVisitor_WhenClickingSignIn_ThenLoginPageOpens', async () => {
    mockApi({})
    const user = userEvent.setup()
    renderApp('/', { signedIn: false })

    await user.click(within(screen.getByRole('navigation', { name: 'Account' })).getByRole('link', { name: 'Sign in' }))

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  test('GivenLandingPreview_WhenReading_ThenItIsHiddenFromAssistiveTechnology', () => {
    mockApi({})
    const { container } = renderApp('/', { signedIn: false })

    expect(container.querySelector('.hero-preview')).toHaveAttribute('aria-hidden', 'true')
  })
})
