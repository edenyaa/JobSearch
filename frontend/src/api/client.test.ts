import { describe, expect, test, vi } from 'vitest'
import { mockApi, stubDownloads } from '../test/helpers'
import { ApiError, configureClient, describeAction, download, messageOf, request, saveBlob, upload } from './client'

async function failureOf(promise: Promise<unknown>): Promise<ApiError> {
  return (await promise.catch((e) => e)) as ApiError
}

describe('request', () => {
  test('GivenToken_WhenRequesting_ThenAuthorizationHeaderIsSent', async () => {
    const api = mockApi({ 'GET /api/things': { body: [] } })
    configureClient('abc123', null)

    await request('GET', '/things')

    expect(api.calls[0].headers.get('Authorization')).toBe('Bearer abc123')
  })

  test('GivenNoToken_WhenRequesting_ThenNoAuthorizationHeaderIsSent', async () => {
    const api = mockApi({ 'GET /api/things': { body: [] } })

    await request('GET', '/things')

    expect(api.calls[0].headers.has('Authorization')).toBe(false)
  })

  test('GivenBody_WhenRequesting_ThenItIsSentAsJson', async () => {
    const api = mockApi({ 'POST /api/things': { body: { id: 1 } } })

    const result = await request<{ id: number }>('POST', '/things', { name: 'x' })

    expect(result).toEqual({ id: 1 })
    expect(api.calls[0].body).toEqual({ name: 'x' })
    expect(api.calls[0].headers.get('Content-Type')).toBe('application/json')
  })

  test('GivenNoContentResponse_WhenRequesting_ThenUndefinedIsReturned', async () => {
    mockApi({ 'DELETE /api/things/1': { status: 204 } })

    const result = await request('DELETE', '/things/1')

    expect(result).toBeUndefined()
  })

  test('GivenErrorWithMessage_WhenRequesting_ThenApiErrorCarriesServerMessage', async () => {
    mockApi({ 'GET /api/things': { status: 409, body: { message: 'Email already registered' } } })

    const failure = await failureOf(request('GET', '/things'))

    expect(failure).toBeInstanceOf(ApiError)
    expect(failure.status).toBe(409)
    expect(failure.message).toContain('Email already registered')
  })

  test('GivenErrorWithoutMessage_WhenRequesting_ThenFallbackMessageForStatusIsUsed', async () => {
    mockApi({
      'GET /api/a': { status: 404, body: {} },
      'GET /api/b': { status: 502, body: {} },
      'GET /api/c': { status: 403, body: {} },
    })

    const notFound = await failureOf(request('GET', '/a'))
    const badGateway = await failureOf(request('GET', '/b'))
    const forbidden = await failureOf(request('GET', '/c'))

    expect(notFound.message).toBe('Could not load the data: That item was not found. It may have been deleted.')
    expect(badGateway.message).toContain('AI service')
    expect(forbidden.message).toBe('Could not load the data: You do not have permission to do that.')
  })

  test('GivenUnknownErrorStatus_WhenRequesting_ThenGenericMessageIncludesStatus', async () => {
    mockApi({ 'GET /api/a': { status: 418, body: {} } })

    const failure = await failureOf(request('GET', '/a'))

    expect(failure.message).toBe('Could not load the data: The server returned an unexpected response (418).')
  })

  test('GivenNonJsonErrorBody_WhenRequesting_ThenFallbackMessageIsUsed', async () => {
    mockApi({ 'GET /api/a': { status: 400, text: '<html>oops</html>' } })

    const failure = await failureOf(request('GET', '/a'))

    expect(failure.message).toContain('Some of the data sent was not valid')
  })

  test('GivenNetworkFailure_WhenRequesting_ThenApiErrorWithStatusZeroAndHelpfulMessage', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const failure = await failureOf(request('GET', '/a'))

    expect(failure.status).toBe(0)
    expect(failure.message).toContain('the server did not respond')
    expect(failure.message).toContain('CORS_ALLOWED_ORIGINS must include http://localhost:3000')
  })

  test('Given401WithToken_WhenRequesting_ThenUnauthorizedHandlerIsCalled', async () => {
    mockApi({ 'GET /api/a': { status: 401, body: {} } })
    const handler = vi.fn()
    configureClient('abc', handler)

    await request('GET', '/a').catch(() => undefined)

    expect(handler).toHaveBeenCalledOnce()
  })

  test('Given401WithoutToken_WhenRequesting_ThenUnauthorizedHandlerIsNotCalled', async () => {
    mockApi({ 'POST /api/users/login': { status: 401, body: { message: 'Invalid email or password.' } } })
    const handler = vi.fn()
    configureClient(null, handler)

    const failure = await failureOf(request('POST', '/users/login', {}))

    expect(handler).not.toHaveBeenCalled()
    expect(failure.message).toBe('Invalid email or password.')
  })
})

