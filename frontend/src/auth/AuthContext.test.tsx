import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test, vi } from 'vitest'
import { request } from '../api/client'
import { mockApi, USER } from '../test/helpers'
import { AuthProvider, useAuth } from './AuthContext'

function Probe() {
  const auth = useAuth()
  return (
    <div>
      <span data-testid="who">{auth.user?.name ?? 'nobody'}</span>
      <button onClick={() => auth.login('dana@example.com', 'secret123').catch(() => undefined)}>login</button>
      <button onClick={() => auth.register('Dana Levi', 'dana@example.com', 'secret123').catch(() => undefined)}>
        register
      </button>
      <button onClick={() => auth.logout()}>logout</button>
      <button onClick={() => auth.updateUser({ ...USER, name: 'Renamed' })}>rename</button>
    </div>
  )
}

function renderProbe() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )
}

const LOGIN_REPLY = { body: { token: 'tok-1', user: USER } }

describe('AuthProvider', () => {
  test('GivenCredentials_WhenLoggingIn_ThenSessionIsStoredAndUserIsExposed', async () => {
    mockApi({ 'POST /api/users/login': LOGIN_REPLY })
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByText('login'))

    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('Dana Levi'))
    expect(JSON.parse(localStorage.getItem('ats.session')!).token).toBe('tok-1')
  })

  test('GivenWrongCredentials_WhenLoggingIn_ThenNoSessionIsCreated', async () => {
    mockApi({ 'POST /api/users/login': { status: 401, body: { message: 'Invalid email or password.' } } })
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByText('login'))

    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('nobody'))
    expect(localStorage.getItem('ats.session')).toBeNull()
  })

  test('GivenStoredSession_WhenMounting_ThenUserIsRestored', () => {
    localStorage.setItem('ats.session', JSON.stringify({ token: 't', user: USER }))

    renderProbe()

    expect(screen.getByTestId('who')).toHaveTextContent('Dana Levi')
  })

  test('GivenCorruptStoredSession_WhenMounting_ThenNobodyIsSignedIn', () => {
    localStorage.setItem('ats.session', '{not json')

    renderProbe()

    expect(screen.getByTestId('who')).toHaveTextContent('nobody')
  })

  test('GivenSignedIn_WhenLoggingOut_ThenServerLogoutIsCalledAndSessionCleared', async () => {
    const api = mockApi({ 'POST /api/users/1/logout': { status: 204 } })
    localStorage.setItem('ats.session', JSON.stringify({ token: 't', user: USER }))
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByText('logout'))

    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('nobody'))
    expect(api.to('POST /api/users/1/logout')).toHaveLength(1)
    expect(localStorage.getItem('ats.session')).toBeNull()
  })

  test('GivenServerLogoutFails_WhenLoggingOut_ThenSessionIsStillCleared', async () => {
    mockApi({ 'POST /api/users/1/logout': { status: 500, body: {} } })
    localStorage.setItem('ats.session', JSON.stringify({ token: 't', user: USER }))
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByText('logout'))

    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('nobody'))
    expect(localStorage.getItem('ats.session')).toBeNull()
  })

  test('GivenNewDetails_WhenRegistering_ThenAccountIsCreatedAndLoggedIn', async () => {
    const api = mockApi({ 'POST /api/users': { status: 201, body: USER }, 'POST /api/users/login': LOGIN_REPLY })
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByText('register'))

    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('Dana Levi'))
    expect(api.to('POST /api/users')[0].body).toEqual({
      name: 'Dana Levi',
      email: 'dana@example.com',
      password: 'secret123',
    })
  })

  test('GivenEmailTaken_WhenRegistering_ThenNoLoginIsAttempted', async () => {
    const api = mockApi({ 'POST /api/users': { status: 409, body: { message: 'Email already registered' } } })
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByText('register'))

    await waitFor(() => expect(api.to('POST /api/users')).toHaveLength(1))
    expect(api.to('POST /api/users/login')).toHaveLength(0)
    expect(screen.getByTestId('who')).toHaveTextContent('nobody')
  })

  test('GivenSignedIn_WhenServerAnswers401_ThenSessionExpires', async () => {
    mockApi({ 'GET /api/users/1/jobs': { status: 401, body: {} } })
    localStorage.setItem('ats.session', JSON.stringify({ token: 't', user: USER }))
    renderProbe()

    await act(async () => {
      await request('GET', '/users/1/jobs').catch(() => undefined)
    })

    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('nobody'))
    expect(localStorage.getItem('ats.session')).toBeNull()
  })

  test('GivenSignedIn_WhenUpdatingUser_ThenStoredSessionHasTheNewUser', async () => {
    localStorage.setItem('ats.session', JSON.stringify({ token: 't', user: USER }))
    const user = userEvent.setup()
    renderProbe()

    await user.click(screen.getByText('rename'))

    expect(screen.getByTestId('who')).toHaveTextContent('Renamed')
    expect(JSON.parse(localStorage.getItem('ats.session')!).user.name).toBe('Renamed')
  })

  test('GivenNoProvider_WhenUsingAuth_ThenItThrows', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)

    expect(() => render(<Probe />)).toThrow('useAuth must be used inside AuthProvider')
  })
})
