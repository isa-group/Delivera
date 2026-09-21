import {
    createMap, addMarker, addRoute, clusterOptions, fitBounds,addFmsRoute,
    attachRouteVisibilityHandler, currentLocationOf, isActiveOrder, hasOriginCoords,
    routesOverlays,
    initOverlays,
    addlayer,
    initLayer,
  } from '@/composables/useDeliveraMap'
  
import { MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION } from '@/constants/map'
import L from 'leaflet'
import { computed, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

export function useRoutesMap() {

  
    const realCostBySlot = ref({})
    const markersByReference = ref({})
    const layersBySolver = ref({})
    const layersBySlot = ref({})


   
   
    const { t } = useI18n() 
   

    const colorBySolver = {
        "GREEDY":"#283593",
        "GENETIC":"#026901"
    }

    const dashedColorBySolver = {
        "GREEDY":"#283593",
        "GENETIC":"#026901"
    }



    function addSolutionLayer({slotId, routeId, solverType, layer}) {
        if (!layersBySlot.value[slotId]) {
            layersBySlot.value = {
                ...layersBySlot.value, 
                [slotId] : {}
            }
        }
        const slotEntry = layersBySlot.value[slotId]
        if (!slotEntry[solverType]) {
            slotEntry[solverType] = {
                root: initLayer(),
                routes: []
            }
        }
        layer.addTo(slotEntry[solverType].root)
        slotEntry[solverType].routes.push({id: routeId, layer: layer})
        
        layersBySlot.value = {
            ...layersBySlot.value, 
            [slotId] : slotEntry
        }
    }

    function getRootAndSolverBySlotList() {
        return Object.entries(layersBySlot.value).map(([k,v]) => {
            return {
                slotId: k, 
                solutions: Object.entries(v).map(([k1,v1]) => {
                    return { solverType: k1, root: v1.root}
                }) 
            }
        })
    }


    function addRootSolutionToMap({map, layerOverlay}) {
        if (map == null || layerOverlay == null) return;

        const RootAndSolverBySlot =  getRootAndSolverBySlotList()

        RootAndSolverBySlot.forEach( ({slotId, solutions}) => {
            solutions.forEach( ({solverType, root}) => {
                root.addTo(map)
                addlayer(
                    layerOverlay, 
                    root,
                    `${t(`routes.layers.${solverType}`)}-${t(`routes.tables.slot`)} ${slotId}`)
            })
        })
    }

    function unmountRootLayer({layerOverlay}) {
        getRootAndSolverBySlotList()
            .forEach(({slotId, solutions}) => {
                solutions.forEach(({solverType, root}) => {
                    root.remove()
                    layerOverlay.removeLayer(root)
                })
            }) 
    }


    /*
      
        const visibleLayers = ref(new Set())
         const availableLayers = computed(() =>
             Array.from(selectedSolversId.value)
                 .map(id => solvers.find(s => s.id === id))
         )

         function toggleLayer(solverType) {
            if (map === null) return;
            const layer = layersBySolver.value[solverType]
        
            const visible = new Set(visibleLayers.value)
        
            if (visible.has(solverType)) {
        
                visible.delete(solverType)
                
                map.removeLayer(layer)
        
            } else {
        
                visible.add(solverType)
        
                layer.addTo(map)
            }
        
            visibleLayers.value = visible
        }
     */
        

   

    function unmountMap(map, layersBySolver, layerOverlay) {
        if (layersBySolver.value) {
            Object.entries(layersBySolver.value).forEach(([type,data])=> {
                data.root?.remove()
                for (const route of data.routes) {
                    route.layer?.remove()
                }

            })
        }
        if (layerOverlay) {layerOverlay.remove(); layerOverlay = null}
        if (map) { map.remove(); map = null } 
    }

        

    function addMarkerToMap(map,elemets, name, titlePrefix, customColor) {
        let index = 1
        for (const elemet of elemets) {
            const marker = addMarker(map,{
                id: elemet.id,
                lat: elemet.lat,
                lon: elemet.lng,
                kind:name,
                title: titlePrefix+"-"+index,
                customColor: customColor? customColor :  name === "OWN_UNIT" ? '#000000' :"#6d6d6d"
            })
            markersByReference.value = {
                ... markersByReference.value,
                [titlePrefix+"-"+elemet.id]: marker
            }
            marker.addTo(map)
            index++
        }
    }

    
    function addCustomers({map,customers, customColor = null}) {
        addMarkerToMap(map,customers,"customers","customer", customColor)
        
    }

    function addDepots({map,depots, customColor = null}) {
        addMarkerToMap(map,depots,"OWN_UNIT","depot", customColor)
    }

    function getCoordinatesByElemetId(elemets) {
        return [...elemets].reduce((acc,elemet) => {
            acc[elemet.id] = {lat: elemet.lat, lon: elemet.lng}
            return acc
        },{})
    }

    function addRouteOrderMarker(map, route, customersById, depotsById, solverType, routeIndx, dashed) {
        const depotId = route.depotId
        const depotCoords = depotsById[depotId]
        if (depotCoords){
            addMarker(map,{
                id: solverType+'-'+depotId,
                lat: depotCoords.lat,
                lon: depotCoords.lon,
                kind: 'OWN_UNIT',
                title: 'test',
                subtitle: 'test',
                customColor: getRouteColor(solverType)
            }).addTo(map)
        }
        for (let stop = 0; stop< route.stops.length ; stop++) {
            const client = route.stops[stop]
            const clientCoords = customersById[client]
            
            if (!clientCoords) continue;
            addMarker(map,{
                id: solverType+'-'+client,
                lat: clientCoords.lat,
                lon: clientCoords.lon,
                kind: 'STOP',
                title: 'test',
                subtitle: 'test',
                stop: stop+1,
                customColor: getRouteColor(solverType, dashed)
            }).addTo(map)
        }

    }
    


    function getRouteColor(solverType, dashed) {
        if (dashed) {
            return dashedColorBySolver[solverType] ?? "#000000"
        }else {
            return colorBySolver[solverType] ?? "#000000"
        }
    }

    function accumulateRealCostBySlotAndSolver({
        slotId, 
        solverType,
        routeMetrics
    }) {
        if (!realCostBySlot.value[slotId]) {
            realCostBySlot.value = {
                ...realCostBySlot.value,
                [slotId]: {}
            }
        }
        const slotEntry = realCostBySlot.value[slotId]
        slotEntry[solverType] = routeMetrics

        realCostBySlot.value = {
            ...realCostBySlot.value,
            [slotId]: slotEntry
        }

        

    }


    // TODO: REFACTOR
    async function drawRoutes({
        routes, 
        customers, 
        depots, 
        solverType,
        slotId
    }) {
        const customersById = getCoordinatesByElemetId(customers)
        const depotsById = getCoordinatesByElemetId(depots)
        const addRoutesPromises = []
        let routeIndx = 0
        //const osrmRoutes = []
        const routeMetrics = {
            distance: 0,
            duration: 0
        }        
        for (const route of routes) {
            const useDataFunction = (data) => {
                //osrmRoutes.push(data)
                const route = data.routes?.[0]
                if (!route) {
                    return
                }
                routeMetrics.distance += route.distance
                routeMetrics.duration += route.duration
            }

            const drawRoute = route.stops && !route.vehicleId.startsWith("V-FALLBACK") 
            const drawDashedRoute = route.vehicleId.startsWith("V-GA-")
            if (drawRoute) {
                
                const depotId = route.depotId
                const depotCoords = depotsById[depotId]
                if (!depotCoords) continue;
                // map
                const layer = initLayer()
                addRouteOrderMarker(layer, route,customersById,depotsById, solverType, routeIndx, drawDashedRoute)
                addRoutesPromises.push(
                    addFmsRoute(
                        layer,
                        {
                            depotId: depotId,
                            stops: route.stops,
                            depotsById: depotsById,
                            customersById: customersById,
                            markers: markersByReference.value,
                            color: `${getRouteColor( solverType, drawDashedRoute )}`,
                            useDataFunction: useDataFunction,
                            dashed: drawDashedRoute
                        }
                    )
                )
                addSolutionLayer({slotId, routeId: routeIndx, solverType, layer})

                routeIndx++
            }
        }
        
        await Promise.all(addRoutesPromises)
        accumulateRealCostBySlotAndSolver({slotId, solverType, routeMetrics})
    }




    return  {
        realCostBySlot,
        markersByReference,
        layersBySolver,
        layersBySlot,
        addCustomers,
        addDepots, 
        drawRoutes,
        unmountMap,
        addRootSolutionToMap,
        unmountRootLayer
    }
}