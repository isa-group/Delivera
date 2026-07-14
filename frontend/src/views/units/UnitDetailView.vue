<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useFormatDate } from '@/composables/useFormatDate'
import { createMap, ownUnitIcon } from '@/composables/useDeliveraMap'
import L from 'leaflet'
import { useServices } from '@/composables/useServices'

const { t } = useI18n()
const { formatDate } = useFormatDate()
const route = useRoute()
const router = useRouter()
const dataApi = useServices("data-service")
const auth = useAuthStore()

const unit = ref(null)
const loading = ref(false)
const error = ref('')
const mapEl = ref(null)
let map = null

const isAdmin = computed(() => auth.role === 'COMPANY_ADMIN')
const hasCoords = computed(() => unit.value?.latitude != null && unit.value?.longitude != null)

function initMap() {
  if (!mapEl.value || !hasCoords.value) return
  if (map) { map.remove(); map = null }
  const lat = Number.parseFloat(unit.value.latitude)
  const lng = Number.parseFloat(unit.value.longitude)
  map = createMap(mapEl.value)
  map.invalidateSize()
  map.setView([lat, lng], 15)
  L.marker([lat, lng], { icon: ownUnitIcon() })
    .bindPopup(`<strong>${unit.value.name}</strong><br><small>${t('units.' + unit.value.type)}</small>`)
    .addTo(map)
    .openPopup()
}

async function load() {
  loading.value = true
  try {
    const res = await dataApi.get(`/units/${route.params.id}`)
    if (res.ok) unit.value = await res.json()
    else if (res.status === 404 || res.status === 400) error.value = t('units.notFound')
    else error.value = t('error.connection')
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  await nextTick()
  initMap()
})

onBeforeRouteLeave(() => { if (map) { map.remove(); map = null } })

onUnmounted(() => { if (map) { map.remove(); map = null } })
</script>

<template>
  <div class="surface-card unit-detail-page card-full">
    <div v-if="loading" class="loading-state" style="padding:48px;text-align:center">
      <i class="pi pi-spin pi-spinner" style="font-size:24px" />
    </div>

    <PMessage v-else-if="error" severity="error" :closable="false" style="margin:24px">{{ error }}</PMessage>

    <div v-else-if="unit" class="unit-detail-split">
      <!-- Panel izquierdo: info -->
      <div class="unit-detail-panel">
        <div class="detail-actions">
          <PButton
            type="button"
            text
            severity="secondary"
            icon="pi pi-arrow-left"
            @click="router.push('/units')"
          />
          <PButton
            v-if="isAdmin"
            type="button"
            icon="pi pi-pencil"
            severity="secondary"
            outlined
            size="small"
            :label="t('units.edit')"
            @click="router.push(`/units/${unit.id}/edit`)"
          />
        </div>

        <h1 class="detail-title">{{ unit.name }}</h1>

        <div class="badge-group">
          <span class="info-label">{{ t('fields.type') }}</span>
          <PTag :value="t(`units.${unit.type}`)" />
        </div>

        <div class="info-grid">
          <div class="info-item info-item--full">
            <span class="info-label">{{ t('fields.address') }}</span>
            <span class="info-value">{{ unit.address || '—' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">{{ t('fields.latitude') }} / {{ t('fields.longitude') }}</span>
            <span class="info-value">
              {{ hasCoords ? `${unit.latitude}, ${unit.longitude}` : t('units.noCoords') }}
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">{{ t('orders.date') }}</span>
            <span class="info-value">{{ formatDate(unit.createdAt) ?? '—' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">{{ t('settings.defaultPriority') }}</span>
            <span class="info-value">
              <template v-if="unit.defaultPriority">
                {{ t('orders.priority.' + unit.defaultPriority) }}
                <span class="priority-override-badge" :title="t('units.defaultPriority')">{{ t('units.priorityOverridden') }}</span>
              </template>
              <template v-else>
                <em>{{ t('units.priorityInheritedFromCompany') }}</em>
              </template>
            </span>
          </div>
        </div>

        <!-- Trabajadores asignados -->
        <div v-if="isAdmin" class="workers-section">
          <h3>{{ t('units.workers') }}</h3>
            <div class="buttons-section">
              <RouterLink
                :to="{
                  path: `/units/${unit.id}/assign-workers`,
                  query: {
                    name: unit.name
                  }
                }"
              >
                <PButton :label="t('units.assignWorkers')" icon="pi pi-plus" severity="secondary" size="small" class="assign-btn" />
              </RouterLink>
              <RouterLink
                  :to="{
                    path: `/units/${unit.id}/workers`,
                    query: {
                      name: unit.name
                    }
                  }"
                >
                

                <PButton :label="t('units.workers')" icon="" severity="secondary" size="small" class="assign-btn" />
              </RouterLink>
            </div>
            
          
        </div>
      </div>

      <!-- Panel derecho: mapa -->
      <div class="unit-map-panel">
        <div v-if="hasCoords" ref="mapEl" class="unit-map-el" />
        <div v-else class="map-no-coords">
          <i class="pi pi-map-marker" style="font-size:28px;color:#cbd5e1;margin-bottom:8px" />
          <p>{{ t('units.noCoords') }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped src="./UnitDetailView.css"></style>
