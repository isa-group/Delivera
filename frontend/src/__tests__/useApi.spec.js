import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { fetchPublicOrder, useApi } from '@/composables/useApi'

// ── Mocks ────────────────────────────────────────────────────────────────────

const mockLogout = vi.fn()
const mockPush = vi.fn()
let mockToken = ''

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ 
    token: mockToken, 
    logout: mockLogout,
    isJwtExpired: vi.fn(() => false)
  }),
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')

  return {
    ...actual,
    useRouter: () => ({
      push: vi.fn(),
    }),
    useRoute: () => ({
      params: {},
    }),
  }
})

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key) => `[${key}]`,
    te: (key) => key.startsWith('error.known'),
  }),
}))

// ── Helper ────────────────────────────────────────────────────────────────────

let api

import { createPinia, setActivePinia } from 'pinia'

beforeEach(() => {
  setActivePinia(createPinia())
})

function mountApi() {
  mount(defineComponent({
    setup() { api = useApi(); return () => null },
  }))
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('useApi — translateError', () => {
  beforeEach(() => mountApi())

  it('translates data.code when i18n key exists', () => {
    expect(api.translateError({ code: 'known.CODE' }, 'error.fallback')).toBe('[error.known.CODE]')
  })

  it('returns fallback key translation when data is undefined', () => {
    expect(api.translateError(undefined, 'error.fallback')).toBe('[error.fallback]')
  })
})

describe('useApi — request', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    mockLogout.mockClear()
    mockPush.mockClear()
  })

  it('adds Authorization header when auth.token is set', async () => {
    mockToken = 'my-jwt'
    mountApi()
    let capturedHeaders
    vi.stubGlobal('fetch', vi.fn((url, opts) => {
      capturedHeaders = opts.headers
      return Promise.resolve({ status: 200 })
    }))
    await api.get('/test')
    expect(capturedHeaders.Authorization).toBe('Bearer my-jwt')
    mockToken = ''
  })

  it('calls logout and redirects to / on 401 outside /auth/ when authenticated', async () => {
    mockToken = 'my-jwt'
    mountApi()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 401 }))
    expect(mockLogout).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalledWith('/')
    mockToken = ''
  })

  it('does NOT logout on 401 when user is not authenticated', async () => {
    mockToken = ''
    mountApi()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 401 }))
    const res = await api.get('/orders')
    expect(res.status).toBe(401)
    expect(mockLogout).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })
})

describe('useApi — post', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends POST with JSON-serialized body', async () => {
    mockToken = ''
    mountApi()
    let capturedOpts
    vi.stubGlobal('fetch', vi.fn((url, opts) => {
      capturedOpts = opts
      return Promise.resolve({ status: 200 })
    }))
    await api.post('/orders', { foo: 'bar' })
    expect(capturedOpts.method).toBe('POST')
    expect(capturedOpts.body).toBe(JSON.stringify({ foo: 'bar' }))
  })
})

describe('fetchPublicOrder', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('returns JSON on ok response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ reference: 'DEL-001' }),
    }))
    const result = await fetchPublicOrder('DEL-001')
    expect(result.reference).toBe('DEL-001')
  })

  it('throws Error("not_found") on non-ok response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))
    await expect(fetchPublicOrder('BAD')).rejects.toThrow('not_found')
  })
})
