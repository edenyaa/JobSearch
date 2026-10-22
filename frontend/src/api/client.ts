import { API_BASE_URL, apiUrl } from './config'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

let currentToken: string | null = null
let unauthorizedHandler: (() => void) | null = null

export function configureClient(token: string | null, onUnauthorized: (() => void) | null) {
  currentToken = token
  unauthorizedHandler = onUnauthorized
}

const FALLBACK_MESSAGES: Record<number, string> = {
  400: 'Some of the data sent was not valid. Check the fields and try again.',
  401: 'Your session is no longer valid. Please sign in again.',
  403: 'You do not have permission to do that.',
  404: 'That item was not found. It may have been deleted.',
  409: 'That already exists.',
  413: 'The file is too large. The limit is 10 MB.',
  500: 'The server hit an unexpected error and gave no details. The backend may not be running or may still be starting. Check the backend console for the error.',
  502: 'The AI service is unavailable right now. Try again in a minute.',
  503: 'The server is temporarily unavailable. Try again in a moment.',
  504: 'The server took too long to answer. Try again in a moment.',
}

const NOUNS: Record<string, string> = {
  jobs: 'jobs',
  skills: 'skills',
  courses: 'courses',
  cv: 'your CV',
  resume: 'the resume file',
  tailor: 'the tailored CV',
  analysis: 'the analysis',
  match: 'the keyword match',
  status: 'the job status',
  name: 'your name',
  email: 'your email',
  password: 'your password',
  logout: 'your session',
  text: 'your CV text',
}

export function describeAction(method: string, path: string): string {
  const segments = path.split('?')[0].split('/').filter(Boolean)
  if (segments[0] === 'users' && segments[1] === 'login') return 'Could not sign in'
  if (segments[0] === 'users' && segments.length === 1 && method === 'POST') return 'Could not create the account'
  const words = segments.filter((segment) => !/^\d+$/.test(segment) && segment !== 'users')
  const last = words[words.length - 1]
  const noun = last ? NOUNS[last] : undefined
  if (last === 'tailor') return 'Could not tailor your CV'
  if (last === 'logout') return 'Could not sign out'
  if (!noun) return method === 'GET' ? 'Could not load the data' : 'Could not complete the request'
  if (method === 'GET') return `Could not load ${noun}`
  if (method === 'DELETE') return `Could not delete ${noun}`
  return `Could not save ${noun}`
}

async function toError(response: Response, action: string): Promise<ApiError> {
  let message = ''
  try {
    const data = await response.json()
    if (data && typeof data.message === 'string') {
      message = data.message
    }
  } catch {
    message = ''
  }
  if (!message) {
    message = FALLBACK_MESSAGES[response.status] ?? `The server returned an unexpected response (${response.status}).`
  }
  const text = response.status === 401 ? message : `${action}: ${message}`
  return new ApiError(response.status, text)
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const action = describeAction(init.method ?? 'GET', path)
  const headers = new Headers(init.headers)
  if (currentToken) {
    headers.set('Authorization', `Bearer ${currentToken}`)
  }
  let response: Response
  try {
    response = await fetch(apiUrl(path), { ...init, headers })
  } catch {
    throw new ApiError(0, `${action}: the server did not respond. Either the backend at ${API_BASE_URL || 'this site'} is down or starting up, or it does not allow this website (the backend's CORS_ALLOWED_ORIGINS must include ${window.location.origin}).`)
  }
  if (!response.ok) {
    if (response.status === 401 && currentToken && unauthorizedHandler) {
      unauthorizedHandler()
    }
    throw await toError(response, action)
  }
  return response
}

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const init: RequestInit = { method }
  if (body !== undefined) {
    init.body = JSON.stringify(body)
    init.headers = { 'Content-Type': 'application/json' }
  }
  const response = await send(path, init)
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export async function upload<T>(path: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  const response = await send(path, { method: 'PUT', body: form })
  return (await response.json()) as T
}

export interface Download {
  blob: Blob
  fileName: string
}

export async function download(path: string, fallbackName: string): Promise<Download> {
  const response = await send(path, { method: 'GET' })
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const match = /filename="?([^";]+)"?/.exec(disposition)
  return { blob: await response.blob(), fileName: match ? match[1] : fallbackName }
}

export function saveBlob(file: Download) {
  const url = URL.createObjectURL(file.blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong.'
}
