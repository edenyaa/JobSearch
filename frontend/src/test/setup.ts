import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { configureClient } from '../api/client'

afterEach(() => {
  cleanup()
  localStorage.clear()
  configureClient(null, null)
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})
