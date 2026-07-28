<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { useFormatDate } from '@/composables/useFormatDate'
import { useAuthStore } from '@/stores/auth'
import { buildDeleteConfirmOptions } from '@/composables/useConfirmDelete'
import { useServices } from '@/composables/useServices'
import { useResourceListFromService } from '@/composables/useResourceListFromService'

const { t } = useI18n()
const auth = useAuthStore()
const { formatDate } = useFormatDate()
const router = useRouter()
const api = useServices("data-service")
const confirm = useConfirm()
const { items: vehicles, loading, error } = useResourceListFromService('/vehicles',"data-service")

const deleteError = ref('')
const filterText = ref('')

const filtered = computed(() => {
  if (!filterText.value) return vehicles.value
  const q = filterText.value.toLowerCase()
  return vehicles.value.filter(v =>
    v.plate.toLowerCase().includes(q) ||
    (v.depotName && v.depotName.toLowerCase().includes(q))
  )
})

async function deleteVehicle(e, id) {
  e.stopPropagation()
  deleteError.value = ''
  confirm.require(buildDeleteConfirmOptions(t, t('vehicles.deleteConfirm'), async () => {
    const res = await api.del(`/vehicles/${id}`)
    if (res.ok) {
      vehicles.value = vehicles.value.filter(v => v.id !== id)
    } else {
      deleteError.value = t('error.connection')
    }
  }))
}
</script>

<template>
  <main class="surface-card card-full" aria-labelledby="vehicles-page-title">
    <div class="list-header">
      <h1 id="vehicles-page-title">{{ t('vehicles.title') }}</h1>
      <PButton
        v-if="auth.isCompanyAdmin"
        :label="t('vehicles.new')"
        icon="pi pi-plus"
        :aria-label="t('vehicles.new')"
        @click="router.push('/vehicles/new')"
      />
    </div>

    <div class="filters-bar" role="search" aria-label="vehicles-filter">
      <label for="vehicles-filter-input" class="filters-label">{{ t('common.filters') }}</label>
      <input
        id="vehicles-filter-input"
        v-model="filterText"
        :placeholder="t('vehicles.searchPlaceholder')"
        :aria-label="t('vehicles.searchPlaceholder')"
        class="filter-search"
        type="search"
      />
    </div>

    <div role="alert" aria-live="polite">
      <PMessage v-if="error" severity="error" :closable="false">{{ error }}</PMessage>
      <PMessage v-if="deleteError" severity="error" :closable="false">{{ deleteError }}</PMessage>
    </div>

    <div class="list-scroll">
    <DataTable
      :value="filtered"
      :loading="loading"
      paginator
      :rows="10"
      striped-rows
      row-hover
      aria-label="vehicles-table"
      @row-click="e => router.push(`/vehicles/${e.data.id}`)"
    >
      <template #empty>
        <EmptyState icon="pi-truck" :message="t('vehicles.empty')">
          <PButton
            v-if="auth.isCompanyAdmin"
            :label="t('vehicles.new')"
            icon="pi pi-plus"
            size="small"
            :aria-label="t('vehicles.new')"
            @click="router.push('/vehicles/new')"
          />
        </EmptyState>
      </template>
      <Column field="plate" :header="t('vehicles.plate')" style="font-weight:600" />
      <Column :header="t('vehicles.capacity')" style="width:130px">
        <template #body="{ data }">
          <span class="capacity-badge" :aria-label="String(data.capacity)">{{ data.capacity }}</span>
        </template>
      </Column>
      <Column field="depotName" :header="t('vehicles.depot')" />
      <Column :header="t('orders.date')" style="width:130px">
        <template #body="{ data }">
          <time :datetime="data.createdAt">{{ formatDate(data.createdAt) }}</time>
        </template>
      </Column>
      <Column v-if="auth.isCompanyAdmin" style="width:80px;padding:0">
        <template #body="{ data }">
          <div class="row-actions">
            <PButton icon="pi pi-pencil" text rounded size="small" class="action-btn" :aria-label="`${t('vehicles.edit')}: ${data.plate}`"
                     v-tooltip.top="t('vehicles.edit')"
                     @click.stop="router.push(`/vehicles/${data.id}/edit`)" />
            <PButton icon="pi pi-trash" text rounded severity="danger" size="small" class="action-btn" :aria-label="`${t('common.delete')}: ${data.plate}`"
                     v-tooltip.top="t('common.delete')"
                     @click="deleteVehicle($event, data.id)" />
          </div>
        </template>
      </Column>
    </DataTable>
    </div>
  </main>
</template>

<style scoped src="./VehiclesView.css"></style>
