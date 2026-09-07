import { computed, ref } from "vue"


export function useRoutesWizard() {
    const currentPhase = ref(1)
    const currentPhaseEnabled = ref(1)
        
    
    const phases = [
        {name:"configData", value: 1},
        {name:"groupingData", value: 2},
        {name:"showData", value: 3},
        {name:"selectMode", value: 4},
        {name:"selectSolvers", value: 5},
        {name:"executeSolvers", value: 6},
    ]
    
    const currentPhaseTitle = computed(() => {
        return phases[currentPhase.value-1].name
    })

    function selectPhase(phase) {
        currentPhase.value  = phase.value
    }

    function disabledNextPhases() {
        currentPhaseEnabled.value = currentPhase.value 
    }

    function allowNextPhases(maxPhase) {
        currentPhaseEnabled.value = maxPhase 
    }

    function allowNextPhase() {
        currentPhaseEnabled.value = currentPhase.value +1
    }

    function goToPhase({phase = null, name = null}) {
        if (phase == null && name == null) return;

        let value = phase?? phases.findIndex(p => p.name == name)+1
        
        currentPhase.value = value
        currentPhaseEnabled.value = Math.max(value,currentPhaseEnabled.value)
    }

    function canGoToPhase(phase) {
        return phase.value <= currentPhaseEnabled.value;
    }

    function getCurrentPhaseName() {
        return phases[currentPhase.value-1]?.name
    }



    return {
        phases,
        currentPhase,
        currentPhaseEnabled,
        currentPhaseTitle,
        selectPhase,
        allowNextPhase,
        disabledNextPhases,
        allowNextPhases,
        canGoToPhase,
        goToPhase,
        getCurrentPhaseName


    }
}