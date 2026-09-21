import { useI18n } from "vue-i18n"
import { useSelections } from "./useSelections"
import { computed, ref, watch } from "vue"
import { useLoad } from "./useLoad"
import { useServices } from "./useServices"
import { addlayer, initSolverLayer } from "./useDeliveraMap"

export function useRoutesSolver(
    {
        disabledNextPhases,
        goToPhase,
        allowNextPhase,
        getCurrentPhaseName
    },
    {
        isComparisonMode, 
        isCustomMode
    },
    {
        groupsRows,
        getSlotInstance,
        countSlots
    }
) {

    const { t } = useI18n() 
    const {
        selections,
        selectOnlyOne,
        toggleSelection
    } = useSelections()
    const {post} = useLoad()
    const dataApi = useServices('data-service')
    const routesBySolver = ref({})

    const routesByCluster =ref({})

    const routesErrorBySolver = ref({})
    const showCatalog = ref(false)

    const slotRows = computed(() => setSlotRows())

    
    
    const solvers = [
        {
            id:1,
            name: t("routes.solvers.greedy.name"),
            description: t("routes.solvers.greedy.description"),
            icon: "pi pi-bolt",
            type: "GREEDY"
        },{
            id:2,
            name: t("routes.solvers.genetic.name"),
            description: t("routes.solvers.genetic.description"),
            icon: "pi pi-share-alt",
            type: "GENETIC"
        }
    ]

    const translateSolver = {
        "GREEDY":"GREEDY",
        "GENETIC":"GENETIC"
    }
    
    function getSolverNames() {
        return Object.keys(translateSolver)
    }
    
        
    
    function selectSolver(id) {
        if (isComparisonMode()) {
            toggleSelection(id,selections)
            allowNextPhase()
        } else if (isCustomMode()) {
            const oneSelected = selectOnlyOne(id,selections)
            if (oneSelected) {
                goToPhase(5)
            } else {
                disabledNextPhases()
            }
        }
    }
    
    function showSolverSelector() {
        return getCurrentPhaseName() === "selectSolvers"
    }

    function isSolved(solverType) {
        return routesBySolver.value[solverType]
    }

    async function executeSlots({data,layersBySolver , drawFunction = () => {}}) {
        const promises = []
        getSolverNames().forEach(name => initSolverLayer(name, layersBySolver) )
        
        for (const slotRow of slotRows.value) {
            const solversSelected = []
            const instance = slotRow.instance
            const payload = {
                customers: Array.from(instance.customers),
                depots: Array.from(instance.depots)
            };
            
            getSolverNames().forEach(name => {
                if (slotRow[name]) {
                    promises.push(promiseExecuteSolver(slotRow.id, payload, name))
                }
            })
        }

        await Promise.all(promises)
        const {
            customers,
            depots
        } = data.value
        const drawPromises = []
        console.log(routesByCluster.value)
        for (const slotRow of slotRows.value) {
            const slotResult = routesByCluster.value[slotRow.id]
            if (slotResult) {
                console.log(":)")
                for (const {solver, solution} of slotResult) {
                    drawPromises.push(
                        drawFunction({
                            routes: solution.routes,
                            customers: customers,
                            depots: depots, 
                            solverType: solver, 
                            slotId: slotRow.id
                        })
                    )
                }
               
            }
        }
        await Promise.all(drawPromises)
    }
    

    

    async function  promiseExecuteSolver(slotId, instance, solverType) {
        if (translateSolver[solverType] && !isSolved(solverType)) {
            return post(
                dataApi,
                "/fms/routing/solve/cluster?solverType="+translateSolver[solverType],
                instance,
                routesByCluster,
                routesErrorBySolver,
                null,
                (data) => {

                    const  newRoutes =  { ...routesByCluster.value}
                    if (!newRoutes[slotId]) {
                        newRoutes[slotId] = []
                    }
                    newRoutes[slotId].push({solver: solverType, solution: data})
                    
                    return newRoutes
                    /*
                    return {

                        ...routesBySolver.value,
                        [solverType]: data
                    }*/
                }
            )
        }else {
            // TODO: HANDLE ERRORS
            routesErrorBySolver.value = {
                ...routesErrorBySolver.value,
                [solverType]: "NO SOLVER TYPE AVAILABLE"
            }
            return () => {}
        }
        
    }


    function forExistingSelectedSolvers(executeFunction) {
        for (const id of selections.value) {
            const solver = solvers.find(_solver => _solver.id === id)
            if (solver.type) {
                executeFunction(solver)
            }
        }
    }
    

    async function executeAllSelected({
        data,
        layersBySolver, 
        drawFunction = () => {}
    }) {
        const promises = []
        forExistingSelectedSolvers((solver) => {
            initSolverLayer(solver.type, layersBySolver)
            promises.push(promiseExecuteSolver(solver.type))
        })
        await Promise.all(promises)
        const {
            customers,
            depots
        } = data.value
        const drawPromises = []
        forExistingSelectedSolvers((solver) => {
            drawPromises.push(drawFunction(routesBySolver.value[solver.type].routes, customers,depots, solver.type))
        })
        await Promise.all(drawPromises)
        /*
        Object.entries(layersBySolver.value).map(([solverType,layer]) =>{
            layer.root.addTo(map)
            addlayer(layerOverlay,layer.root,`S-${solverType}`)
        })*/
    
    }
    

    function createSlotRow(instance) {
        Object.keys(translateSolver).forEach(solverName => {
            instance[solverName] = false
        })
        instance.execute = false
        instance.executed = false
        return instance
    }

    function setSlotRows() {
        const newSlotRows = []
        const instances = []
        const totalSlots = countSlots()
        for(let i = 0; i<totalSlots ; i++) {
            const instance = getSlotInstance(i)
            const totalCustomers = instance.customers?.size
            const totalDepots = instance.depots?.size
            if( totalCustomers == 0 && totalDepots == 0) {
                continue
            }
            instances.push({
                id: i, 
                instance: instance, 
                totalCustomers: totalCustomers, 
                totalDepots: totalDepots
            })
        }

        instances.forEach( instance => {
            newSlotRows.push(createSlotRow(instance))
        })

        return newSlotRows
    }

    function getSolverDistance({slotId, solverType}) {
        let distance = undefined
        const slotEntry = routesByCluster.value[slotId]
        if (slotEntry) {
            const solverSolution = slotEntry.find( 
                solution => solution.solver === solverType
            )
            console.log("solution", solverSolution)
            if (solverSolution) {
                distance = solverSolution.solution.totalCost
            }
        }
        return distance

    }
    
    function toggleCatalog() {
        showCatalog.value = !showCatalog.value
    }

    function disableSelection(slotId,solverName) {
        const slot = slotRows.value.find(slot => slot.id === slotId)
        let disable = true
        if (isComparisonMode()) {
            disable = false
        } else if (isCustomMode()) {
            let anyActive = ""
            for (const name of getSolverNames()) {
                if (slot[name]) {
                    anyActive = name
                    break;
                }
            }
            if (!anyActive) {
                disable = false
            } else if(anyActive === solverName) {
                disable = false
            }
             
        }

        return disable || slot.totalDepots == 0

    }

    return {
        selectedSolversId: selections,
        solvers,
        showCatalog,
        routesBySolver,
        routesErrorBySolver,
        slotRows,
        routesByCluster,
        selectSolver,
        showSolverSelector,
        promiseExecuteSolver,
        executeAllSelected,
        toggleCatalog,
        getSolverNames,
        disableSelection,
        executeSlots,
        getSolverDistance
    }
}