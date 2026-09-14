import type { Domain, DomainCognition, Draft, EvolutionPage, Plan } from './types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message) }
}

export type AuthState = { authenticated: boolean, userId: number | null, username: string | null }

let csrfToken: string | null = null
let csrfPending: Promise<string> | null = null
let unauthorizedHandler: (() => void) | null = null

async function ensureCsrf(): Promise<string> {
  if (csrfToken) return csrfToken
  csrfPending ??= fetch(`${BASE_URL}/auth/csrf`, { credentials: 'include' })
    .then(async response => {
      if (!response.ok) throw new ApiError('无法获取登录凭证，请重试。', response.status)
      const body = await response.json() as { token: string }
      csrfToken = body.token
      return body.token
    })
    .catch(reason => { throw reason instanceof ApiError ? reason : new ApiError('无法获取登录凭证，请确认后端已启动。', 0) })
    .finally(() => { csrfPending = null })
  return csrfPending
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response
  try {
    const method = (options?.method ?? 'GET').toUpperCase()
    const headers = new Headers(options?.headers)
    if (method !== 'GET' && method !== 'HEAD') headers.set('X-CSRF-TOKEN', await ensureCsrf())
    response = await fetch(`${BASE_URL}${path}`, { ...options, headers, credentials: 'include' })
  } catch (reason) {
    if (reason instanceof ApiError) throw reason
    throw new ApiError('无法连接后端，请确认服务已启动。', 0)
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null
    if (response.status === 401 && path !== '/auth/login') { csrfToken = null; unauthorizedHandler?.() }
    throw new ApiError(body?.message || `请求失败（${response.status}）`, response.status)
  }
  return response.json() as Promise<T>
}

const json = (body: unknown): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const api = {
  setUnauthorizedHandler: (handler: (() => void) | null) => { unauthorizedHandler = handler },
  me: () => request<AuthState>('/auth/me'),
  csrf: ensureCsrf,
  login: async (username: string, password: string) => {
    const result = await request<AuthState>('/auth/login', json({ username, password }))
    csrfToken = null
    return result
  },
  logout: async () => {
    const result = await request<AuthState>('/auth/logout', { method: 'POST' })
    csrfToken = null
    return result
  },
  domains: () => request<Domain[]>('/domains'),
  domainCognition: (domainId: number) => request<DomainCognition>(`/domains/${domainId}/cognition`),
  evolutions: (page: number, domainId?: number) => request<EvolutionPage>(`/evolutions?page=${page}&size=20&order=${domainId ? 'asc' : 'desc'}${domainId ? `&domainId=${domainId}` : ''}`),
  importText: (content: string) => request<{ conversationId: number }>('/conversation-imports/text', json({ content })),
  importPdf: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<{ conversationId: number }>('/conversation-imports/pdf', { method: 'POST', body: form })
  },
  draft: (conversationId: number) => request<Draft>('/analysis/draft', json({ conversationId })),
  createPlan: (draft: Draft, planRequestId: string) => request<Plan>('/analysis/draft/plans', json({ planRequestId, draft })),
  getPlan: (planId: number | string) => request<Plan>(`/analysis/draft/plans/${encodeURIComponent(planId)}`),
  confirmPlan: (planId: number | string) => request<unknown>(`/analysis/draft/plans/${encodeURIComponent(planId)}/confirm`, { method: 'POST' }),
}