describe('upload', () => {
  test('GivenFile_WhenUploading_ThenItIsSentAsMultipartPut', async () => {
    const api = mockApi({ 'PUT /api/users/1/cv': { body: { id: 1 } } })
    const file = new File(['hello'], 'cv.txt', { type: 'text/plain' })

    await upload('/users/1/cv', file)

    const sent = api.calls[0].body as FormData
    expect(sent.get('file')).toBeInstanceOf(File)
    expect((sent.get('file') as File).name).toBe('cv.txt')
    expect(api.calls[0].headers.has('Content-Type')).toBe(false)
  })
})

describe('download', () => {
  test('GivenContentDisposition_WhenDownloading_ThenFileNameIsTakenFromIt', async () => {
    mockApi({
      'GET /api/users/1/cv': { text: 'bytes', headers: { 'Content-Disposition': 'attachment; filename="my cv.pdf"' } },
    })

    const file = await download('/users/1/cv', 'fallback')

    expect(file.fileName).toBe('my cv.pdf')
    expect(await file.blob.text()).toBe('bytes')
  })

  test('GivenNoContentDisposition_WhenDownloading_ThenFallbackNameIsUsed', async () => {
    mockApi({ 'GET /api/users/1/cv': { text: 'bytes' } })

    const file = await download('/users/1/cv', 'fallback')

    expect(file.fileName).toBe('fallback')
  })

  test('GivenFile_WhenSaving_ThenAnchorWithDownloadNameIsClickedAndUrlRevoked', () => {
    const stubs = stubDownloads()

    saveBlob({ blob: new Blob(['x']), fileName: 'cv.docx' })

    expect(stubs.createObjectURL).toHaveBeenCalledOnce()
    expect(stubs.click).toHaveBeenCalledOnce()
    expect(stubs.revokeObjectURL).toHaveBeenCalledWith('blob:test')
  })
})

describe('messageOf', () => {
  test('GivenError_WhenGettingMessage_ThenItsMessageIsReturned', () => {
    expect(messageOf(new Error('boom'))).toBe('boom')
  })

  test('GivenNonError_WhenGettingMessage_ThenGenericMessageIsReturned', () => {
    expect(messageOf('nope')).toBe('Something went wrong.')
  })
})

describe('describeAction', () => {
  test('GivenMethodAndPath_WhenDescribing_ThenItNamesWhatFailed', () => {
    expect(describeAction('GET', '/users/1/jobs')).toBe('Could not load jobs')
    expect(describeAction('POST', '/users/1/jobs')).toBe('Could not save jobs')
    expect(describeAction('DELETE', '/users/1/skills/4')).toBe('Could not delete skills')
    expect(describeAction('POST', '/users/1/jobs/5/tailor')).toBe('Could not tailor your CV')
    expect(describeAction('GET', '/users/1/jobs/5/analysis')).toBe('Could not load the analysis')
    expect(describeAction('PUT', '/users/1/cv')).toBe('Could not save your CV')
    expect(describeAction('POST', '/users/login')).toBe('Could not sign in')
    expect(describeAction('POST', '/users')).toBe('Could not create the account')
    expect(describeAction('POST', '/users/1/logout')).toBe('Could not sign out')
    expect(describeAction('GET', '/unknown')).toBe('Could not load the data')
    expect(describeAction('PUT', '/unknown')).toBe('Could not complete the request')
  })

  test('GivenServerErrorWithoutBody_WhenRequesting_ThenMessageExplainsTheBackendMayBeDown', async () => {
    mockApi({ 'GET /api/users/1/jobs': { status: 500, body: {} } })

    const failure = await failureOf(request('GET', '/users/1/jobs'))

    expect(failure.message).toContain('Could not load jobs: The server hit an unexpected error')
    expect(failure.message).toContain('backend may not be running')
  })

  test('GivenTooLargeFile_WhenUploading_ThenMessageStatesTheLimit', async () => {
    mockApi({ 'PUT /api/users/1/cv': { status: 413, body: {} } })

    const failure = await failureOf(request('PUT', '/users/1/cv'))

    expect(failure.message).toBe('Could not save your CV: The file is too large. The limit is 10 MB.')
  })

  test('GivenUnauthorizedResponse_WhenRequesting_ThenMessageIsNotPrefixed', async () => {
    mockApi({ 'GET /api/users/1/jobs': { status: 401, body: {} } })

    const failure = await failureOf(request('GET', '/users/1/jobs'))

    expect(failure.message).toBe('Your session is no longer valid. Please sign in again.')
  })
})
