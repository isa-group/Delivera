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
import { onUnmounted, ref } from 'vue'

export function useRoutesMap() {

  
    const realCostBySolver = ref({})
    const markersByReference = ref({})
    const layersBySolver = ref({})
   
   

    const colorBySolver = {
        "GREEDY":"#a10000",
        "GENETIC":"#026901"
    }

    const dashedColorBySolver = {
        "GREEDY":"#551919",
        "GENETIC":"#61895e"
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

   


    async function drawRoutes(routes, customers, depots, solverType) {
        const customersById = getCoordinatesByElemetId(customers)
        const depotsById = getCoordinatesByElemetId(depots)
        const addRoutesPromises = []
        let routeIndx = 0
        const osrmRoutes = []
        const routeMetrics = {
            distance: 0,
            duration: 0
        }        
        for (const route of routes) {
            //if (routeIndx != 0) continue;
            console.log(route.vehicleId.startsWith("V-GA-"))
            const useDataFunction = (data) => {
                osrmRoutes.push(data)
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
                layer.addTo(layersBySolver.value[solverType].root)
                layersBySolver.value[solverType]?.routes?.push({
                    id: routeIndx,
                    layer
                })
                routeIndx++
            }
        }
        
        await Promise.all(addRoutesPromises)
        realCostBySolver.value = {
            ...realCostBySolver.value,
            [solverType]: routeMetrics
        }
        /*console.log(osrmRoutes)
        console.log(routeMetrics)
        console.log(
            routeMetrics.distance / 1000,
            "km"
        )
        console.log(
            routeMetrics.duration / 3600,
            "h"
        )*/

    }




    return  {
        realCostBySolver,
        markersByReference,
        layersBySolver,
        addCustomers,
        addDepots, 
        drawRoutes,
        unmountMap
    }
}