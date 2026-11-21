import { describe, it, expect, vi } from 'vitest'
import { readFileSync } from 'node:fs'
// La URL de jsdom no entiende rutas de fichero: se usa la de Node.
import { fileURLToPath, URL as NodeURL } from 'node:url'
import { parse } from 'yaml'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key) => key }),
}))
vi.mock('@/composables/useLoad', () => ({
  useLoad: () => ({ post: vi.fn(), executeLoad: vi.fn(() => Promise.resolve()) }),
}))
vi.mock('@/composables/useServices', () => ({
  useServices: () => ({}),
}))
// El mapa arrastra Leaflet; aquí solo interesa la lista de solvers.
vi.mock('@/composables/useDeliveraMap', () => ({
  addlayer: vi.fn(),
  initSolverLayer: vi.fn(),
}))

import { useRoutesSolver } from '@/composables/useRoutesSolver'

const QUANTUM_SOLVERS = ['QISKIT', 'DWAVE']

function setup({ comparison = true } = {}) {
  const phases = {
    disabledNextPhases: vi.fn(),
    goToPhase: vi.fn(),
    allowNextPhase: vi.fn(),
    getCurrentPhaseName: () => 'selectSolvers',
  }
  const modes = { isComparisonMode: () => comparison, isCustomMode: () => !comparison }
  const groups = { groupsRows: { value: [] }, getSlotInstance: vi.fn(), countSlots: () => 0 }
  return { phases, ...useRoutesSolver(phases, modes, groups) }
}

function loadLocale(name) {
  const path = fileURLToPath(new NodeURL(`../locales/${name}.yml`, import.meta.url))
  return parse(readFileSync(path, 'utf-8')).routes
}

describe('useRoutesSolver — solvers cuánticos', () => {
  // allSolvers es el catálogo completo; solvers, solo los que el usuario tiene contratados.
  it('ofrece QISKIT, DWAVE y HGS junto a los clásicos', () => {
    const { allSolvers, getSolverNames } = setup()

    expect(allSolvers.map((solver) => solver.type)).toEqual(['GREEDY', 'GENETIC', 'ANNEALING', 'QISKIT', 'DWAVE', 'HGS'])
    expect(getSolverNames()).toEqual(expect.arrayContaining(QUANTUM_SOLVERS))
  })

  it('cada solver cuántico tiene id único, icono y textos propios', () => {
    const { allSolvers } = setup()
    const ids = allSolvers.map((solver) => solver.id)

    expect(new Set(ids).size).toBe(ids.length)
    for (const type of QUANTUM_SOLVERS) {
      const solver = allSolvers.find((item) => item.type === type)
      expect(solver.icon).toMatch(/^pi pi-/)
      expect(solver.name).toBe(`routes.solvers.${type}.name`)
      expect(solver.description).toBe(`routes.solvers.${type}.description`)
    }
  })

  it('en modo comparación se pueden marcar los dos a la vez', () => {
    const { allSolvers, selectSolver, selectedSolversId, phases } = setup({ comparison: true })
    const idOf = (type) => allSolvers.find((solver) => solver.type === type).id

    selectSolver(idOf('QISKIT'))
    selectSolver(idOf('DWAVE'))

    expect([...selectedSolversId.value]).toEqual([idOf('QISKIT'), idOf('DWAVE')])
    expect(phases.allowNextPhase).toHaveBeenCalledTimes(2)
  })

  it.each(['es', 'en'])('todos los solvers tienen nombre, descripción y capa en %s', (language) => {
    const routes = loadLocale(language)
    const { getSolverNames } = setup()

    for (const type of getSolverNames()) {
      expect(routes.solvers[type]?.name, `${language}: routes.solvers.${type}.name`).toBeTruthy()
      expect(routes.solvers[type]?.description, `${language}: routes.solvers.${type}.description`).toBeTruthy()
      expect(routes.layers[type], `${language}: routes.layers.${type}`).toBeTruthy()
    }
  })
})
