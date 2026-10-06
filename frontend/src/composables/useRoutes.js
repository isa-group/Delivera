import { icon } from "leaflet"
import { computed, onMounted, onUnmounted, ref, watch } from "vue"
import { useI18n } from "vue-i18n"
import { useLoad } from "./useLoad"
import { useServices } from "./useServices"
import {
    createMap, addMarker, addRoute, clusterOptions, fitBounds,addFmsRoute,
    attachRouteVisibilityHandler, currentLocationOf, isActiveOrder, hasOriginCoords,
    initSolverLayer,
    routesOverlays,
    initOverlays,
    addlayer,
    initLayer,
  } from '@/composables/useDeliveraMap'
  
import { MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION } from '@/constants/map'
import L from 'leaflet'
import { useRoutesWizard } from "./useRoutesWizard"
import { useRoutesMap } from "./useRoutesMap"
import { useRoutesModes } from "./useRoutesMode"
import {  useRoutesData } from "./useRoutesData"
import { useRoutesSolver } from "./useRoutesSolver"
import { useRoutesGrouping } from "./useRoutesGrouping"
import { useNumberFormat } from "./useNumberFormat"



export function useRoutes() {

    const CLUSTER_COLOR = "#a78bfa"
    const DEPOT_COLOR = "#7c3aed"

    // =====================================
    // [START] MAP
    // =====================================
    const mapEl = ref(null)
    let map = null
    let clusterGroup = null
    let routeEntries = []
    let mapToken = 0
    let detachRouteVisibility = null
    let layerOverlay
   
    function initMap() {
        if (!mapEl.value) return
        if (map) { map.remove(); map = null }
        map = createMap(mapEl.value)
        map.setView(MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION)
    }
    // =====================================
    // [END] MAP
    // =====================================

    // =====================================
    // [START] UTILS
    // =====================================
    const {numberI18n} = useNumberFormat()

    const maxPerExecution = ref(20)

    const wizard = useRoutesWizard()
    const {goToPhase} = wizard
    const mapUtils = useRoutesMap()
    const {
        realCostBySolver,
        layersBySolver,
        realCostBySlot,
        addCustomers,
        addDepots, 
        drawRoutes,
        unmountMap,
        addRootSolutionToMap,
        unmountRootLayer,
        benchmarkAccumulateRealCostBySlotAndSolver
        
    } = mapUtils
    const modesUtils = useRoutesModes(wizard)
    const dataUtils = useRoutesData(wizard)
    const {
        data,
        dataError,
        selectedBenchmark,
        deliveryWindowConfig,
        loadInitialData: loadInitialData,
        isBenchmark,
        isCustomMode
    } = dataUtils
    const groupingUtils = useRoutesGrouping(wizard, mapUtils,{maxPerExecution}, {isBenchmark})
    const {
        groups,   
        groupsError,     
        executeGrouping,
        executeCustomGrouping,
        executeBenchmarksGrouping,
        groupsRows
    } = groupingUtils
    const solverUtils = useRoutesSolver(wizard,modesUtils, groupingUtils)
    const {executeAllSelected,executeSlots, getSolverDistance, routesBySolver} = solverUtils
    const { t } = useI18n() 
    let initialCustomerLayer = null
    let initalDepotLayer = null
    let finalDepotLayer = null
    const groupLayers = ref([])
    const selectedClusterId = ref(null)

    const computedMetricsBySlot = computed(() => {
        const result = []
        Object.entries(realCostBySlot.value)
        .forEach(([slotId,v]) => {
            Object.entries(v).forEach(([solverType,metrics]) => {
                const solverDistance = getSolverDistance({slotId, solverType})
                result.push({
                    id: slotId, 
                    solverType: t(`routes.solvers.${solverType}.name`), 
                    solverDistance:  `${
                        solverDistance? numberI18n({value: solverDistance, maxFractionDigits: 3}) :  "-"
                    }`, 
                    aproxDistance: metrics.distance ? `${numberI18n({value: metrics.distance/1000, maxFractionDigits: 3})} ` : '-', 
                    aproxDuration:  metrics.duration ? `${numberI18n({value: metrics.duration/3600})}` : '-',
                    rawAproxDistance: metrics.distance/1000,
                    rawSolverDistance: solverDistance
                })
            })
        })
        result.sort((e1, e2) => {

            const id1 = e1.id ?? 0
            const id2 = e2.id ?? 0
        
            if (id1 !== id2) {
                return id1 - id2
            }
        
            const dist1 = e1.rawAproxDistance ?? Number.MAX_VALUE
            const dist2 = e2.rawAproxDistance ?? Number.MAX_VALUE

            if (dist1 == dist2) {
                return e1.rawSolverDistance - e2.rawSolverDistance
            }
        
            return dist1 - dist2
        })
        const visitedSlot = {}
        result.map(resultEntry => {
            if (! visitedSlot[resultEntry.id]) {
                visitedSlot[resultEntry.id] = resultEntry.rawAproxDistance 
            }
            
            const bestSlotEntry = visitedSlot[resultEntry.id] 
            resultEntry.best = bestSlotEntry === resultEntry.rawAproxDistance
            
            return resultEntry
        })
        return result
    })
   

    
    onMounted(async () => {
        initMap()
        layerOverlay = initOverlays(map)
    })

    async function runInitialData() {
        removeGroupLayers()
        if (finalDepotLayer) {
            finalDepotLayer.remove()
            layerOverlay.removeLayer(finalDepotLayer)
            finalDepotLayer = null
        }
        await loadInitialData()
        if (initialCustomerLayer) { 
            initialCustomerLayer.remove()
            layerOverlay.removeLayer(initialCustomerLayer)
            initialCustomerLayer = null
        }
        if (initalDepotLayer) { 
            initalDepotLayer.remove()
            layerOverlay.removeLayer(initalDepotLayer)
            initalDepotLayer = null
        }

        if (data.value) {
            initialCustomerLayer =  initLayer()
            initalDepotLayer = initLayer()
            const {
                customers,
                depots
            } = data.value
            addCustomers({map: initialCustomerLayer, customers})
            addDepots({map: initalDepotLayer, depots})
            addlayer(layerOverlay,initialCustomerLayer,t("routes.layers.initialCustomers"))
            addlayer(layerOverlay,initalDepotLayer,t("routes.layers.initialDepots"))
            initialCustomerLayer.addTo(map)
            initalDepotLayer.addTo(map)
        }
    }

   
    

    function removeGroupLayers() {
        for (const {id, layer} of groupLayers.value || []) {
            layer.remove()
            layerOverlay.removeLayer(layer)
            
        }
        if (finalDepotLayer) {
            finalDepotLayer.remove()  
            layerOverlay.removeLayer(finalDepotLayer)    
                 
            finalDepotLayer = null
        }
        groupLayers.value = []
      
       
    }


    // TODO: FIX THIS MAY PROVOKE A BUG IN THE MAP,  MAKERS GO CRAZY SOME TIMES
    /*
    function updateMapVisibility(selectedId) {
        const toggleGroup = selectedId === selectedClusterId.value
        for (const {id, layer} of groupLayers.value || []) {
            
            const newFocus = (Number(id) === selectedId && !toggleGroup)
            const visible =   toggleGroup || newFocus ;

            if (visible) {
                map.addLayer(layer);
            } else {
                map.removeLayer(layer);
            }
        }
        return toggleGroup
    }
    */

    function focusOnGroup(selectedId) {
        /*const toggleGroup = updateMapVisibility(selectedId);
        if (toggleGroup) {
            selectedClusterId.value = null;
        } else {
            selectedClusterId.value = selectedId;
        }*/
        
    }


    onUnmounted(() => {
        removeGroupLayers()
        initialCustomerLayer = null
        initalDepotLayer = null
        finalDepotLayer = null
        unmountRootLayer({layerOverlay})
        unmountMap(map,layersBySolver,layerOverlay)
    })

    async function  runGrouping() {
        selectedClusterId.value == null
        if (groups.value) {
            removeGroupLayers()
        }
        if (isBenchmark()) {
            await executeBenchmarksGrouping(selectedBenchmark.value)
        } else if(isCustomMode()) {
            await executeCustomGrouping(deliveryWindowConfig.value)
            
        } else {
            await executeGrouping()
        }
        
        if (groups.value) {
            const executionResult = groups.value
            const depotsIndexs = new Set()
            for (const cluster of executionResult?.clusters || []) {
                const clusterLayer =  initLayer()
                addCustomers({map: clusterLayer, customers:cluster.customers, customColor: CLUSTER_COLOR })
                groupLayers.value = [
                    ...groupLayers.value,
                    {id:cluster.id, layer:clusterLayer}
                ]
                clusterLayer.addTo(map)
                addlayer(layerOverlay,clusterLayer, t("routes.layers.cluster")+" "+cluster.id)
                for (const depotIndex of cluster.depots || []) {
                    depotsIndexs.add(depotIndex)
                }
            }
            const clusterDepots = [...executionResult.depots].filter(d => depotsIndexs.has(d.matrixIndex))
            finalDepotLayer = initLayer()
            addDepots({map: finalDepotLayer, depots: clusterDepots, customColor: DEPOT_COLOR })
            addlayer(layerOverlay,finalDepotLayer, t("routes.layers.depots"))
            finalDepotLayer.addTo(map)
            
            goToPhase({name: "showData"})
        }      
    }

    async function  runSolvers() {
        if (isBenchmark()) {
            // drawRoutes --> This will calculate OSMR routes, however benchmarks have clients in the ocean and sea, so it will not draw any route.
            executeSlots({
                data, 
                layersBySolver, 
                drawFunction: benchmarkAccumulateRealCostBySlotAndSolver, 
                urlBase: `/fms/routing/solve/benchmarks/${selectedBenchmark.value}/cluster`
            })
        } else {
            executeSlots({data, layersBySolver, drawFunction: drawRoutes })
            .then(() => addRootSolutionToMap({map, layerOverlay}))
        }
        
        
    }

 
    // =====================================
    // [END] UTILS
    // =====================================

    return {
        ...wizard,
        ...dataUtils,
        ...groupingUtils,
        ...modesUtils,
        ...solverUtils,
        mapEl,
        computedMetricsBySlot,
        runSolvers,
        runInitialData,
        runGrouping,
        focusOnGroup
    }

}