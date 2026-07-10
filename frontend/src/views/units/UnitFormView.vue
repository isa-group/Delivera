<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useApi } from '@/composables/useApi'
import { useUnitForm } from '@/composables/useUnitForm'
import { buildPriorityOptions } from '@/composables/useOrderPriority'
import { useCompanySettings, canUnitOverridePriority } from '@/composables/useCompanySettings'
import { MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION } from '@/constants/map'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
import { useServices } from '@/composables/useServices'

delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({ iconUrl: markerIcon, iconRetinaUrl: markerIcon2x, shadowUrl: markerShadow })

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const api = useApi()
const dataApi = useServices("data-service")

const unitId = computed(() => route.params.id || null)
const isEdit = computed(() => !!unitId.value)

const { name, unitType, address, latitude, longitude, defaultPriority, error, success, loading, errors, invalids, submitUnit, loadFromUnit } = useUnitForm()
const priorityOptions = buildPriorityOptions(t)
const priorityLockedByCompany = ref(false)
const { load: loadCompanySettings } = useCompanySettings()
const loadError = ref('')

const mapEl = ref(null)
let map = null
let marker = null
const geocoding = ref(false)
const geocodeError = ref('')
const locationLocked = ref(false) // true si la unidad tiene pedidos activos

const UNIT_TYPES = ['WAREHOUSE', 'STORE', 'FACTORY', 'LOGISTICS_CENTER']
const unitTypeOptions = computed(() =>
  UNIT_TYPES.map(type => ({ label: t(`units.${type}`), value: type }))
)

function initMap() {
  if (!mapEl.value || map) return

  const initLat = latitude.value ? Number.parseFloat(latitude.value) : MAP_DEFAULT_CENTER[0]
  const initLng = longitude.value ? Number.parseFloat(longitude.value) : MAP_DEFAULT_CENTER[1]
  const initZoom = latitude.value ? 13 : MAP_DEFAULT_ZOOM_REGION

  map = L.map(mapEl.value).setView([initLat, initLng], initZoom)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map)

  if (latitude.value && longitude.value) {
    placeMarker(Number.parseFloat(latitude.value), Number.parseFloat(longitude.value))
  }

  // Click en el mapa para establecer coordenadas (solo si no está bloqueado)
  map.on('click', async (e) => {
    if (locationLocked.value) return
    const { lat, lng } = e.latlng
    latitude.value = lat.toFixed(6)
    longitude.value = lng.toFixed(6)
    placeMarker(lat, lng)
    await reverseGeocode(lat, lng)
  })
}

function buildingIcon() {
  return L.divIcon({
    html: `<div style="width:32px;height:32px;border-radius:50%;background:#7c3aed;border:2.5px solid white;box-shadow:0 2px 6px rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center"><i class="pi pi-building" style="color:white;font-size:16px"></i></div>`,
    className: '', iconSize: [32, 32], iconAnchor: [16, 16], popupAnchor: [0, -18],
  })
}

function placeMarker(lat, lng) {
  if (marker) map.removeLayer(marker)
  marker = L.marker([lat, lng], { draggable: !locationLocked.value, icon: buildingIcon() }).addTo(map)
  marker.on('dragend', async (e) => {
    if (locationLocked.value) return
    const pos = e.target.getLatLng()
    latitude.value = pos.lat.toFixed(6)
    longitude.value = pos.lng.toFixed(6)
    await reverseGeocode(pos.lat, pos.lng)
  })
}

async function geocodeAddress() {
  if (!address.value.trim()) return
  geocoding.value = true
  geocodeError.value = ''
  try {
    const q = encodeURIComponent(address.value.trim())
    const res = await fetch(
      `https://nominatim.openstreetmap.org/search?q=${q}&format=json&limit=1`,
      { headers: { 'Accept-Language': 'es' }, signal: AbortSignal.timeout(5000) }
    )
    if (res.ok) {
      const data = await res.json()
      if (data.length > 0) {
        const { lat, lon } = data[0]
        latitude.value = Number.parseFloat(lat).toFixed(6)
        longitude.value = Number.parseFloat(lon).toFixed(6)
        if (map) {
          placeMarker(Number.parseFloat(lat), Number.parseFloat(lon))
          map.setView([Number.parseFloat(lat), Number.parseFloat(lon)], 15)
        }
      } else {
        geocodeError.value = 'units.geocodeNotFound'
      }
    }
  } catch {
    // silencioso
  } finally {
    geocoding.value = false
  }
}

