<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { useApi } from '@/composables/useApi'
import { buildDeleteConfirmOptions } from '@/composables/useConfirmDelete'
import { useAuthStore } from '@/stores/auth'
import { useResourceList } from '@/composables/useResourceList'
import EmptyState from '@/components/EmptyState.vue'
import L from 'leaflet'
import { createMap, addMarker, clusterOptions, fitBounds } from '@/composables/useDeliveraMap'
import { MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION } from '@/constants/map'

const { t } = useI18n()
const router = useRouter()
const api = useApi()
const confirm = useConfirm()
const auth = useAuthStore()
const { items: units, loading, error } = useResourceList('/units')

const deleteError = ref('')
const filterText = ref('')
const filterType = ref('ALL')
const mapEl = ref(null)
let map = null
let clusterGroup = null

const typeOptions = computed(() => [
  { label: t('units.filterAll'), value: 'ALL' },
  { label: t('units.WAREHOUSE'), value: 'WAREHOUSE' },
  { label: t('units.STORE'), value: 'STORE' },
  { label: t('units.FACTORY'), value: 'FACTORY' },
  { label: t('units.LOGISTICS_CENTER'), value: 'LOGISTICS_CENTER' },
])

const filtered = computed(() => {
  return units.value.filter(u => {
    if (filterType.value !== 'ALL' && u.type !== filterType.value) return false
    if (filterText.value && !u.name.toLowerCase().includes(filterText.value.toLowerCase())) return false
    return true
  })
})

function initMap() {
  if (!mapEl.value) return
  if (map) { map.remove(); map = null }
  map = createMap(mapEl.value)
  map.setView(MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION)
  updateMapMarkers()
}

function updateMapMarkers() {
  if (!map) return
  if (clusterGroup) { map.removeLayer(clusterGroup); clusterGroup = null }

  const items = filtered.value.filter(u => u.latitude != null && u.longitude != null)
  if (items.length === 0) return

  clusterGroup = L.markerClusterGroup(clusterOptions())
  const bounds = []
  items.forEach(u => {
    const lat = Number.parseFloat(u.latitude)
    const lng = Number.parseFloat(u.longitude)
    const marker = addMarker(map, {
      id: u.id,
      lat, lon: lng,
      kind: 'OWN_UNIT',
      title: u.name,
      subtitle: `${t('units.' + u.type)}${u.address ? ' · ' + u.address : ''}`,
      actionLabel: t('units.detail'),
      navigateTo: `/units/${u.id}`,
      router,
    })
    clusterGroup.addLayer(marker)
    bounds.push([lat, lng])
  })
  map.addLayer(clusterGroup)
  fitBounds(map, bounds)
}

// Re-renderizar marcadores al filtrar
watch(filtered, () => { if (map) updateMapMarkers() })

async function deleteUnit(e, id) {
  e.stopPropagation()
  deleteError.value = ''
  confirm.require(buildDeleteConfirmOptions(t, t('units.deleteConfirm'), async () => {
    const res = await api.del(`/units/${id}`)
    if (res.ok) {
      units.value = units.value.filter(u => u.id !== id)
    } else {
      deleteError.value = res.status === 409
        ? t('units.deleteActiveOrders')
        : t('error.connection')
    }
  }))
}

onMounted(async () => {
  await nextTick()
  initMap()
})

onBeforeRouteLeave(() => { if (map) { map.remove(); map = null } })

onUnmounted(() => { if (map) { map.remove(); map = null } })

// Re-init map cuando lleguen unidades
watch(units, async () => {
  await nextTick()
  if (map) updateMapMarkers()
  else initMap()
})
</script>

<template>
  <div class="surface-card units-page card-full">
    <!-- Split layout -->
    <div class="units-split">
      <!-- Panel izquierdo: filtros + lista -->
      <div class="units-list-panel">
        <div class="list-header">
          <h1>{{ t('units.title') }}</h1>
          <div class="header-actions">
            <PButton
              v-if="auth.isCompanyAdmin"
              :label="t('units.new')"
              icon="pi pi-plus"
              @click="router.push('/units/new')"
            />
          </div>
        </div>

        <div class="filters-wrapper">
          <span class="filters-label">{{ t('common.filters') }}</span>
          <div class="filters-box">
            <div class="filter-row">
              <label for="units-filter-name" class="filter-group-label">{{ t('fields.unitName') }}</label>
              <input
                id="units-filter-name"
                v-model="filterText"
                :placeholder="t('fields.unitName') + '...'"
                class="filter-search"
                type="text"
              />
            </div>
            <div class="filter-row">
              <label for="units-filter-type" class="filter-group-label">{{ t('fields.type') }}</label>
              <PSelect
                input-id="units-filter-type"
                v-model="filterType"
                :options="typeOptions"
                option-label="label"
                option-value="value"
                class="filter-select"
              />
            </div>
          </div>
        </div>

        <PMessage v-if="error" severity="error" :closable="false">{{ error }}</PMessage>
        <div v-if="deleteError" class="delete-error-wrapper">
          <PMessage severity="error" :closable="false">{{ deleteError }}</PMessage>
        </div>

        <div class="list-scroll">
        <DataTable
          :value="filtered"
          :loading="loading"
          paginator
          :rows="8"
          striped-rows
          row-hover
          @row-click="e => router.push(`/units/${e.data.id}`)"
        >
          <template #empty>
            <EmptyState icon="pi-warehouse" :message="t('units.empty')">
              <PButton
                v-if="auth.isCompanyAdmin"
                :label="t('units.new')"
                icon="pi pi-plus"
                size="small"
                @click="router.push('/units/new')"
              />
            </EmptyState>
          </template>
          <Column :header="t('fields.type')" style="width:180px">
            <template #body="{ data }">
              <PTag :value="t(`units.${data.type}`)" />
            </template>
          </Column>
          <Column field="name" :header="t('fields.unitName')" />
          <Column field="address" :header="t('fields.address')" />
          <Column v-if="auth.isCompanyAdmin" style="width:80px;padding:0">
            <template #body="{ data }">
              <div class="row-actions">
                <PButton icon="pi pi-pencil" text rounded size="small" class="action-btn" :aria-label="t('units.edit')"
                         v-tooltip.top="t('units.edit')"
                         @click.stop="router.push(`/units/${data.id}/edit`)" />
                <PButton icon="pi pi-times" text rounded severity="danger" size="small" class="action-btn" :aria-label="t('common.delete')"
                         v-tooltip.top="t('common.delete')"
                         @click="deleteUnit($event, data.id)" />
              </div>
            </template>
          </Column>
        </DataTable>
        </div>
      </div>

      <!-- Panel derecho: mapa -->
      <div class="units-map-panel">
        <div ref="mapEl" class="units-map-el" />
        <div v-if="filtered.filter(u => u.latitude).length === 0 && !loading" class="map-no-coords">
          {{ t('units.noCoords') }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped src="./UnitsView.css"></style>
