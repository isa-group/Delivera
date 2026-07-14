<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useApi } from '@/composables/useApi'
import L from 'leaflet'
import { createMap, addMarker, clusterOptions, fitBounds } from '@/composables/useDeliveraMap'
import { MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION } from '@/constants/map'
import { useServices } from '@/composables/useServices'

const { t } = useI18n()
const api = useApi()
const dataApi = useServices("data-service")
const router = useRouter()

const loading = ref(false)
const error = ref('')
const unitsWithCoords = ref(0)
const mapEl = ref(null)
let map = null
let clusterGroup = null

onMounted(async () => {
  loading.value = true
  error.value = ''
  let mapped = []
  try {
    const res = await dataApi.get('/units')
    if (!res.ok) { error.value = t('error.connection'); return }
    const units = await res.json()
    mapped = units.filter(u => u.latitude != null && u.longitude != null)
    unitsWithCoords.value = mapped.length
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }

  if (error.value) return

  await nextTick()
  map = createMap(mapEl.value)

  if (mapped.length === 0) {
    map.setView(MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION)
    return
  }

  clusterGroup = L.markerClusterGroup(clusterOptions())
  const bounds = []
  mapped.forEach(u => {
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
})

onBeforeRouteLeave(() => {
  if (map) { map.remove(); map = null }
})

onUnmounted(() => {
  if (map) { map.remove(); map = null }
})
</script>

<template>
  <div class="surface-card card-wide">
    <div class="list-header">
      <h1>{{ t('units.mapTitle') }}</h1>
      <PButton
        :label="t('units.listView')"
        icon="pi pi-list"
        severity="secondary"
        @click="router.push('/units')"
      />
    </div>

    <PMessage v-if="error" severity="error" :closable="false">{{ error }}</PMessage>

    <div v-if="loading" class="map-loading">
      <i class="pi pi-spin pi-spinner" style="font-size:24px" />
    </div>

    <div v-show="!loading && !error" class="map-container">
      <div ref="mapEl" class="map-el" data-testid="units-map" />
      <p v-if="!loading && unitsWithCoords === 0" class="map-no-coords">
        {{ t('units.noCoords') }}
      </p>
    </div>
  </div>
</template>

<style scoped src="./UnitsMapView.css"></style>
