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



export function useRoutes() {
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
    const wizard = useRoutesWizard()
    const {goToPhase} = wizard
    const mapUtils = useRoutesMap()
    const {
        realCostBySolver,
        layersBySolver,
        addCustomers,
        addDepots, 
        drawRoutes,
        unmountMap
    } = mapUtils
    const modesUtils = useRoutesModes(wizard)
    const dataUtils = useRoutesData(wizard)
    const {
        data,
        loadInitialData: loadInitialData,
    } = dataUtils
    const groupingUtils = useRoutesGrouping(wizard, mapUtils)
    const {
        groups,
        executeGrouping
    } = groupingUtils
    const solverUtils = useRoutesSolver(wizard,modesUtils)
    const {executeAllSelected, routesBySolver} = solverUtils
    const { t } = useI18n() 
    let initialDataLayer = null
    const groupLayers = ref([])
    

    onMounted(async () => {
        initMap()
        await loadInitialData()
        layerOverlay = initOverlays(map)
        if (data.value) {
            initialDataLayer =  initLayer()
            const {
                customers,
                depots
            } = data.value
            addCustomers({map: initialDataLayer, customers})
            addDepots({map: initialDataLayer, depots})
            addlayer(layerOverlay,initialDataLayer,t("routing.layer.initialData"))
            initialDataLayer.addTo(map)
        }
        
    })
    onUnmounted(() => {
        unmountMap(map,layersBySolver,layerOverlay)
        for (const layer of groupLayers.value) {
            layer.remove()
        }
        groupLayers.value = []
    })

    async function  runGrouping() {
        await executeGrouping()
        if (groups.value) {
            const executionResult = groups.value
            for (const cluster of executionResult?.clusters || []) {
                const clusterLayer =  initLayer()
                addCustomers({map: clusterLayer, customers:cluster.customers, customColor: "#a78bfa" })
                groupLayers.value = [
                    ...groupLayers.value,
                    clusterLayer
                ]
                clusterLayer.addTo(map)
                addlayer(layerOverlay,clusterLayer, t("routing.cluster" )+"-"+cluster.id)
            }
            goToPhase({name: "showData"})
        }      
    }

    async function  run() {
        executeAllSelected({
            data,
            layersBySolver,
            drawFunction: drawRoutes
        })
        Object.entries(layersBySolver.value).map(([solverType,layer]) =>{
            layer.root.addTo(map)
            addlayer(layerOverlay,layer.root,`S-${solverType}`)
        })
    }

    // TODO: DELETE
    function showExecutions() {
        console.log(routesBySolver.value)
        console.log(realCostBySolver.value)
        console.log(data.value)
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
        realCostBySolver: computed(() => Object.entries(realCostBySolver.value)),
        run,
        runGrouping,
        showExecutions
    }

}