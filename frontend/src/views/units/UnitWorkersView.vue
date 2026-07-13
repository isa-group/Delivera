<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { useServices } from '@/composables/useServices'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const deliveraApi = useServices("delivera-service")
const dataApi = useServices("data-service")
const auth = useAuthStore()

const isAdmin = computed(() => auth.role === 'COMPANY_ADMIN')
const allWorkers = ref([])
const loading = ref(false)
const error = ref('')
const workerActionError = ref('')
const unitName = computed(() => route.query.name)

async function unassignWorker(workerId) {
  workerActionError.value = ''
  try {
    const res = await dataApi.del(`/units/${route.params.id}/workers/${workerId}`)
    if (!res.ok) {
      const d = await res.json(); workerActionError.value = dataApi.translateError(d, 'error.saveFailed')
      return
    }
    const workerIds = await res.json()

    if (workerIds.length === 0) {
      allWorkers.value = []
      return
    }

    const workersRes = await deliveraApi.post(
      '/workers/required',
      workerIds
    )

    if (!workersRes.ok) {
      error.value = t('error.connection')
      return
    }

    allWorkers.value = await workersRes.json()

  } catch { workerActionError.value = t('error.connection') }
}




async function load() {
  loading.value = true

  try {
    const unitWorkersRes = await dataApi.get(
      `/units/${route.params.id}/workers`
    )

    if (!unitWorkersRes.ok) {
      error.value = t('error.connection')
      return
    }

    const workerIds = await unitWorkersRes.json()

    if (workerIds.length === 0) {
      allWorkers.value = []
      return
    }

    const workersRes = await deliveraApi.post(
      '/workers/required',
      workerIds
    )

    if (!workersRes.ok) {
      error.value = t('error.connection')
      return
    }

    allWorkers.value = await workersRes.json()

  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}



onMounted(load)
</script>

<template>
  <div class="surface-card card-wide">
    <PButton type="button" text severity="secondary" icon="pi pi-arrow-left" class="detail-back-btn"
      @click="router.push(`/units/${route.params.id}`)" />

      <div v-if="isAdmin" class="workers-section">
        <h1 v-if="unitName" class="detail-title">{{ unitName }}</h1>
          <div class="assign-button">
            <RouterLink
              :to="{
                path: `/units/${route.params.id}/assign-workers`,
                query: {
                  name: unitName
                }
              }"
            >
              

              <PButton :label="t('units.assignWorkers')" icon="pi pi-plus" severity="secondary" size="small" class="assign-btn" />
            </RouterLink>
          </div>
          
          <h3>{{ t('units.workers') }}</h3>
          <PMessage v-if="workerActionError" severity="error" :closable="false" class="form-message">{{ workerActionError }}</PMessage>
          <div v-if="allWorkers && allWorkers.length" class="worker-list">
            <div v-for="w in allWorkers" :key="w.id" class="worker-row">
              <span class="worker-info">
                <span class="worker-name">{{ w.firstName }} {{ w.lastName }}</span>
                <span class="worker-email">{{ w.email }}</span>
              </span>
              <PTag :value="t('workers.roles.' + w.role)" severity="info" />
              <PButton icon="pi pi-times" text rounded severity="danger" size="small" :aria-label="t('common.delete')"
                       v-tooltip.top="t('common.delete')" @click="unassignWorker(w.id)" />
            </div>
          </div>
          <p v-else class="empty-workers">{{ t('units.noWorkers') }}</p>
    </div>
  </div>
</template>

<style scoped src="./UnitWorkersView.css"></style>
