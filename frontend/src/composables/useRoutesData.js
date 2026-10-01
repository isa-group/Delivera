import { computed, ref } from "vue";
import { useLoad } from "./useLoad";
import { useServices } from "./useServices";
import { useI18n } from "vue-i18n";
import { useSelections } from "./useSelections";
import { useDateRange } from "./useDateRange";
import { fitBounds } from "./useDeliveraMap";
import { useNumberFormat } from "./useNumberFormat";


export function useRoutesData({
    disabledNextPhases,
    goToPhase,
    getCurrentPhaseName
}) {
    const { numberI18n} = useNumberFormat()
    const { t } = useI18n() 
    const {
        selections,
        selectOnlyOne 
    } = useSelections()

    const data = ref({})
    const allBenchmark = ref([])
    const allBenchmarkError = ref()
    const possibleBenchmarks = computed(() => {
        return [...allBenchmark.value].map(instance => {
            return {value: instance.name, label: instance.name}
        })


    })

    const selectedBenchmark = ref({})
    
    const dataError = ref()
    const {executeLoad, post} = useLoad()
    const dataApi = useServices("data-service")
    const url = "/fms/routing/data"
    const benchmarksUrl = "/fms/routing/data/benchmarks"
    const showConfig = ref(false)
    const includeNullsFromDate  = ref(true)
    const includeNullsToDate = ref(true)

    const isBenchmarkComputed = computed(() => {
        return isBenchmark()
    })

    const deliveryWindowConfig = computed(() => {
        return {
            fromDate: selectedInstants.value.from ?? null,
            toDate: selectedInstants.value.to ?? null,
            includeNullsFromDate: includeNullsFromDate.value,
            includeNullsToDate: includeNullsToDate.value
        }
    })

    const {fromDate, toDate, selectedInstants} = useDateRange()

    const benchmarkId = 3
    const customId = 2

    const dataModes = [
        {
            id:1,
            name: t("routes.dataModes.all.name"),
            description: t("routes.dataModes.all.description"),
            icon: "pi pi-database"
        },
        {
            id:2,
            name: t("routes.dataModes.custom.name"),
            description: t("routes.dataModes.custom.description"),
            icon: "pi pi-calendar-clock"
        },
        {
            id: benchmarkId,
            name: t("routes.dataModes.benchmarks.name"),
            description: t("routes.dataModes.benchmarks.description"),
            icon: "pi pi-chart-scatter"
        },

    ]

    

    function isBenchmark() {
        return selections.value.has(benchmarkId)
    }

    function showBenchmarksSection() {
        return isBenchmark() && showConfig.value
    }
    
    function isCustomMode() {
        return selections.value.has(customId)
    }

    function showCustomSection() {
        return isCustomMode() && showConfig.value
    }

    function showDataConfigButton() {
        return isCustomMode() || isBenchmark()
    }

    async function loadInitialData() {
        console.log(deliveryWindowConfig.value)
        dataError.value = ""
        if (isBenchmark() && selectedBenchmark.value) {
            await executeLoad(dataApi,benchmarksUrl+`/${selectedBenchmark.value}`,data,dataError)
        } else if(isCustomMode()) {
            await post(dataApi,url, deliveryWindowConfig.value ,data, dataError )
        } else {
            await executeLoad(dataApi,url,data,dataError)
        }
        if(!dataError.value) {
            goToPhase({name: "groupingData"})
        }
       
       
    }

    function toggleConfig() {
        showConfig.value = !showConfig.value
    }

    function mapInstances(data) {
        return data.instances.map(instance => {
            instance.maxDuration = instance.maxDuration ?? '-'
            instance.loadRatio = numberI18n({value: instance.loadRatio, maxFractionDigits: 2})
            return instance
        })
    }

    async function selectDataMode(id) {
        const oneSelected = selectOnlyOne(id,selections)
        if (oneSelected) {
            if(isBenchmark()) {
                await executeLoad(dataApi,benchmarksUrl,  allBenchmark, allBenchmarkError, null, mapInstances)
                showConfig.value = true
            } 
        } else {
            disabledNextPhases()
        }
        
    }

    function showDataModeSelector() {
        return getCurrentPhaseName() === "configData"
    }

    function showLoadButton() {
        return selections.value.size > 0
    }


    return {
        data,
        dataError,
        dataModes,
        selectedDataModeId: selections,
        selectedInstants,
        fromDate,
        toDate,
        showConfig,
        allBenchmark,
        isBenchmarkComputed,
        possibleBenchmarks,
        selectedBenchmark,
        includeNullsFromDate,
        includeNullsToDate,
        deliveryWindowConfig,
        loadInitialData: loadInitialData,
        selectDataMode,
        toggleConfig,
        showDataModeSelector,
        showBenchmarksSection,
        showDataConfigButton,
        showLoadButton,
        isBenchmark,
        isCustomMode,
        showCustomSection

        
    }

}