async function reverseGeocode(lat, lng) {
  geocoding.value = true
  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lng}&format=json`,
      { headers: { 'Accept-Language': 'es' }, signal: AbortSignal.timeout(5000) }
    )
    if (res.ok) {
      const data = await res.json()
      if (data.display_name) {
        address.value = data.display_name
      }
    }
  } catch {
    // silencioso
  } finally {
    geocoding.value = false
  }
}

// Cuando cambien lat/lon manualmente, mover el marcador
watch([latitude, longitude], ([lat, lng]) => {
  if (!map || !lat || !lng) return
  const parsedLat = Number.parseFloat(lat)
  const parsedLng = Number.parseFloat(lng)
  if (Number.isNaN(parsedLat) || Number.isNaN(parsedLng)) return
  placeMarker(parsedLat, parsedLng)
  map.setView([parsedLat, parsedLng], map.getZoom() < 10 ? 13 : map.getZoom())
})

async function loadUnit(id) {
  if (!id) return
  loadError.value = ''
  try {
    const [unitRes, ordersRes] = await Promise.all([
      dataApi.get(`/units/${id}`),
      api.get('/orders'),
    ])
    if (!unitRes.ok) { loadError.value = t('error.connection'); return }
    const unit = await unitRes.json()
    loadFromUnit(unit)
    if (ordersRes.ok) {
      const orders = await ordersRes.json()
      locationLocked.value = orders.some(o => o.originId === id || o.destinationId === id)
    }
    // Actualizar el mapa con la posición cargada
    if (map && unit.latitude && unit.longitude) {
      const lat = Number.parseFloat(unit.latitude)
      const lng = Number.parseFloat(unit.longitude)
      placeMarker(lat, lng)
      map.setView([lat, lng], 13)
    }
  } catch {
    loadError.value = t('error.connection')
  }
}

watch(unitId, (newId) => loadUnit(newId))
onMounted(async () => {
  // Iniciar mapa primero, luego cargar datos
  await new Promise(r => setTimeout(r, 50)) // aguardar el DOM
  initMap()
  // Cargar lock de prioridad de la empresa para deshabilitar el campo si procede
  const s = await loadCompanySettings()
  priorityLockedByCompany.value = !canUnitOverridePriority(s)
  await loadUnit(unitId.value)
})

onBeforeRouteLeave(() => { if (map) { map.remove(); map = null; marker = null } })

onUnmounted(() => { if (map) { map.remove(); map = null; marker = null } })

function handleSubmit() {
  submitUnit({ isEdit: isEdit.value, unitId: unitId.value })
}
</script>

<template>
  <div class="surface-card unit-form-page card-full">
    <div class="unit-form-split">
      <!-- Panel izquierdo: formulario -->
      <form class="unit-form-panel" @submit.prevent="handleSubmit">
        <PButton
          type="button"
          text
          severity="secondary"
          icon="pi pi-arrow-left"
          class="back-btn-inline"
          @click="router.push('/units')"
        />

        <h1>{{ t(isEdit ? 'units.edit' : 'units.new') }}</h1>

        <PMessage v-if="loadError" severity="error" :closable="false" class="form-message">{{ loadError }}</PMessage>
        <PMessage v-if="locationLocked" severity="warn" :closable="false" class="form-message">{{ t('units.locationLocked') }}</PMessage>

        <div class="form-field">
          <span class="form-field-label">{{ t('fields.type') }}</span>
          <div :class="{ 'selector-invalid': invalids.unitType }">
            <SelectButton
              v-model="unitType"
              :options="unitTypeOptions"
              option-label="label"
              option-value="value"
              class="unit-type-selector"
            />
          </div>
        </div>

        <div class="form-field">
          <label for="unit-name">{{ t('fields.unitName') }}</label>
          <InputText
            id="unit-name"
            v-model="name"
            :placeholder="t('fields.unitNamePlaceholder')"
            maxlength="255"
            :invalid="!!invalids.name"
            fluid
          />
          <small v-if="errors.name" class="field-error">{{ errors.name }}</small>
        </div>

        <div class="form-field">
          <label for="unit-address">{{ t('fields.address') }}</label>
          <div class="address-row">
            <InputText
              id="unit-address"
              v-model="address"
              :placeholder="t('fields.addressPlaceholder')"
              :disabled="locationLocked"
              maxlength="500"
              fluid
              @keydown.enter.prevent="!locationLocked && geocodeAddress()"
            />
            <PButton
              type="button"
              icon="pi pi-search"
              severity="secondary"
              :loading="geocoding"
              :disabled="locationLocked"
              @click="geocodeAddress"
              v-tooltip="t('units.geocodeSearch')"
            />
          </div>
          <small v-if="geocodeError" class="field-hint" style="color:#f59e0b">
            {{ t(geocodeError) }}
          </small>
          <small v-if="geocoding" class="field-hint">
            <i class="pi pi-spin pi-spinner" /> {{ t('units.geocoding') }}
          </small>
        </div>

        <div v-if="!locationLocked" class="coords-hint">
          <i class="pi pi-map-marker" />
          {{ t('units.mapClickHint') }}
        </div>

        <div class="coords-row">
          <div class="form-field">
            <label for="unit-lat">{{ t('fields.latitude') }}</label>
            <InputText
              id="unit-lat"
              v-model="latitude"
              type="number"
              step="any"
              :placeholder="t('fields.latitudePlaceholder')"
              :disabled="locationLocked"
              fluid
            />
          </div>
          <div class="form-field">
            <label for="unit-lon">{{ t('fields.longitude') }}</label>
            <InputText
              id="unit-lon"
              v-model="longitude"
              type="number"
              step="any"
              :placeholder="t('fields.longitudePlaceholder')"
              :disabled="locationLocked"
              fluid
            />
          </div>
        </div>

        <div class="form-field">
          <label for="unit-default-priority">{{ t('units.defaultPriority') }}</label>
          <PSelect id="unit-default-priority" v-model="defaultPriority" :options="priorityOptions" option-label="label" option-value="value" :disabled="priorityLockedByCompany" show-clear fluid />
          <small v-if="priorityLockedByCompany" class="field-hint" style="color:#b45309">
            <i class="pi pi-lock" /> {{ t('units.defaultPriorityLockedByCompany') }}
          </small>
          <small v-else class="field-hint">{{ t('units.defaultPriorityHelp') }}</small>
        </div>

        <PMessage v-if="error" severity="error" :closable="false" class="form-message">{{ error }}</PMessage>
        <PMessage v-if="success" severity="success" :closable="false" class="form-message">{{ t(success) }}</PMessage>

        <PButton
          type="submit"
          :label="loading ? t('common.loading') : t('common.save')"
          :loading="loading"
          fluid
          class="submit-btn"
        />
      </form>

      <!-- Panel derecho: mapa -->
      <div class="unit-map-panel">
        <div ref="mapEl" class="unit-map-el" />
        <div class="map-hint-overlay">
          <i class="pi pi-info-circle" />
          {{ t('units.mapClickHint') }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped src="./UnitFormView.css"></style>
