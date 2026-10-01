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
  400: 'The request was not valid.',
  401: 'Please sign in again.',
  403: 'You are not allowed to do that.',
  404: 'Not found.',
  409: 'That already exists.',
  502: 'The AI service is unavailable right now. Try again in a minute.',
}

async function toError(response: Response): Promise<ApiError> {
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
    message = FALLBACK_MESSAGES[response.status] ?? `Something went wrong (${response.status}).`
  }
  return new ApiError(response.status, message)
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers)
  if (currentToken) {
    headers.set('Authorization', `Bearer ${currentToken}`)
  }
  let response: Response
  try {
    response = await fetch(`/api${path}`, { ...init, headers })
  } catch {
    throw new ApiError(0, 'Could not reach the server. Is the backend running?')
  }
  if (!response.ok) {
    if (response.status === 401 && currentToken && unauthorizedHandler) {
      unauthorizedHandler()
    }
    throw await toError(response)
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
