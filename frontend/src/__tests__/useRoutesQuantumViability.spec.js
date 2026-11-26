import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

// vi.mock se eleva al principio del fichero: post tiene que existir antes.
const { post } = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('@/composables/useLoad', () => ({ useLoad: () => ({ post }) }))
vi.mock('@/composables/useServices', () => ({ useServices: () => ({}) }))

import { useRoutesQuantumViability } from '@/composables/useRoutesQuantumViability'

const groups = ref({
  depots: [
    { id: 'D1', matrixIndex: 0 },
    { id: 'D2', matrixIndex: 1 },
  ],
  clusters: [
    { id: 0, customers: [{ id: 'C1' }, { id: 'C2' }], depots: [0] },
    { id: 1, customers: [{ id: 'C3' }], depots: [1] },
  ],
})

// Simula useLoad.post: llama a la transformación con la respuesta de cada grupo.
function answerWith(responses) {
  post.mockImplementation(async (api, url, body, target, error, loaded, transform) => {
    const response = responses[body.customers[0]]
    if (response instanceof Error) {
      error.value = response.message
      return
    }
    target.value = transform(response)
  })
}

function setup({ benchmark = false } = {}) {
  return useRoutesQuantumViability({ groups }, { isBenchmark: () => benchmark, selectedBenchmark: ref('p01') })
}

describe('useRoutesQuantumViability', () => {
  // Con llaves: si beforeEach devuelve una función, Vitest la ejecuta como limpieza.
  beforeEach(() => {
    post.mockReset()
  })

  it('pide el análisis de cada grupo con sus clientes y sus depósitos', async () => {
    answerWith({ C1: { analysis: { is_viable: true } }, C3: { analysis: { is_viable: false } } })
    const { analyzeViability } = setup()

    await analyzeViability()

    expect(post).toHaveBeenCalledTimes(2)
    const bodies = post.mock.calls.map((call) => call[2])
    expect(bodies).toContainEqual({ customers: ['C1', 'C2'], depots: ['D1'] })
    expect(bodies).toContainEqual({ customers: ['C3'], depots: ['D2'] })
  })

  it('usa la ruta de empresa o la del benchmark según los datos', async () => {
    answerWith({ C1: { analysis: {} }, C3: { analysis: {} } })

    await setup().analyzeViability()
    expect(post.mock.calls[0][1]).toBe('/fms/routing/analyze/cluster?solverType=QISKIT')

    post.mockClear()
    await setup({ benchmark: true }).analyzeViability()
    expect(post.mock.calls[0][1]).toBe('/fms/routing/analyze/benchmarks/p01/cluster?solverType=QISKIT')
  })

  it('guarda el veredicto y las métricas de cada grupo y los resume', async () => {
    const features = { demand_granularity: 0.1, capacity_tightness: 0.5, spatial_compressibility_index: 0.3 }
    answerWith({ C1: { analysis: { is_viable: true, features } }, C3: { analysis: { is_viable: false } } })
    const { analyzeViability, viabilityByCluster, viabilitySummary, analyzingViability } = setup()

    await analyzeViability()

    expect(viabilityByCluster.value[0]).toEqual({ isViable: true, features })
    expect(viabilityByCluster.value[1]).toEqual({ isViable: false, features: {} })
    expect(viabilitySummary.value).toEqual({ analyzed: 2, viable: 1, noDepot: 0 })
    expect(analyzingViability.value).toBe(false)
  })

  it('si falla un grupo, conserva los demás y expone el error', async () => {
    answerWith({ C1: { analysis: { is_viable: true } }, C3: new Error('Motor no disponible') })
    const { analyzeViability, viabilityByCluster, viabilityError } = setup()

    await analyzeViability()

    expect(Object.keys(viabilityByCluster.value)).toEqual(['0'])
    expect(viabilityError.value).toBe('Motor no disponible')
  })

  it('no analiza los grupos sin unidad asignada y los cuenta aparte', async () => {
    const withLonelyGroup = ref({
      ...groups.value,
      clusters: [...groups.value.clusters, { id: 2, customers: [{ id: 'C9' }], depots: [] }],
    })
    answerWith({ C1: { analysis: { is_viable: true } }, C3: { analysis: { is_viable: false } } })
    const viability = useRoutesQuantumViability({ groups: withLonelyGroup }, { isBenchmark: () => false, selectedBenchmark: ref('') })

    await viability.analyzeViability()

    expect(post).toHaveBeenCalledTimes(2)
    expect(viability.viabilityByCluster.value[2]).toEqual({ isViable: false, features: {}, noDepot: true })
    expect(viability.viabilitySummary.value).toEqual({ analyzed: 2, viable: 1, noDepot: 1 })
  })

  it('da una fila por grupo analizado con su veredicto y los indicadores que fallan', async () => {
    const withLonelyGroup = ref({
      ...groups.value,
      clusters: [
        { ...groups.value.clusters[0], metadata: { customerCount: 2, totalDemand: 30 } },
        groups.value.clusters[1],
        { id: 2, customers: [{ id: 'C9' }], depots: [] },
      ],
    })
    answerWith({
      C1: { analysis: { is_viable: true, features: { demand_granularity: 0.2, capacity_tightness: 0.9, spatial_compressibility_index: 1.1 } } },
      C3: { analysis: { is_viable: false, features: { demand_granularity: 0.6, capacity_tightness: 0.95 } } },
    })
    const viability = useRoutesQuantumViability({ groups: withLonelyGroup }, { isBenchmark: () => false, selectedBenchmark: ref('') })

    await viability.analyzeViability()
    const [viable, notViable, noDepot] = viability.viabilityRows.value

    expect(viable).toMatchObject({ id: 0, customerCount: 2, totalDemand: 30, status: 'viable' })
    expect(viable.indicators.map(i => i.failing)).toEqual([false, false, false])

    expect(notViable).toMatchObject({ id: 1, customerCount: 1, status: 'notViable' })
    expect(notViable.indicators).toEqual([
      { key: 'demand_granularity', label: 'G', value: 0.6, limit: '< 0.6', failing: true },
      { key: 'capacity_tightness', label: 'Tc', value: 0.95, limit: '≤ 0.9', failing: true },
      { key: 'spatial_compressibility_index', label: 'S', value: null, limit: '≤ 1.2', failing: false },
    ])

    expect(noDepot).toMatchObject({ id: 2, status: 'noDepot' })
    expect(noDepot.indicators.every(i => i.value === null && !i.failing)).toBe(true)
  })

  it('no da filas hasta que se analiza', () => {
    expect(setup().viabilityRows.value).toEqual([])
  })
})
