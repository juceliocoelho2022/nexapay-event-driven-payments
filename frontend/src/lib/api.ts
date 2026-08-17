const TOKEN_KEY = 'nexapay.access-token'
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status: number
  readonly details: unknown

  constructor(message: string, status: number, details?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

export function getAccessToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function setAccessToken(token: string) {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearAccessToken() {
  sessionStorage.removeItem(TOKEN_KEY)
}

type RequestOptions = {
  authenticated: boolean
}

async function request<T>(
  path: string,
  init: RequestInit,
  options: RequestOptions,
): Promise<T> {
  const headers = new Headers(init.headers)

  if (options.authenticated) {
    const token = getAccessToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
  } else {
    headers.delete('Authorization')
  }

  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
  })

  const contentType = response.headers.get('content-type') ?? ''
  let payload: unknown = null

  if (response.status !== 204) {
    payload = contentType.includes('application/json')
      ? await response.json()
      : await response.text()
  }

  if (!response.ok) {
    let message = `Falha HTTP ${response.status}`
    if (typeof payload === 'string' && payload.trim()) {
      message = payload
    } else if (payload && typeof payload === 'object') {
      const candidate = payload as Record<string, unknown>
      message = String(candidate.message ?? candidate.error ?? candidate.detail ?? message)
    }
    throw new ApiError(message, response.status, payload)
  }

  return payload as T
}

export function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  return request<T>(path, init, { authenticated: true })
}

export function publicApiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  return request<T>(path, init, { authenticated: false })
}
