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
    let initialCustomerLayer = null
    let initalDepotLayer = null
    let finalDepotLayer = null
    const groupLayers = ref([])
    

    onMounted(async () => {
        initMap()
        await loadInitialData()
        layerOverlay = initOverlays(map)
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
        
    })

    function removeSelectedLayers(_layerIds) {
        const layerIds = new Set(_layerIds)
        if (layerOverlay?._layers) {
            layerOverlay._layers = [...layerOverlay._layers]
            .filter(la => !layerIds.has(la.layer?._leaflet_id))
        }
    }

    function removeGroupLayers() {
        const layerIds = new Set()
        for (const {id, layer} of groupLayers.value) {
            layerIds.add(layer._leaflet_id)
            layer.remove()
        }
        groupLayers.value = []
        removeSelectedLayers(layerIds)
     
       
    }


    onUnmounted(() => {
        removeGroupLayers()
        initialCustomerLayer = null
        initalDepotLayer = null
        finalDepotLayer = null

        unmountMap(map,layersBySolver,layerOverlay)
    })

    async function  runGrouping() {
        if (groups.value) {
            removeGroupLayers()
        }
        await executeGrouping()
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

    async function  run() {
        executeAllSelected({
            data,
            layersBySolver,
            drawFunction: drawRoutes
        })
        Object.entries(layersBySolver.value).map(([solverType,layer]) =>{
            layer.root.addTo(map)
            addlayer(layerOverlay,layer.root,t(`routes.layers.${solverType}`))
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