import { ref } from "vue"
import { useI18n } from "vue-i18n"
import {  useSelections } from "./useSelections"

export function useRoutesModes({
    disabledNextPhases,
    goToPhase,
    getCurrentPhaseName
}) {
    const { t } = useI18n() 
    const {
        selections,
        selectOnlyOne 
    } = useSelections()
    const comparisonModeId = 102
    const customModeId = 101
    //const selectedModesId = ref(new Set())
    const modes = [
        {
            id:1,
            name: t("routes.modes.best.name"),
            description: t("routes.modes.best.description"),
            icon: "pi pi-trophy"
        },{
            id:2,
            name: t("routes.modes.fastest.name"),
            description: t("routes.modes.fastest.description"),
            icon: "pi pi-bolt"
        },
        {
            id:customModeId,
            name: t("routes.modes.custom.name"),
            description: t("routes.modes.custom.description"),
            icon: "pi pi-cog"
        },
        {
            id:comparisonModeId,
            name: t("routes.modes.comparison.name"),
            description: t("routes.modes.comparison.description"),
            icon: "pi pi-chart-bar"
        }
    ]

    function selectMode(id) {
        const oneSelected = selectOnlyOne(id,selections)
        if (oneSelected) {
            goToPhase({name: "selectSolvers"})
        } else {
            disabledNextPhases()
        }
        
    }

    function showModeSelector() {
        return getCurrentPhaseName() === "selectMode"

    }

    function isComparisonMode() {
        return selections.value?.has(comparisonModeId)
    }

    function isCustomMode() {
        return selections.value?.has(customModeId)
    }

    return {
        selectedModesId : selections,
        comparisonModeId,
        customModeId,
        modes,
        selectMode,
        showModeSelector,
        isComparisonMode,
        isCustomMode

    }
}