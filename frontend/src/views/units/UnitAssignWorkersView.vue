<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useApi } from '@/composables/useApi'
import { useServices } from '@/composables/useServices'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const api = useApi()
const dataApi = useServices("data-service")

const unit = ref(null)
const allWorkers = ref([])
const loading = ref(false)
const error = ref('')
const toggleError = ref('')
const togglingIds = ref(new Set())


const unitName = computed(() => route.query.name)
const assignedIds = computed(() => new Set((unit.value || [])))

async function load() {
  loading.value = true
  try {
    const [unitRes, workersRes] = await Promise.all([
      dataApi.get(`/units/${route.params.id}/workers`),
      api.get('/workers'),
    ])
    if (unitRes.ok) unit.value = await unitRes.json()
    else error.value = t('error.connection')
    if (workersRes.ok) allWorkers.value = await workersRes.json()
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}

async function toggle(worker) {
  if (togglingIds.value.has(worker.id)) return
  toggleError.value = ''
  togglingIds.value.add(worker.id)
  try {
    const res = assignedIds.value.has(worker.id)
      ? await dataApi.del(`/units/${route.params.id}/workers/${worker.id}`)
      : await dataApi.post(`/units/${route.params.id}/workers`,{workerId: worker.id, userId: worker.userId })
    if (res.ok) unit.value = await res.json()
    else toggleError.value = t('error.saveFailed')
  } catch {
    toggleError.value = t('error.connection')
  } finally {
    togglingIds.value.delete(worker.id)
  }
}

onMounted(load)
</script>

<template>
  <div class="surface-card card-wide">
    <PButton type="button" text severity="secondary" icon="pi pi-arrow-left" class="detail-back-btn"
      @click="router.push(`/units/${route.params.id}`)" />

    <h1>{{ t('units.assignWorkers') }}</h1>
    <p v-if="unitName" class="subtitle">{{unitName}}</p>

    <PMessage v-if="error" severity="error" :closable="false">{{ error }}</PMessage>
    <PMessage v-if="toggleError" severity="error" :closable="false">{{ toggleError }}</PMessage>

    <div v-if="!loading && allWorkers.length" class="worker-list">
      <div
        v-for="w in allWorkers"
        :key="w.id"
        class="worker-row"
        :class="{ 'worker-row--busy': togglingIds.has(w.id) }"
        @click="toggle(w)"
      >
        <i v-if="togglingIds.has(w.id)" class="pi pi-spin pi-spinner worker-spinner" />
        <PCheckbox v-else :model-value="assignedIds.has(w.id)" :binary="true" @click.stop="toggle(w)" />
        <span class="worker-info">
          <span class="worker-name">{{ w.firstName }} {{ w.lastName }}</span>
          <span class="worker-email">{{ w.email }}</span>
        </span>
        <PTag :value="t('workers.roles.' + w.role)" severity="secondary" />
      </div>
    </div>
    <p v-else-if="!loading" class="empty">{{ t('units.noAssignableWorkers') }}</p>
  </div>
</template>

<style scoped src="./UnitAssignWorkersView.css"></style>
