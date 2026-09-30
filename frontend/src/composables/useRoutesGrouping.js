import { computed, ref } from "vue"
import { useI18n } from "vue-i18n"
import { useServices } from "./useServices"
import { useLoad } from "./useLoad"
import { useNumberFormat } from "./useNumberFormat"

export function useRoutesGrouping({
    disabledNextPhases,
    goToPhase,
    getCurrentPhaseName
},{addCustomers, addDepots}, {maxPerExecution}, {isBenchmark}) {

    const { t } = useI18n() 
    const dataApi = useServices("data-service")
    const {post} = useLoad()
    const { numberI18n} = useNumberFormat()

    const groups = ref({})

    const executionSlots = ref({})

    const groupsRows = ref([])

    const groupsRowsMetadata = ref([])

    const availableSlots = ref([])

    const groupsError = ref()

    const showExtraMetrics = ref(false)



    const groupingParams = ref({
        dbscan: true,
        maxRadiusKm: 50,
        maxDepotRadiusKm: 150,
        minClusterSize: 3,
        maxClusterSize: 20,
        noiseClusterMaxDistanceKm: 150,
        noiseMaxDistanceKm: 30
    })

    const groupingParamsDisabled = computed(() => {
        return {
            dbscan: false,
            maxRadiusKm: !groupingParams.value?.dbscan,
            maxDepotRadiusKm: false,
            minClusterSize: !groupingParams.value?.dbscan,
            maxClusterSize: false,
            noiseClusterMaxDistanceKm: !groupingParams.value?.dbscan,
            noiseMaxDistanceKm: !groupingParams.value?.dbscan
        }

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


    const groupingNormalSelectors = computed( () => { 
        const benchmark = isBenchmark()
        return [
        objectByName("maxDepotRadiusKm",1, benchmark? 10000 : 500),
        objectByName("maxClusterSize",1,benchmark? 500 : maxPerExecution.value ),
        objectByName("minClusterSize",1,100),
        objectByName("maxRadiusKm",1,benchmark? 10000 : 500 ),
        objectByName("noiseClusterMaxDistanceKm",1, benchmark? 10000 : 500),
        objectByName("noiseMaxDistanceKm",1, benchmark? 10000 : 500)  
    ] 
    })


    function showGroupingParams(){
        return  getCurrentPhaseName() === "groupingData"
    }

    function showGroupDatatable(){
        return  getCurrentPhaseName() === "showData"
    }


    function clampValue(selector) {
        const value = groupingParams.value[selector.key];
    
        groupingParams.value[selector.key] = Math.min(
            selector.max,
            Math.max(selector.min, value)
        );
    }

    
    function setGroupData() {
        if (groups.value) {
            groupsRows.value = []
            groupsRowsMetadata.value = []
            executionSlots.value = {}
            let index = 0
            for (const cluster of groups.value?.clusters || []) {
                groupsRows.value = [
                    ...groupsRows.value,
                    {
                        id: cluster.id,
                        customerCount: cluster.metadata.customerCount,
                        totalDemand: cluster.metadata.totalDemand,
                        depots: cluster.depots.length,
                        executionSlot: index
                    }

                ]

                groupsRowsMetadata.value = [
                    ...groupsRowsMetadata.value,
                    {
                        id: cluster.id,
                        cohesion: numberI18n({value: cluster.metadata?.cohesion * 100,minFractionDigits: 1, maxFractionDigits: 1}),
                        density: numberI18n({value: cluster.metadata?.density, maxFractionDigits: 4}),
                        radius: numberI18n({value: cluster.metadata?.coverageRadiusKm}),
                        area: numberI18n({value: cluster.metadata?.area})
                    }
                ]

                executionSlots.value = {
                    ... executionSlots.value,
                    [index]: {ids: new Set([cluster.id]), customerCount: cluster.metadata.customerCount}
                }

                index ++
            }
        }
    }

    function checkInLimitsParams() {
        const limitViolations = []
        const limitConfig =groupingNormalSelectors.value.forEach(
            ({
                key,
                name,
                min,
                max,
                step
            }) =>{
                const paramValue = groupingParams.value[key]
                if (paramValue > max || paramValue < min) {
                    limitViolations.push(paramValue)
                }
            }
        )

        

        return limitViolations.length == 0
    }

    async function executeGrouping() {
        if (checkInLimitsParams()) {
            await post(dataApi,"/fms/routing/cluster",groupingParams.value,groups,groupsError)
            setGroupData()
            goToPhase({name: "selectMode"})
        } else {
            groupingParams.value = {
                dbscan: true,
                maxRadiusKm: 50,
                maxDepotRadiusKm: 150,
                minClusterSize: 3,
                maxClusterSize: 20,
                noiseClusterMaxDistanceKm: 150,
                noiseMaxDistanceKm: 30
            }
        }
       

    }

    async function executeCustomGrouping(deliveryWindowConfig) {
        if (checkInLimitsParams()) {
            const params = {...groupingParams.value}
            params.window = deliveryWindowConfig
            await post(dataApi,"/fms/routing/cluster",params,groups,groupsError)
            setGroupData()
            goToPhase({name: "selectMode"})
        } else {
            groupingParams.value = {
                dbscan: true,
                maxRadiusKm: 50,
                maxDepotRadiusKm: 150,
                minClusterSize: 3,
                maxClusterSize: 20,
                noiseClusterMaxDistanceKm: 150,
                noiseMaxDistanceKm: 30
            }
        }
       

    }

    async function executeBenchmarksGrouping(name) {
        await post(dataApi,`/fms/routing/cluster/benchmarks/${name}`,groupingParams.value,groups,groupsError)
        setGroupData()
        goToPhase({name: "selectMode"})

    }

    function toggleShowExtraMetrics() {
        showExtraMetrics.value = !showExtraMetrics.value
    }

    function getSlots() {
        const slots = {}
        let index = 0
        for (const {id, customerCount, executionSlot} of  groupsRows.value  || [] ) {
            if (!slots[index]) {
                slots[index] = {ids: new Set() , customerCount: 0}
            }

            if (!slots[executionSlot]) {
                slots[executionSlot] = { ids: new Set([id]) , customerCount: customerCount }
            } else {
                const slot = slots[executionSlot]
                slot.ids?.add(id)
                slot.customerCount += customerCount 
            }

            index++
        }
        return slots

    }

    function getSlotInstance(slot) {
        const slots = getSlots()
        if (!slots[slot]) return;
        const { ids } = slots[slot]
        const customers = new Set()
        const depotIndexs = new Set()
        const depots = new Set()
        for (const cluster of groups.value?.clusters || []) { 
            if (ids.has(cluster.id)) {
                cluster.customers.forEach(customer => customers.add(customer.id))
                cluster.depots.forEach(depot => depotIndexs.add(depot))
            }
        }
        for (const depot of groups.value?.depots || []) {
            if(depotIndexs.has(depot.matrixIndex)) {
                depots.add(depot.id)
            }
        }

        return {
            customers: customers,
            depots: depots
        }
    }

    
    function getAvailableSlots(clusterId) {
       const cluster = groupsRows.value.find( row => row.id === clusterId)
       const possibleSlots = Object.entries(getSlots())
       .filter(([k,v]) => v.ids?.has(clusterId) || (v.customerCount + cluster.customerCount <= maxPerExecution.value ))
       .map(([k,v]) => {return {value: Number(k), label: Number(k)}})
       return possibleSlots
    }

    function countSlots() {
        return (groups.value?.clusters || []).length
    }


    return {
        groupingParams,
        groupingNormalSelectors,
        groups,
        groupsError,
        groupingParamsDisabled,
        groupsRows,
        groupsRowsMetadata,
        showExtraMetrics,
        showGroupingParams,
        showGroupDatatable,
        clampValue,
        executeGrouping,
        executeCustomGrouping,
        executeBenchmarksGrouping,
        toggleShowExtraMetrics,
        getAvailableSlots,
        getSlots,
        getSlotInstance,
        countSlots
    }
}