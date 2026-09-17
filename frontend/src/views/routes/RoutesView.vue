<script setup>
import { useRoutes } from '@/composables/useRoutes';
import { Checkbox, Column, DataTable, Select, Step, StepList, Stepper } from 'primevue';
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
        showCatalog,
        dataModes,
        groupingParams,
        groupingNormalSelectors,
        solvers,
        selectedSolversId,
        selectedDataModeId,
        realCostBySolver,
        groupingParamsDisabled,
        groupsRows,
        slotRows,
        groupsRowsMetadata,
        showExtraMetrics,
        selectPhase,
        showGroupDatatable,
        canGoToPhase,
        allowNextPhases,
        selectMode,
        showModeSelector,
        disableSelection,
        getSolverNames,
        showSolverSelector,
        run,
        showExecutions,
        selectDataMode,
        showDataModeSelector,
        showGroupingParams,
        clampValue,
        runGrouping,
        toggleShowExtraMetrics,
        focusOnGroup,
        getAvailableSlots,
        toggleCatalog

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
                        <div class="parameter-checkbox">
                            <h4>{{t("routes.grouping.dbscan")}}</h4>
                            <Checkbox
                                class="parameter-checkbox-input"
                                v-model="groupingParams.dbscan"
                                :binary="true"
                            />
                        </div>
                        
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
                                    :disabled="groupingParamsDisabled[selector.key]"
                                />
                                <input
                                    class="parameter-card-textInput"
                                    type="number"
                                    :step="selector.step"
                                    v-model.number="groupingParams[selector.key]"
                                    @change="clampValue(selector)"
                                    :disabled="groupingParamsDisabled[selector.key]"
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
                    <div>
                        <DataTable 
                            class="groups-table"
                            v-if="showGroupDatatable() && !showExtraMetrics"
                            :value="groupsRows"
                            stripedRows
                            rowHover
                            scrollable
                            scrollHeight="300px"
                            @row-click="e => focusOnGroup(e.data.id)" 
                        >
                            <Column field="id" :header="t('routes.tables.group')"/>

                            <Column :header="t('routes.tables.slot')">
                                <template #body="{ data }">
                                    <Select
                                        v-model="data.executionSlot"
                                        :options="getAvailableSlots(data.id)"
                                        optionLabel="label"
                                        optionValue="value"
                                        @click.stop
                                    />
                                </template>
                            </Column>

                            <Column field="customerCount" :header="t('routes.tables.clients')"/>

                            <Column field="totalDemand" :header="t('routes.tables.demand')"/>

                            <Column field="depots" :header="t('routes.tables.units')"/>                        
                        </DataTable>
                        <DataTable 
                            class="groups-table"
                            v-if="showGroupDatatable() && showExtraMetrics"
                            :value="groupsRowsMetadata"
                            stripedRows
                            rowHover
                            scrollable
                            scrollHeight="300px"
                            @row-click="e => focusOnGroup(e.data.id)" 
                        >
                            <Column field="id" :header="t('routes.tables.group')"/>

                            <Column field="cohesion" :header="t('routes.tables.cohesion')">
                                <template #body="{ data }">
                                    {{ data.cohesion.toFixed(1) }}%
                                </template>
                            </Column>

                            <Column field="density" :header="t('routes.tables.density')">
                                <template #body="{ data }">
                                    {{ data.density.toFixed(4) }}
                                </template>
                            </Column>

                            <Column field="radius" :header="t('routes.tables.radius')">
                                <template #body="{ data }">
                                    {{ data.radius.toFixed(2) }}
                                </template>
                            </Column>
                            <Column field="area" :header="t('routes.tables.area')">
                                <template #body="{ data }">
                                    {{ data.area.toFixed(2) }}
                                </template>
                            </Column>
                        </DataTable>
                        <div class="groups-table-info"
                            v-if="showGroupDatatable()"
                        >
                            <PButton
                                v-if="showGroupDatatable()"
                                :label="`${(!showExtraMetrics? t('routes.show') : t('routes.hide'))} ${t('routes.ExtraMetrics')}`"
                                icon="pi pi-eye"
                                :aria-label=" `${(!showExtraMetrics? t('routes.show') : t('routes.hide'))} ${t('routes.ExtraMetrics')}`"
                                @click="toggleShowExtraMetrics()"
                            />
                            <div class="slot-info-box">
                                <h4>{{t("routes.slot.title")}}</h4>

                                <p>
                                    {{t("routes.slot.p1")}}
                                </p>

                                <p>
                                    {{t("routes.slot.p2")}}
    
                                </p>
                                <p>
                                    {{t("routes.slot.p3")}}
                                </p>

                                <p>
                                    {{t("routes.slot.p4")}}

                                </p>
                            </div>
                        </div>
                       
                    </div>
                    <OptionsGrid
                        v-if="showModeSelector()"
                        :items="modes"
                        :selected-ids="selectedModesId"
                        :on-select="selectMode"
                    />
                    <div v-if="showSolverSelector()">
                        <OptionsGrid
                            v-if="showCatalog"
                            :items="solvers"
                            :selected-ids="selectedSolversId"
                            :on-select="()=>{}"
                        />
                        <DataTable
                            class="groups-table"
                            v-if="!showCatalog"
                            :value="slotRows"
                            stripedRows
                            rowHover
                            scrollable
                            scrollHeight="300px"
                        >
                            <Column field="id" :header="t('routes.tables.slot')"/>

                            <Column field="totalCustomers" :header="t('routes.tables.clients')"/>

                            <Column field="totalDepots" :header="t('routes.tables.units')"/>

                            <Column :key="solverName" v-for="solverName in getSolverNames()" :header="t(`routes.solvers.${solverName}.name`)">
                                <template #body="{ data }">
                                    <Checkbox
                                        v-model="data[solverName]"
                                        binary
                                        :disabled="disableSelection(data.id, solverName)"
                                    />
                                </template>
                            </Column>
                        </DataTable>
                        <PButton
                            :label="`${(!showCatalog? t('routes.show') : t('routes.hide'))} ${t('routes.catalog')}`"
                            icon="pi pi-check-circle"
                            :aria-label="`${(!showCatalog? t('routes.show') : t('routes.hide'))} ${t('routes.catalog')}`"
                            @click="toggleCatalog()"
                        />
                    </div>
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
                @click="allowNextPhases({maxPhase: 5})"
            />
        </div>
    </div>
</template>

<style scoped src="./RoutesView.css"></style>