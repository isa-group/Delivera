import { computed, ref } from "vue"
import { useLoad } from "./useLoad"
import { useServices } from "./useServices"

// El filtro de viabilidad es el mismo para QISKIT y DWAVE: basta con pedírselo a uno.
const ANALYZER_SOLVER = "QISKIT"

// Umbrales del filtro inicial del engine cuántico: un grupo es apto si pasa los tres.
export const VIABILITY_THRESHOLDS = [
    { key: "demand_granularity", label: "G", limit: "< 0.6", passes: value => value < 0.6 },
    { key: "capacity_tightness", label: "Tc", limit: "≤ 0.9", passes: value => value <= 0.9 },
    { key: "spatial_compressibility_index", label: "S", limit: "≤ 1.2", passes: value => value <= 1.2 },
]

function indicators(features = {}) {
    return VIABILITY_THRESHOLDS.map(({ key, label, limit, passes }) => {
        const value = typeof features[key] === "number" ? features[key] : null
        // Sin valor no se puede decir que falle: se muestra "-" y no se marca.
        return { key, label, value, limit, failing: value !== null && !passes(value) }
    })
}

function statusOf(result) {
    if (result.noDepot) return "noDepot"
    return result.isViable ? "viable" : "notViable"
}

/**
 * Viabilidad cuántica de cada grupo: el filtro inicial del engine cuántico
 * (/analyze) aplicado a los clientes y depósitos de cada grupo por separado.
 *
 * Solo dice si el grupo pasa los tres umbrales del filtro (granularidad de la
 * demanda, tensión de capacidad y compresibilidad espacial). No cuenta qubits: un
 * grupo apto puede no caber después en el simulador.
 */
export function useRoutesQuantumViability({ groups }, { isBenchmark, selectedBenchmark }) {
    const { post } = useLoad()
    const dataApi = useServices('data-service')

    // { [clusterId]: { isViable, features, noDepot } }
    const viabilityByCluster = ref({})
    const viabilityError = ref(null)
    const analyzingViability = ref(false)

    const viabilitySummary = computed(() => {
        const results = Object.values(viabilityByCluster.value)
        return {
            analyzed: results.filter(result => !result.noDepot).length,
            viable: results.filter(result => result.isViable).length,
            noDepot: results.filter(result => result.noDepot).length,
        }
    })

    // Una fila por grupo analizado, en el orden de los grupos, para el panel de viabilidad.
    const viabilityRows = computed(() =>
        (groups.value?.clusters || [])
            .filter(cluster => viabilityByCluster.value[cluster.id])
            .map(cluster => {
                const result = viabilityByCluster.value[cluster.id]
                return {
                    id: cluster.id,
                    customerCount: cluster.metadata?.customerCount ?? cluster.customers?.length ?? 0,
                    totalDemand: cluster.metadata?.totalDemand ?? null,
                    status: statusOf(result),
                    indicators: indicators(result.features),
                }
            })
    )

    function clusterPayload(cluster) {
        const depotIndexes = new Set(cluster.depots || [])
        return {
            customers: (cluster.customers || []).map(customer => customer.id),
            depots: (groups.value?.depots || [])
                .filter(depot => depotIndexes.has(depot.matrixIndex))
                .map(depot => depot.id),
        }
    }

    function analyzeUrl() {
        const base = isBenchmark()
            ? `/fms/routing/analyze/benchmarks/${selectedBenchmark.value}/cluster`
            : "/fms/routing/analyze/cluster"
        return `${base}?solverType=${ANALYZER_SOLVER}`
    }

    function toResult(data) {
        const analysis = data?.analysis || {}
        return { isViable: Boolean(analysis.is_viable), features: analysis.features || {} }
    }

    async function analyzeViability() {
        viabilityByCluster.value = {}
        viabilityError.value = null
        analyzingViability.value = true
        const url = analyzeUrl()
        const clusters = groups.value?.clusters || []
        // Un grupo sin unidad dentro del radio no tiene depósito desde el que salir: no
        // hay problema que analizar, y la pasarela lo rechazaría.
        for (const cluster of clusters.filter(cluster => !cluster.depots?.length)) {
            viabilityByCluster.value = {
                ...viabilityByCluster.value,
                [cluster.id]: { isViable: false, features: {}, noDepot: true },
            }
        }
        try {
            await Promise.all(clusters.filter(cluster => cluster.depots?.length).map(cluster =>
                post(dataApi, url, clusterPayload(cluster), viabilityByCluster, viabilityError, null,
                    data => ({ ...viabilityByCluster.value, [cluster.id]: toResult(data) }))
            ))
        } finally {
            analyzingViability.value = false
        }
    }

    function resetViability() {
        viabilityByCluster.value = {}
        viabilityError.value = null
    }

    return {
        viabilityByCluster,
        viabilityError,
        analyzingViability,
        viabilitySummary,
        viabilityRows,
        analyzeViability,
        resetViability,
        clusterPayload,
    }
}
