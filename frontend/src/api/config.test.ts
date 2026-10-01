import { describe, expect, test } from 'vitest'
import { DEFAULT_API_URL, apiUrl, resolveBase } from './config'

describe('api config', () => {
  test('GivenNoConfiguredUrl_WhenResolving_ThenTheRenderBackendIsUsed', () => {
    expect(resolveBase(undefined)).toBe('https://jobsearch-z7b7.onrender.com')
    expect(DEFAULT_API_URL).toBe('https://jobsearch-z7b7.onrender.com')
  })

  test('GivenConfiguredUrlWithTrailingSlash_WhenResolving_ThenSlashIsRemoved', () => {
    expect(resolveBase('http://localhost:8080/')).toBe('http://localhost:8080')
    expect(resolveBase('  https://x.example.com//  ')).toBe('https://x.example.com')
  })

  test('GivenEmptyConfiguredUrl_WhenResolving_ThenRelativeCallsAreUsed', () => {
    expect(resolveBase('')).toBe('')
    expect(apiUrl('/users/1/jobs', '')).toBe('/api/users/1/jobs')
  })

  test('GivenBaseUrl_WhenBuildingUrl_ThenEveryCallGoesToThatServer', () => {
    expect(apiUrl('/users/login', resolveBase(undefined))).toBe('https://jobsearch-z7b7.onrender.com/api/users/login')
    expect(apiUrl('/users/1/jobs?status=OFFER', 'http://localhost:8080')).toBe('http://localhost:8080/api/users/1/jobs?status=OFFER')
  })
})
