export const DEFAULT_API_URL = 'https://jobsearch-z7b7.onrender.com'

export function resolveBase(configured: string | undefined): string {
  const base = configured === undefined ? DEFAULT_API_URL : configured
  return base.trim().replace(/\/+$/, '')
}

export function apiUrl(path: string, base: string = resolveBase(import.meta.env.VITE_API_URL)): string {
  return `${base}/api${path}`
}

export const API_BASE_URL = resolveBase(import.meta.env.VITE_API_URL)
