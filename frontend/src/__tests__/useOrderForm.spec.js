import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { useOrderForm } from '@/composables/useOrderForm'

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key) => key }),
}))

const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

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

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('useOrderForm — computed: b2bOrganizations', () => {
  afterEach(() => vi.clearAllMocks())

  it('deduplicates external units by orgId', async () => {
    const external = [
      { id: '1', orgId: 'o1', orgName: 'OrgA', companyId: 'c1', companyName: 'CompA' },
      { id: '2', orgId: 'o1', orgName: 'OrgA', companyId: 'c1', companyName: 'CompA' },
      { id: '3', orgId: 'o2', orgName: 'OrgB', companyId: 'c2', companyName: 'CompB' },
    ]
    mockGet
      .mockResolvedValueOnce(makeOkResponse([]))
      .mockResolvedValueOnce(makeOkResponse(external))
      .mockResolvedValueOnce(makeOkResponse([]))
    mountComposable()
    await flushPromises()
    expect(composable.b2bOrganizations.value).toHaveLength(2)
    expect(composable.b2bOrganizations.value[0]).toEqual({ id: 'o1', name: 'OrgA' })
  })
})

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
    mockPost.mockResolvedValue({ ok: true, json: async () => ({ reference: 'DEL-001' }) })
    mountComposable()
    await flushPromises()
    composable.originId.value = 'unit-1'
    composable.orderType.value = 'INTERNAL'
    composable.destinationId.value = 'unit-2'
    await composable.handleSubmit()
    await flushPromises()
    expect(mockPush).toHaveBeenCalledWith({ path: '/orders', query: { created: 'DEL-001' } })
  })
})
