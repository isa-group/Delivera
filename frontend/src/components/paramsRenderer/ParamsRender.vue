<script setup>
import { ParamType } from '@/composables/useParamBuilder';
import { InputNumber, Select, Slider, ToggleSwitch } from 'primevue';
import { onMounted, onUnmounted, reactive, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { toRaw } from 'vue'

const { t, locale } = useI18n() 


const props = defineProps({
  params: { type: Array, required: true }
})

const values = reactive({})


const emit = defineEmits([
    'change',
])

/*
emit(
    'change',
    structuredClone(values)
)*/

watch(
    values,
    () => {
        emit(
        'change',
        structuredClone(toRaw(values))
    )},
    { deep: true }
)

watch(
    () => props.params,
    params => {
        params.forEach(param => {
            values[param.name] = param.value
        })
    },
    { immediate: true }
)



function reloadDefault(param) {
    values[param.name] = param.default
}

function supportsSlider(param) {

    const isNumeric =
        param.type === ParamType.INTEGER ||
        param.type === ParamType.NUMBER

    const hasRange =
        (param.minimum !== null && param.minimum !== undefined)  &&
        (param.maximum !== null && param.maximum !== undefined)

    return isNumeric && hasRange

}

</script>
<template>
    <div class="param-container">
        <div
            :key="param.name"
            v-for="param in params"
            class="param-card"
            :class="!param.enabled ? 'disabled' : ''"
        >   
            <h4 class="param-header">{{param?.displayName}} </h4> 
            <div 
                v-if="param.enum"
            >
                <Select
                    class="param-enum"
                    v-if="param.enum"
                    v-model="values[param.name]"
                    :options="param.enum || []"
                    :disabled="!param.enabled"
                    :placeholder="'Select'"
                    show-clear
                />
            </div>
        
            <div 
                v-if="param.type == 'BOOLEAN'"
                
            >   
                <div class="param-info">
                    <span>
                        {{t("common.state")}}: {{t(`common.${values[param.name] ? 'active' : 'deactive'}`)}}
                    </span>
                </div>
                <div class="param-value-box">
                    <ToggleSwitch
                        v-model="values[param.name]"
                        :disabled="!param.enabled"
                    />
                </div>
                
            </div>
            <div v-if="param.type == 'INTEGER' || param.type == 'NUMBER' " >
                <div class="param-info">
                    <div>
                        <span>{{t('common.defaultValue')}}: {{param.default}}</span> 
                        <i 
                            class="pi pi-replay param-reload" 
                            @click="reloadDefault(param)"
                        />
                    </div>
                    <span>
                        {{t('common.valueType')}}: {{t(`common.${param.type.toLowerCase()}Type`)}}
                    </span>
                </div>
                <div class="param-info param-slider">
                    <p v-if="param.minimum !== null && param.minimum !== undefined">
                        min: {{param.minimum}}
                    </p>
                    <p v-if="param.maximum !== null && param.maximum !== undefined">
                        max: {{param.maximum}}
                    </p>   
                </div>
                <div class="param-value-box">
                    <Slider
                        v-if="supportsSlider(param)"
                        class="param-slider"
                        v-model="values[param.name]"
                        :min="param.minimum"
                        :max="param.maximum"
                        :step="param.step ?? (
                            param.type === 'INTEGER'
                                ? 1
                                : 0.01
                        )"
                        :disabled="!param.enabled"
                    />

                    <InputNumber
                        class="param-input"
                        :class="supportsSlider(param) ? '': 'only'"
                        v-model="values[param.name]"
                        :useGrouping="false"
                        :min="param.minimum"
                        :max="param.maximum"
                        :locale="locale"
                        :step="param.step ?? (
                            param.type === 'INTEGER'
                                ? 1
                                : 0.01
                        )"
                        :maxFractionDigits="
                            param.type === 'INTEGER'
                                ? 0
                                : 6
                        "
                    />
                </div>
            </div>
            

        </div>
    </div>
</template>
<style scoped src="./ParamsRender.css"></style>

