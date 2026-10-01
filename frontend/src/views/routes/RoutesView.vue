<script setup>
import { useRoutes } from '@/composables/useRoutes';
import { Checkbox, Column, DataTable, DatePicker, Select, Step, StepList, Stepper } from 'primevue';
import { useI18n } from 'vue-i18n';
import OptionsGrid from './OptionsGrid.vue';
import { getLocaleFormat } from '@/composables/useDateRange.js';




const { t} = useI18n() 
const {
        mapEl,
        currentPhase,
        phases,
        currentPhaseTitle,
        selectedModesId,
        modes,
        showConfig,
        showCatalog,
        dataModes,
        groupingParams,
        groupingNormalSelectors,
        solvers,
        selectedSolversId,
        selectedDataModeId,
        groupingParamsDisabled,
        groupsRows,
        slotRows,
        groupsRowsMetadata,
        showExtraMetrics,
        computedMetricsBySlot,
        allBenchmark,
        possibleBenchmarks,
        selectedBenchmark,
        toDate,
        fromDate,
        includeNullsFromDate,
        includeNullsToDate,
        selectPhase,
        showGroupDatatable,
        canGoToPhase,
        allowNextPhases,
        selectMode,
        showModeSelector,
        disableSelection,
        getSolverNames,
        showSolverSelector,
        runSolvers,
        runInitialData,
        toggleConfig,
        showDataConfigButton,
        showLoadButton,
        showBenchmarksSection,
        selectDataMode,
        showDataModeSelector,
        showGroupingParams,
        clampValue,
        runGrouping,
        toggleShowExtraMetrics,
        focusOnGroup,
        getAvailableSlots,
        toggleCatalog,
        showCustomSection,
       

    } = useRoutes()

    

    function rowClass(data) {
        return data.best
            ? 'best-solver-row'
            : '';
    }

   

    
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
                <h2 v-if="!showSolverSelector() && !showGroupDatatable()">
                    {{t('routes.'+currentPhaseTitle)}}
                </h2>
                
                
                <div class="optionsGrid">
                    
                    <div v-if="showDataModeSelector()">
                        <OptionsGrid
                            v-if="!showConfig"
                            :items="dataModes"
                            :selected-ids="selectedDataModeId"
                            :on-select="selectDataMode"
                        />
                        <DataTable 
                            class="groups-table"
                            v-if="showBenchmarksSection()"
                            :value="allBenchmark"
                            stripedRows
                            rowHover
                            scrollable
                            scrollHeight="300px"
                        >
                            <Column field="name" :header="t('routes.tables.name')"/>

                            <Column field="problemType" :header="t('routes.tables.type')"/>

                            <Column field="numDepots" :header="t('routes.tables.units')"/>

                            <Column field="numCustomers" :header="t('routes.tables.clients')"/>

                            <Column field="vehiclesPerDepot" :header="t('routes.tables.vehiclesPerDepot')"/>

                            <Column field="vehicleCapacity" :header="t('routes.tables.vehicleCapacity')"/>

                            <Column field="maxDuration" :header="t('routes.tables.maxDuration')"/>
                            
                            <Column field="totalDemand" :header="t('routes.tables.demand')"/> 
                            
                            <Column field="loadRatio" :header="t('routes.tables.loadRatio')"/>
                        </DataTable>
                        <Select
                            v-if="showBenchmarksSection()"
                            v-model="selectedBenchmark"
                            :options="possibleBenchmarks"
                            optionLabel="label"
                            optionValue="value"
                            @click.stop
                        />
                        <div class="custom-section" v-if="showCustomSection()">

                            <div class="date-config" >
                                <label>{{ t('routes.fromDate') }}</label>

                                <DatePicker
                                    v-model="fromDate"
                                    showTime
                                    showIcon
                                    manualInput
                                    hourFormat="24"
                                    :dateFormat="getLocaleFormat()"
                                    :max-date="toDate"
                                    :showOnFocus="false"
                                />

                                <div class="parameter-checkbox">
                                    <span>{{ t('routes.includeNullFromDate') }}</span>

                                    <Checkbox
                                        v-model="includeNullsFromDate"
                                        :binary="true"
                                    />
                                </div>
                            </div>

                            <div class="date-config">
                                <label>{{ t('routes.toDate') }}</label>

                               
                                <DatePicker
                                    v-model="toDate"
                                    showTime
                                    showIcon
                                    manualInput
                                    hourFormat="24"
                                    :dateFormat="getLocaleFormat()"
                                    :min-date="fromDate"
                                    :showOnFocus="false"
                                />

                                <div class="parameter-checkbox">
                                    <span>{{ t('routes.includeNullToDate') }}</span>

                                    <Checkbox
                                        v-model="includeNullsToDate"
                                        :binary="true"
                                    />
                                </div>
                            </div>

                        </div>
                        <PButton
                            v-if="showDataConfigButton()"
                            class="data-config-box-buttom"
                            :label="`${(!showConfig? t('routes.show') : t('routes.hide'))} ${t('routes.dataConfig')}`"
                            icon="pi pi-power-off"
                            :aria-label=" `${(!showConfig? t('routes.show') : t('routes.hide'))} ${t('routes.dataConfig')}`"
                            @click="toggleConfig()"
                        />
                        <div class="execute-panel">
                            <PButton
                                v-if="showLoadButton()"
                                class="execute-button"
                                severity="success"
                                :label="t('routes.load')"
                                icon="pi pi-power-off"
                                :aria-label="t('routes.load')"
                                @click="runInitialData()"
                            />
                        </div>
                    </div>
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
                    <div class="groups-table-box">
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
                                    {{ data.cohesion }}%
                                </template>
                            </Column>

                            <Column field="density" :header="t('routes.tables.density')">
                                <template #body="{ data }">
                                    {{ data.density }}
                                </template>
                            </Column>

                            <Column field="radius" :header="t('routes.tables.radius')">
                                <template #body="{ data }">
                                    {{ data.radius }}
                                </template>
                            </Column>
                            <Column field="area" :header="t('routes.tables.area')">
                                <template #body="{ data }">
                                    {{ data.area }}
                                </template>
                            </Column>
                        </DataTable>
                        <div class="groups-table-info"
                            v-if="showGroupDatatable()"
                        >
                            <PButton
                                v-if="showGroupDatatable()"
                                :label="`${(!showExtraMetrics? t('routes.show') : t('routes.hide'))} ${t('routes.extraMetrics')}`"
                                icon="pi pi-eye"
                                :aria-label=" `${(!showExtraMetrics? t('routes.show') : t('routes.hide'))} ${t('routes.extraMetrics')}`"
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
                    <div v-if="showModeSelector() ">   
                        <OptionsGrid
                            v-if="!showCatalog"
                            :items="modes"
                            :selected-ids="selectedModesId"
                            :on-select="selectMode"
                        />
                        <OptionsGrid
                            v-if="showCatalog"
                            :items="solvers"
                            :selected-ids="selectedSolversId"
                            :on-select="()=>{}"
                        />
                        <div class="groups-table-info">
                            <PButton
                            :label="`${(!showCatalog? t('routes.show') : t('routes.hide'))} ${t('routes.catalog')}`"
                            icon="pi pi-eye"
                            :aria-label="`${(!showCatalog? t('routes.show') : t('routes.hide'))} ${t('routes.catalog')}`"
                            @click="toggleCatalog()"
                        />
                        </div>
                    </div>
                </div>

                <div  v-if="showSolverSelector()" class="solver-selection-panel">
                    <div class="solver-config-card">
                        <DataTable
                            class="groups-table"
                            v-if="!showCatalog"
                            :value="slotRows"
                            stripedRows
                            rowHover
                            scrollable
                            scrollHeight="200px"
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
                    </div>
                    
                    <div class="solver-results-card">
                        <h3>{{t('routes.results')}}</h3>
                        <DataTable
                            class="groups-table"
                             v-if="!showCatalog"
                            :value="computedMetricsBySlot"
                            :rowClass="rowClass"
                            stripedRows
                            rowHover
                            scrollable
                            scrollHeight="250px"
                        >
                            <Column field="id" :header="t('routes.tables.slot')"/>

                            <Column field="solverType" :header="t('routes.tables.type')"/>

                            <Column field="solverDistance" :header="t('routes.tables.solverDistance')"/>

                            <Column field="aproxDistance" :header="t('routes.tables.aproxDistance')"/>

                            <Column field="aproxDuration" :header="t('routes.tables.aproxDuration')"/>

                        </DataTable>
                        <div class="execute-panel">
                            <PButton
                                    class="execute-button"
                                    severity="success"
                                    :label="t('routes.execute')"
                                    icon="pi pi-power-off"
                                    :aria-label="t('routes.execute')"
                                    @click="runSolvers()"
                                />
                        </div>
                    </div>

                   

                </div>
              
                
            </div>
            <div class="right-panel">
                <div ref="mapEl" class="map" />
            </div>
        </div>
        

        <div v-if="false">
            <PButton
                :label="t('pricing.nexts')"
                icon="pi pi-check-circle"
                :aria-label="t('pricing.nexts')"
                @click="allowNextPhases({maxPhase: 5})"
            />
        </div>
        <PButton
            v-if="false"
            :label="t('pricing.cancel')"
            icon="pi pi-check-circle"
            :aria-label="t('pricing.cancel')"
            @click="runInitialData()"
        />
    </div>
</template>

<style scoped src="./RoutesView.css"></style>