import { computed, ref } from "vue"
import { useLoad } from "./useLoad"
import { useServices } from "./useServices"

// El filtro de viabilidad es el mismo para QISKIT y DWAVE: basta con pedírselo a uno.
const ANALYZER_SOLVER = "QISKIT"

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
        analyzeViability,
        resetViability,
        clusterPayload,
    }
}
