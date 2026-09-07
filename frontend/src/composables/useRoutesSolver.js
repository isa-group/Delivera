import { useI18n } from "vue-i18n"
import { useSelections } from "./useSelections"
import { ref } from "vue"
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
    const routesErrorBySolver = ref({})

    

    
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


    

    const translateSolver = {
        "GREEDY":"GREEDY",
        "GENETIC":"GENETIC"
    }

    async function  promiseExecuteSolver(solverType) {
        if (translateSolver[solverType] && !isSolved(solverType)) {
            return post(
                dataApi,
                "/fms/routing/solve?solverType="+translateSolver[solverType],
                {},
                routesBySolver,
                routesErrorBySolver,
                null,
                (data) => {
                    return {
                        ...routesBySolver.value,
                        [solverType]: data
                    }
                }
            )
        }else {
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

    return {
        selectedSolversId: selections,
        solvers,
        routesBySolver,
        routesErrorBySolver,
        selectSolver,
        showSolverSelector,
        promiseExecuteSolver,
        executeAllSelected
    }
}