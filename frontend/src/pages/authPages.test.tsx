import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { makeJob, mockApi, renderApp, USER } from '../test/helpers'
import { isStrongPassword } from './RegisterPage'

const LOGIN_OK = { body: { token: 'tok-1', user: USER } }
const EMPTY_LISTS = {
  'GET /api/users/1/jobs': { body: [] },
  'GET /api/users/1/skills': { body: [] },
}

describe('LoginPage', () => {
  test('GivenValidCredentials_WhenSubmitting_ThenUserSeesTheDashboard', async () => {
    const api = mockApi({ 'POST /api/users/login': LOGIN_OK, ...EMPTY_LISTS })
    const user = userEvent.setup()
    renderApp('/login', { signedIn: false })

    await user.type(screen.getByLabelText('Email'), 'dana@example.com')
    await user.type(screen.getByLabelText('Password'), 'secret123')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(api.to('POST /api/users/login')[0].body).toEqual({ email: 'dana@example.com', password: 'secret123' })
    expect(api.to('GET /api/users/1/jobs')[0].headers.get('Authorization')).toBe('Bearer tok-1')
  })

  test('GivenWrongCredentials_WhenSubmitting_ThenServerMessageIsShownAndUserStaysOnLogin', async () => {
    mockApi({ 'POST /api/users/login': { status: 401, body: { message: 'Invalid email or password.' } } })
    const user = userEvent.setup()
    renderApp('/login', { signedIn: false })

    await user.type(screen.getByLabelText('Email'), 'dana@example.com')
    await user.type(screen.getByLabelText('Password'), 'wrongpass1')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })

  test('GivenBackendDown_WhenSubmitting_ThenHelpfulMessageIsShown', async () => {
    mockApi({})
    const user = userEvent.setup()
    renderApp('/login', { signedIn: false })
    globalThis.fetch = (() => Promise.reject(new TypeError('Failed to fetch'))) as typeof fetch

    await user.type(screen.getByLabelText('Email'), 'dana@example.com')
    await user.type(screen.getByLabelText('Password'), 'secret123')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server')
  })

  test('GivenAlreadySignedIn_WhenOpeningLogin_ThenUserIsRedirectedToTheDashboard', async () => {
    mockApi(EMPTY_LISTS)
    renderApp('/login')

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
  })

  test('GivenSignedOut_WhenOpeningProtectedPage_ThenUserIsSentToLogin', async () => {
    mockApi({})
    renderApp('/jobs', { signedIn: false })

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  test('GivenRedirectedFromProtectedPage_WhenLoggingIn_ThenUserReturnsToThatPage', async () => {
    mockApi({ 'POST /api/users/login': LOGIN_OK, 'GET /api/users/1/jobs': { body: [makeJob()] } })
    const user = userEvent.setup()
    renderApp('/jobs', { signedIn: false })

    await user.type(await screen.findByLabelText('Email'), 'dana@example.com')
    await user.type(screen.getByLabelText('Password'), 'secret123')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Jobs' })).toBeInTheDocument()
    expect(await screen.findByText('Backend Engineer')).toBeInTheDocument()
  })

  test('GivenNoticeInNavigationState_WhenOpeningLogin_ThenNoticeIsShown', () => {
    mockApi({})
    renderApp({ pathname: '/login', state: { notice: 'Password changed. Please sign in again.' } }, { signedIn: false })

    expect(screen.getByRole('status')).toHaveTextContent('Password changed. Please sign in again.')
  })

  test('GivenLoginPage_WhenClickingCreateAccount_ThenRegisterPageOpens', async () => {
    mockApi({})
    const user = userEvent.setup()
    renderApp('/login', { signedIn: false })

    await user.click(screen.getByRole('link', { name: 'Create an account' }))

    expect(screen.getByRole('heading', { name: 'Create your account' })).toBeInTheDocument()
  })
})

describe('RegisterPage', () => {
  async function fillAndSubmit(password = 'secret123') {
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Name'), 'Dana Levi')
    await user.type(screen.getByLabelText('Email'), 'dana@example.com')
    await user.type(screen.getByLabelText('Password'), password)
    await user.click(screen.getByRole('button', { name: 'Create account' }))
  }

  test('GivenValidDetails_WhenSubmitting_ThenAccountIsCreatedAndUserIsSignedIn', async () => {
    const api = mockApi({
      'POST /api/users': { status: 201, body: USER },
      'POST /api/users/login': LOGIN_OK,
      ...EMPTY_LISTS,
    })
    renderApp('/register', { signedIn: false })

    await fillAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(api.to('POST /api/users')[0].body).toEqual({
      name: 'Dana Levi',
      email: 'dana@example.com',
      password: 'secret123',
    })
  })

  test('GivenWeakPassword_WhenSubmitting_ThenRuleIsShownAndNothingIsSent', async () => {
    const api = mockApi({})
    renderApp('/register', { signedIn: false })

    await fillAndSubmit('short')

    expect(await screen.findByRole('alert')).toHaveTextContent('at least 7 characters')
    expect(api.calls).toHaveLength(0)
  })

  test('GivenEmailAlreadyRegistered_WhenSubmitting_ThenServerMessageIsShown', async () => {
    mockApi({ 'POST /api/users': { status: 409, body: { message: 'Email already registered' } } })
    renderApp('/register', { signedIn: false })

    await fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Email already registered')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled())
  })

  test('GivenRegisterPage_WhenClickingSignIn_ThenLoginPageOpens', async () => {
    mockApi({})
    const user = userEvent.setup()
    renderApp('/register', { signedIn: false })

    await user.click(screen.getByRole('link', { name: 'Sign in' }))

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})

describe('isStrongPassword', () => {
  test.each([
    ['abcdef1', true],
    ['secret123', true],
    ['abcdef', false],
    ['abcdefgh', false],
    ['12345678', false],
    ['a1', false],
    ['', false],
  ])('GivenPassword_%s_WhenChecking_ThenResultIs_%s', (password, expected) => {
    expect(isStrongPassword(password)).toBe(expected)
  })
})
