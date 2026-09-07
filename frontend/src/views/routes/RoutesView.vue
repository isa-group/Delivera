<script setup>
import { useRoutes } from '@/composables/useRoutes';
import { Step, StepList, Stepper } from 'primevue';
import { useI18n } from 'vue-i18n';
import OptionsGrid from './OptionsGrid.vue';



const { t } = useI18n() 
const {
        mapEl,
        currentPhase,
        phases,
        currentPhaseTitle,
        selectedModesId,
        modes,
        dataModes,
        groupingParams,
        groupingNormalSelectors,
        solvers,
        selectedSolversId,
        selectedDataModeId,
        realCostBySolver,
        selectPhase,
        canGoToPhase,
        allowNextPhases,
        selectMode,
        showModeSelector,
        selectSolver,
        showSolverSelector,
        run,
        showExecutions,
        selectDataMode,
        showDataModeSelector,
        showGroupingParams,
        clampValue,
        runGrouping

    } = useRoutes()


console.log(realCostBySolver)
</script>

<template>
    <div class="background">
        <h1>{{t('routes.title')}}</h1>
        <Stepper class="stepper" :value="currentPhase">
            <StepList>
                <Step 
                    v-for="phase in phases"
                    :key="phase.name"
                    :disabled="!canGoToPhase(phase)" 
                    :value="phase.value"
                    @Click="selectPhase(phase)"
                >
                    {{t('routes.'+phase.name)}}
                </Step>
            </StepList>
        </Stepper>
        <div class="cards">
            <div class="left-panel">
                <h2>
                    {{t('routes.'+currentPhaseTitle)}}
                </h2>
                <div class="optionsGrid">
                    <OptionsGrid
                        v-if="showDataModeSelector()"
                        :items="dataModes"
                        :selected-ids="selectedDataModeId"
                        :on-select="selectDataMode"
                    />
                    <div
                        v-if="showGroupingParams()"
                        class="parameter-box"
                    >
                        <div 
                            class="parameter-card"
                            :key="selector.key" 
                            v-for="selector in groupingNormalSelectors"
                        >
                            <h4>{{selector.name}}</h4>
                            <div>
                                <input
                                    type="range"
                                    :min="selector.min"
                                    :max="selector.max"
                                    :step="selector.step"
                                    v-model.number="groupingParams[selector.key]"
                                />
                                <input
                                    class="parameter-card-textInput"
                                    type="number"
                                    :step="selector.step"
                                    v-model.number="groupingParams[selector.key]"
                                    @change="clampValue(selector)"
                                />
                            </div> 
                        </div>
                        <div>
                            <PButton
                                class="params-box-buttom"
                                severity="success"
                                :label="t('routes.execute')"
                                icon="pi pi-power-off"
                                :aria-label="t('routes.execute')"
                                @click="runGrouping()"
                            />
                        </div>
                    </div>

                    
                    <OptionsGrid
                        v-if="showModeSelector()"
                        :items="modes"
                        :selected-ids="selectedModesId"
                        :on-select="selectMode"
                    />
                    <OptionsGrid
                        v-if="showSolverSelector()"
                        :items="solvers"
                        :selected-ids="selectedSolversId"
                        :on-select="selectSolver"
                    />
                    <PButton
                        :label="t('pricing.cancel')"
                        icon="pi pi-check-circle"
                        :aria-label="t('pricing.cancel')"
                        @click="showExecutions()"
                    />
                    <PButton
                        :label="t('pricing.confirm')"
                        icon="pi pi-check-circle"
                        :aria-label="t('pricing.confirm')"
                        @click="run()"
                    />
                    <div :key="tuple[0]" v-for="tuple in realCostBySolver">
                        <p>{{tuple[0]}} - {{ tuple[1].distance/1000 }} km - {{ tuple[1].duration /3600}} h</p>
                    </div>
                </div>
              
                
            </div>
            <div class="right-panel">
                <div ref="mapEl" class="map" />
            </div>
        </div>
        

        <div v-if="true">
            <PButton
                :label="t('pricing.nexts')"
                icon="pi pi-check-circle"
                :aria-label="t('pricing.nexts')"
                @click="allowNextPhases(5)"
            />
        </div>
    </div>
</template>

<style scoped src="./RoutesView.css"></style>