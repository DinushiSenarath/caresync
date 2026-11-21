const BASE: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string> | null
  data: unknown

  constructor(
    status: number,
    message: string,
    fieldErrors: Record<string, string> | null = null,
    data: unknown = null,
  ) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
    this.data = data
  }
}

let token: string | null = sessionStorage.getItem('caresync_token')
let onUnauthorized: (() => void) | null = null

export function setToken(value: string | null) {
  token = value
  if (value) sessionStorage.setItem('caresync_token', value)
  else sessionStorage.removeItem('caresync_token')
}

export function getToken() {
  return token
}

export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn
}

type Query = Record<string, string | number | boolean | null | undefined>

interface Options {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  query?: Query
}

function defaultMessage(status: number): string {
  switch (status) {
    case 401: return 'Please sign in again.'
    case 403: return 'You do not have permission to do this.'
    case 404: return 'Not found.'
    case 429: return 'Too many requests. Please try again later.'
    default: return 'Something went wrong. Please try again.'
  }
}

export async function api<T = unknown>(path: string, options: Options = {}): Promise<T> {
  const url = new URL(path, BASE)
  if (options.query) {
    for (const [key, value] of Object.entries(options.query)) {
      if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
    }
  }

  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers['Authorization'] = `Bearer ${token}`

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 90000)
  let res: Response
  try {
    res = await fetch(url, {
      method: options.method ?? 'GET',
      headers,
      signal: controller.signal,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  } catch {
    throw new ApiError(
      0,
      'Cannot reach the server. If it has been idle it may need about a minute to wake up. Please try again.',
    )
  } finally {
    clearTimeout(timer)
  }

  if (res.status === 204) return undefined as T

  let data: unknown = null
  const text = await res.text()
  if (text) {
    try { data = JSON.parse(text) } catch { data = null }
  }

  if (!res.ok) {
    const d = data as { message?: string; fieldErrors?: Record<string, string> | null } | null
    if (res.status === 401 && token && onUnauthorized) onUnauthorized()
    throw new ApiError(res.status, d?.message ?? defaultMessage(res.status), d?.fieldErrors ?? null, data)
  }
  return data as T
}