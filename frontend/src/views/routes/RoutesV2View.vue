<script setup>
import { useRoutes } from '@/composables/useRoutes';
import { Checkbox, Column, DataTable, DatePicker, Select, Step, StepList, Stepper } from 'primevue';
import { useI18n } from 'vue-i18n';
import OptionsGrid from '../../components/optionsGrid/OptionsGrid.vue';
import { getLocaleFormat } from '@/composables/useDateRange.js';
import QuotaUsage from '@/components/quotaUsage/QuotaUsage.vue';
import FloatingPanel from '@/components/windows/FloatingPanel.vue';
import { ref } from 'vue';
import DockBar  from '@/components/windows/DockBar.vue';
import { useSmartTranslation } from '@/composables/useSmartTranslation.js';
import ParamsRender from '@/components/paramsRenderer/ParamsRender.vue';
import { useParamBuilder } from '@/composables/useParamBuilder.js';
import ErrorCard from '@/components/errorCard/ErrorCard.vue';



const { t} = useI18n() 
const { getDisplayName} = useSmartTranslation()
const {build} = useParamBuilder()
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
        solverParams, // TODO :)
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
        featureContexts,
        allSolvers,
        globlaErrors,
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
        updateSolverValues
        analyzingViability,
        viabilitySummary,
        runViabilityAnalysis,


    } = useRoutes()

    

    function rowClass(data) {
        return data.best
            ? 'best-solver-row'
            : '';
    }

   
    const errorPanelPosition = {
        x: (window.innerWidth - 600) / 2,
        y: (window.innerHeight - 400) / 2
    }

</script>

<template>
    <div class="routes-workspace">
        <div class="right-panel">
            <div ref="mapEl" class="map" />
        </div>
        
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


        <DockBar>
            <FloatingPanel
                :title="t('routes.routing')"
                :panel-id="'Routing'"
                :display-name="t('routes.routing')"
                :icon="'pi pi-box'"
                :initial-x="20"
                :initial-y="80"
                :start-minimized="false"
            > 
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
                            <div class="select-benchmark">
                                <Select
                                v-if="showBenchmarksSection()"
                                v-model="selectedBenchmark"
                                :options="possibleBenchmarks"
                                optionLabel="label"
                                optionValue="value"
                                @click.stop
                                />
                            </div>
                            
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
                        
                            <div class="groups-table-info">
                                <PButton
                                :label="`${(!showCatalog? t('routes.show') : t('routes.hide'))} ${t('routes.catalog')}`"
                                icon="pi pi-eye"
                                :aria-label="`${(!showCatalog? t('routes.show') : t('routes.hide'))} ${t('routes.catalog')}`"
                                @click="toggleCatalog()"
                            />
                                <PButton
                                    :label="t('routes.quantumViability.analyze')"
                                    icon="pi pi-microchip"
                                    severity="secondary"
                                    :loading="analyzingViability"
                                    :aria-label="t('routes.quantumViability.analyze')"
                                    @click="runViabilityAnalysis()"
                                />
                                <small v-if="viabilitySummary.analyzed" class="quantum-viability-summary">
                                    {{ t('routes.quantumViability.summary', viabilitySummary) }}
                                </small>
                                <small v-if="viabilitySummary.noDepot" class="quantum-viability-summary">
                                    {{ t('routes.quantumViability.noDepotSummary', viabilitySummary) }}
                                </small>
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
            </FloatingPanel>
            <FloatingPanel
                :title="t('routes.errors')"
                :panel-id="'Errors'"
                :display-name="t('routes.errors')"
                :icon="'pi pi-exclamation-triangle'"
                :initial-x="errorPanelPosition?.x"
                :initial-y="errorPanelPosition?.y"
            >
               <ErrorCard :errors="globlaErrors" />

            </FloatingPanel>
            <FloatingPanel
                v-if="allSolvers.length"
                :title="t('routes.catalog')"
                :panel-id="'Catalog'"
                :display-name="t('routes.catalog')"
                :icon="'pi pi-shopping-cart'"
                :initial-x="620"
                :initial-y="140"
            >
                <OptionsGrid   
                    :items="allSolvers"
                    :selected-ids="selectedSolversId"
                    :on-select="()=>{}"
                />
            </FloatingPanel>
            <FloatingPanel
                :title="t('routes.quota')"
                :panel-id="'Quota'"
                :display-name="t('routes.quota')"
                :icon="'pi pi-gauge'"
                :initial-x="420"
                :initial-y="140"
            >
                <QuotaUsage  :features="featureContexts"/>

            </FloatingPanel>
            <FloatingPanel
                :title="getDisplayName('Config')"
                :panel-id="'Config'"
                :display-name="getDisplayName('Config')"
                :icon="'pi pi-cog'"
                :initial-x="300"
                :initial-y="80"
                :start-minimized="false"
            >   
                <div  
                    v-if="solverParams.length > 0"
                >
                    <div class="solver-config-card" :key="solver"  v-for="[solver,params] in solverParams">
                        <div>
                            <h2>{{t(`routes.solvers.${solver}.name`)}}</h2>
                        </div>
                        <ParamsRender
                            :key="solver"
                            :params="params"
                            @change="newValues => updateSolverValues(solver, newValues)"
                        />

                        <div v-if="params.length == 0">
                            {{"EMPTY"}}
                        </div>
                    </div>
                </div>
                
        
            </FloatingPanel>
        </DockBar> 
    </div>
</template>

<style scoped src="./RoutesV2View.css"></style>