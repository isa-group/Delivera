import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { useOrderForm } from '@/composables/useOrderForm'

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key) => key }),
}))

const mockPush = vi.fn()
vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')

  return {
    ...actual,
    useRouter: () => ({
      push: mockPush,
    }),
    useRoute: () => ({
      params: {},
    }),
  }
})

const mockValidate = vi.fn().mockReturnValue(true)
vi.mock('@/composables/useValidation', () => ({
  useValidation: () => ({
    validate: mockValidate,
    required: () => null,
    email: () => null,
    errors: {},
    invalids: {},
  }),
}))

const mockGet = vi.fn()
const mockPost = vi.fn()
vi.mock('@/composables/useApi', () => ({
  useApi: () => ({
    get: mockGet,
    post: mockPost,
    translateError: (_data, fallback) => fallback,
  }),
}))



// ── Helpers ───────────────────────────────────────────────────────────────────

function makeOkResponse(data = []) {
  return { ok: true, json: async () => data }
}

import { createPinia, setActivePinia } from 'pinia'

beforeEach(() => {
  setActivePinia(createPinia())
})

let composable

function mountComposable() {
  const Wrapper = defineComponent({
    setup() {
      composable = useOrderForm()
      return () => null
    },
  })
  mount(Wrapper)
}

const mockDataPost = vi.fn()

vi.mock('@/composables/useServices', () => ({
  useServices: () => ({
    post: mockDataPost,
    get: vi.fn(),
  }),
}))

// ── Tests ─────────────────────────────────────────────────────────────────────



describe('useOrderForm — handleSubmit', () => {
  beforeEach(() => {
    mockGet.mockResolvedValue(makeOkResponse([]))
    mockValidate.mockReturnValue(true)
  })
  afterEach(() => {
    vi.clearAllMocks()
    mockPush.mockClear()
  })

  it('navigates to /orders with reference on success', async () => {
    mockDataPost.mockResolvedValue({
      ok: true,
      json: async () => ({ reference: 'DEL-001' }),
    })
  
    mountComposable()
    await flushPromises()
  
    composable.originId.value = 'unit-1'
    composable.orderType.value = 'INTERNAL'
    composable.destinationId.value = 'unit-2'
  
    await composable.handleSubmit()
    await flushPromises()
  
    expect(mockDataPost).toHaveBeenCalled()
  
    expect(mockPush).toHaveBeenCalledWith({
      path: '/orders',
      query: { created: 'DEL-001' },
    })
  })
})
