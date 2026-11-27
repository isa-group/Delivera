import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import MyOrderDetailView from '@/views/orders/MyOrderDetailView.vue'

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key) => key }),
}))

vi.mock('@/composables/useFormatDate', () => ({
  useFormatDate: () => ({ formatDateTime: (d) => String(d) }),
}))

vi.mock('@/composables/useAppConfig', () => ({
  useAppConfig: () => ({ load: vi.fn(), statusSeverity: {} }),
}))

vi.mock('@/composables/useApi', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useApi: () => ({
      get: vi.fn().mockResolvedValue({ ok: true, json: async () => [] }),
      post: vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) }),
    }),
  }
})

const mockPush = vi.fn()
let mockRouteQuery = { q: 'DEL-20240101-0001' }
vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')

  return {
    ...actual,
    useRouter: () => ({
      push: vi.fn(),
    }),
    useRoute: () => ({
      params: {},
      query: {
        q: 'DEL-20240101-0001'
      }
    }),
  }
})

import { createPinia, setActivePinia } from 'pinia'

beforeEach(() => {
  setActivePinia(createPinia())
})

// ── Helpers ───────────────────────────────────────────────────────────────────

const STUBS = {
  PButton: { template: '<button />' },
  PMessage: { template: '<div class="p-message"><slot /></div>' },
  PTag: { template: '<span />' },
  PTextarea: { template: '<textarea />' },
  TimelineList: { template: '<div />' },
}

function buildOrder(overrides = {}) {
  return {
    reference: 'DEL-20240101-0001',
    companyName: 'Acme',
    originName: 'Almacén Central',
    destinationName: null,
    recipientName: 'Juan García',
    recipientEmail: 'juan@test.com',
    status: 'PENDING',
    events: [],
    ...overrides,
  }
}

function mockFetch(response, ok = true) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok,
    json: async () => response,
  }))
}

function mountView() {
  return mount(MyOrderDetailView, { global: { stubs: STUBS } })
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('MyOrderDetailView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    mockRouteQuery = { q: 'DEL-20240101-0001' }
    mockPush.mockClear()
  })

  it('renders order reference on successful fetch', async () => {
    mockFetch(buildOrder())
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('DEL-20240101-0001')
  })

  it('shows error message when fetch returns non-ok', async () => {
    mockFetch({}, false)
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('.p-message').exists()).toBe(true)
  })

})
