import { ref } from "vue";
import { useLoad } from "./useLoad";
import { useServices } from "./useServices";
import { useI18n } from "vue-i18n";
import { useSelections } from "./useSelections";

export function useRoutesData({
    disabledNextPhases,
    goToPhase,
    getCurrentPhaseName
}) {

    const { t } = useI18n() 
    const {
        selections,
        selectOnlyOne 
    } = useSelections()

    const data = ref({})
    const dataError = ref()
    const {executeLoad} = useLoad()
    const dataApi = useServices("data-service")
    const url = "/fms/routing/data"


    const dataModes = [
        {
            id:1,
            name: t("routes.dataModes.auto.name"),
            description: t("routes.dataModes.auto.description"),
            icon: "pi pi-bolt"
        },
        {
            id:2,
            name: t("routes.dataModes.all.name"),
            description: t("routes.dataModes.all.description"),
            icon: "pi pi-database"
        },
        {
            id:3,
            name: t("routes.dataModes.custom.name"),
            description: t("routes.dataModes.custom.description"),
            icon: "pi pi-wrench"
        },
        {
            id: 4,
            name: t("routes.dataModes.benchmarks.name"),
            description: t("routes.dataModes.benchmarks.description"),
            icon: "pi pi-chart-scatter"
        },

    ]

    //const radius = ref(50)

    async function loadInitialData() {
        await executeLoad(dataApi,url,data,dataError)
    }

    function selectDataMode(id) {
        const oneSelected = selectOnlyOne(id,selections)
        if (oneSelected) {
            goToPhase({name: "selectMode"})
        } else {
            disabledNextPhases()
        }
        
    }

    function showDataModeSelector() {
        return getCurrentPhaseName() === "configData"

    }


    return {
        data,
        dataModes,
        selectedDataModeId: selections,
        loadInitialData: loadInitialData,
        selectDataMode,
        showDataModeSelector
        
    }

}