import { ref } from "vue"
import { useI18n } from "vue-i18n"
import { useServices } from "./useServices"
import { useLoad } from "./useLoad"

export function useRoutesGrouping({
    disabledNextPhases,
    goToPhase,
    getCurrentPhaseName
},{addCustomers, addDepots}) {

    const { t } = useI18n() 
    const dataApi = useServices("data-service")
    const {post} = useLoad()

    const groups = ref({})
    const groupsError = ref()

    const groupingParams = ref({
        maxRadiusKm: 100,
        maxDepotRadiusKm: 250,
        minClusterSize: 1,
        maxClusterSize: 20,
        noiseClusterMaxDistanceKm: 125,
        noiseMaxDistanceKm: 70
    })

    function objectByName(name,min, max, step=1) {
        return {
            key: name,
            name: t("routes.grouping."+name),
            min: min,
            max: max,
            step: step
        }
    }


    const groupingNormalSelectors = [
        objectByName("maxRadiusKm",1,500),
        objectByName("maxDepotRadiusKm",1,500),
        objectByName("minClusterSize",1,100),
        objectByName("maxClusterSize",1,100),
        objectByName("noiseClusterMaxDistanceKm",1,500),
        objectByName("noiseMaxDistanceKm",1,500)        
    ]


    function showGroupingParams(){
        return  getCurrentPhaseName() === "groupingData"
    }

    function clampValue(selector) {
        const value = groupingParams.value[selector.key];
    
        groupingParams.value[selector.key] = Math.min(
            selector.max,
            Math.max(selector.min, value)
        );
    }

    async function executeGrouping() {
        console.log(groupingParams.value)
        await post(dataApi,"/fms/routing/cluster",groupingParams.value,groups,groupsError)
        console.log(groups.value)
        goToPhase(3)

    }






    return {
        groupingParams,
        groupingNormalSelectors,
        groups,
        showGroupingParams,
        clampValue,
        executeGrouping
    }
}