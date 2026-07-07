import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key) => key }),
}))

const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('@/composables/useValidation', () => ({
  useValidation: () => ({
    validate: vi.fn().mockReturnValue(true),
    required: () => null,
    errors: {},
    invalids: {},
  }),
}))

const mockPost = vi.fn()
const mockPut = vi.fn()
vi.mock('@/composables/useApi', () => ({
  useApi: () => ({
    post: mockPost,
    put: mockPut,
    translateError: (_d, fallback) => fallback,
  }),
}))

const { useUnitForm } = await import('@/composables/useUnitForm')

beforeEach(() => {
  mockPost.mockReset()
  mockPut.mockReset()
  mockPush.mockReset()
})

describe('useUnitForm loadFromUnit', () => {
  it('mapea los campos del backend al formulario', () => {
    const f = useUnitForm()
    f.loadFromUnit({ name: 'X', type: 'WAREHOUSE', address: 'a', latitude: 1, longitude: 2, defaultPriority: 'HIGH' })
    expect(f.name.value).toBe('X')
    expect(f.unitType.value).toBe('WAREHOUSE')
    expect(f.address.value).toBe('a')
    expect(f.latitude.value).toBe(1)
    expect(f.longitude.value).toBe(2)
    expect(f.defaultPriority.value).toBe('HIGH')
  })
  it('usa defaults seguros con campos ausentes o nulos', () => {
    const f = useUnitForm()
    f.loadFromUnit({})
    expect(f.name.value).toBe('')
    expect(f.unitType.value).toBeNull()
    expect(f.defaultPriority.value).toBeNull()
  })
  it('no falla si recibe null', () => {
    const f = useUnitForm()
    expect(() => f.loadFromUnit(null)).not.toThrow()
  })
})

describe('useUnitForm submitUnit', () => {
  it('envía defaultPriority cuando se indica', async () => {
    mockPost.mockResolvedValue({ ok: true })
    const f = useUnitForm()
    f.name.value = 'U1'
    f.unitType.value = 'WAREHOUSE'
    f.address.value = 'calle'
    f.defaultPriority.value = 'HIGH'

    await f.submitUnit({ isEdit: false })

    expect(mockPost).toHaveBeenCalledWith('/units', expect.objectContaining({
      name: 'U1', defaultPriority: 'HIGH',
    }))
  })

  it('envía defaultPriority null cuando se deja vacío', async () => {
    mockPut.mockResolvedValue({ ok: true })
    const f = useUnitForm()
    f.name.value = 'U2'
    f.unitType.value = 'STORE'
    f.address.value = 'calle'
    f.defaultPriority.value = null

    await f.submitUnit({ isEdit: true, unitId: 'abc' })

    expect(mockPut).toHaveBeenCalledWith('/units/abc', expect.objectContaining({
      defaultPriority: null,
    }))
  })
})
