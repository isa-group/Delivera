<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useConfirm } from 'primevue/useconfirm'
import { buildDeleteConfirmOptions } from '@/composables/useConfirmDelete'
import { useAuthStore } from '@/stores/auth'
import { useAppConfig } from '@/composables/useAppConfig'
import { useFormatDate } from '@/composables/useFormatDate'
import EmptyState from '@/components/EmptyState.vue'
import L from 'leaflet'
import {
  createMap, addMarker, addRoute, clusterOptions, fitBounds,
  attachRouteVisibilityHandler, currentLocationOf, isActiveOrder, hasOriginCoords,
} from '@/composables/useDeliveraMap'
import { MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION } from '@/constants/map'
import { useResourceListFromService } from '@/composables/useResourceListFromService'
import { useServices } from '@/composables/useServices'

const { t } = useI18n()
const { formatDate } = useFormatDate()
const router = useRouter()
const route = useRoute()
const dataApi = useServices("data-service")
const confirm = useConfirm()
const auth = useAuthStore()
const { load: loadConfig, statusSeverity } = useAppConfig()
const { items: orders, loading, error } = useResourceListFromService('/orders','data-service')

const successMsg = ref(route.query.created ? t('orders.created', { reference: route.query.created }) : '')
const filterStatus = ref('ALL')
const filterPriority = ref('ALL')
const filterText = ref('')

const mapEl = ref(null)
let map = null
let clusterGroup = null
let routeEntries = []
let mapToken = 0
let detachRouteVisibility = null

const statusOptions = computed(() => [
  { label: t('orders.filterAll'), value: 'ALL' },
  { label: t('orders.status.PENDING'), value: 'PENDING' },
  { label: t('orders.status.IN_TRANSIT'), value: 'IN_TRANSIT' },
  { label: t('orders.status.DELIVERED'), value: 'DELIVERED' },
  { label: t('orders.status.CANCELLED'), value: 'CANCELLED' },
])

const priorityOptions = computed(() => [
  { label: t('orders.filterAll'), value: 'ALL' },
  { label: t('orders.priority.HIGH'), value: 'HIGH' },
  { label: t('orders.priority.NORMAL'), value: 'NORMAL' },
  { label: t('orders.priority.LOW'), value: 'LOW' },
])

const filtered = computed(() => {
  return orders.value.filter(o => {
    if (filterStatus.value !== 'ALL' && o.status !== filterStatus.value) return false
    if (filterPriority.value !== 'ALL' && o.priority !== filterPriority.value) return false
    if (filterText.value) {
      const q = filterText.value.toLowerCase()
      const matches = o.reference.toLowerCase().includes(q)
        || (o.originName || '').toLowerCase().includes(q)
        || (o.destinationName || '').toLowerCase().includes(q)
        || (o.recipientEmail || '').toLowerCase().includes(q)
        || (o.recipientName || '').toLowerCase().includes(q)
      if (!matches) return false
    }
    return true
  })
})

// Pedidos activos con coordenadas de origen (visibles en el mapa)
const mappableOrders = computed(() =>
  filtered.value.filter(o => isActiveOrder(o) && hasOriginCoords(o))
)

// Pedidos activos SIN coordenadas (no aparecen en el mapa) — listado lateral DSI-22.3
const unmappableOrders = computed(() =>
  filtered.value.filter(o => isActiveOrder(o) && !hasOriginCoords(o))
)

function isOwnCompany(companyId) {
  return companyId && auth.companyId && companyId === auth.companyId
}

function originKind(o) {
  return isOwnCompany(o.originCompanyId) ? 'OWN_UNIT' : 'OTHER_UNIT'
}

function originNavigateTo(o) {
  return isOwnCompany(o.originCompanyId) ? `/units/${o.originId}` : null
}

function destinationKind(o) {
  if (o.destinationId) {
    return isOwnCompany(o.destinationCompanyId) ? 'OWN_UNIT' : 'OTHER_UNIT'
  }
  return 'CUSTOMER'
}

function destinationNavigateTo(o) {
  if (o.destinationId && isOwnCompany(o.destinationCompanyId)) {
    return `/units/${o.destinationId}`
  }
  if (!o.destinationId && o.loyalUserId) return `/loyal-users/${o.loyalUserId}`
  return null
}

function destinationTitle(o) {
  return o.destinationName || o.recipientName || o.recipientEmail || ''
}

function initMap() {
  if (!mapEl.value) return
  if (map) { map.remove(); map = null }
  map = createMap(mapEl.value)
  map.setView(MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION)
  updateMapOrders()
}

function clearRoutes() {
  routeEntries.forEach(e => { if (map && e?.layer) map.removeLayer(e.layer) })
  routeEntries = []
}

function addOrGetMarker(mapInstance, group, markerByKey, key, opts) {
  if (markerByKey.has(key)) return markerByKey.get(key)
  const mk = addMarker(mapInstance, opts)
  markerByKey.set(key, mk)
  group.addLayer(mk)
  return mk
}

async function addOrderRoutes(routeable, token, markerByKey) {
  for (const o of routeable) {
    if (token !== mapToken || !map) return
    const originKey = 'u:' + o.originId
    const destSuffix = o.loyalUserId ? 'l:' + o.loyalUserId : `c:${o.destinationLat},${o.destinationLon},${o.recipientEmail || ''}`
    const destKey = o.destinationId ? 'u:' + o.destinationId : destSuffix
    const entry = await addRoute(map, {
      orderId: o.id,
      origin: { lat: Number.parseFloat(o.originLat), lon: Number.parseFloat(o.originLon) },
      dest:   { lat: Number.parseFloat(o.destinationLat), lon: Number.parseFloat(o.destinationLon) },
      popupTitle: o.reference,
      popupSubtitle: `${o.originName} → ${destinationTitle(o)}`,
      actionLabel: t('orders.viewDetail'),
      router,
      originMarker: markerByKey.get(originKey) || null,
      destMarker: markerByKey.get(destKey) || null,
      status: o.status,
      currentLocation: currentLocationOf(o),
    })
    if (token !== mapToken) {
      if (entry?.layer && map) map.removeLayer(entry.layer)
      return
    }
    routeEntries.push(entry)
    entry.layer?.bringToFront?.()
  }
}

async function updateMapOrders() {
  if (!map) return
  const token = ++mapToken
  if (clusterGroup) { map.removeLayer(clusterGroup); clusterGroup = null }
  clearRoutes()

  const items = mappableOrders.value
  if (items.length === 0) return

  clusterGroup = L.markerClusterGroup(clusterOptions())
  const bounds = []
  const markerByKey = new Map()

  items.forEach(o => {
    const oLat = Number.parseFloat(o.originLat)
    const oLon = Number.parseFloat(o.originLon)

    const oKind = originKind(o)
    const oNav = originNavigateTo(o)
    addOrGetMarker(map, clusterGroup, markerByKey, 'u:' + o.originId, {
      id: o.originId, lat: oLat, lon: oLon, kind: oKind,
      title: o.originName, subtitle: t('units.detail'),
      actionLabel: oNav ? t('units.detail') : null,
      navigateTo: oNav, router,
    })
    bounds.push([oLat, oLon])

    if (o.destinationLat != null && o.destinationLon != null) {
      const dLat = Number.parseFloat(o.destinationLat)
      const dLon = Number.parseFloat(o.destinationLon)
      const kind = destinationKind(o)
      const destSuffix = o.loyalUserId ? 'l:' + o.loyalUserId : `c:${dLat},${dLon},${o.recipientEmail || ''}`
      const key = o.destinationId ? 'u:' + o.destinationId : destSuffix
      const loyalLabel = (kind === 'CUSTOMER' && o.loyalUserId) ? t('loyalUsers.detail') : null
      const destActionLabel = kind === 'OWN_UNIT' ? t('units.detail') : loyalLabel
      addOrGetMarker(map, clusterGroup, markerByKey, key, {
        id: key, lat: dLat, lon: dLon, kind,
        title: destinationTitle(o),
        subtitle: kind === 'CUSTOMER' ? t('orders.recipientName') : t('units.detail'),
        actionLabel: destActionLabel,
        navigateTo: destinationNavigateTo(o), router,
      })
      bounds.push([dLat, dLon])
    }
  })

  map.addLayer(clusterGroup)
  fitBounds(map, bounds)

  // Instalar el handler limpiando el anterior para evitar acumulación de listeners.
  if (detachRouteVisibility) { detachRouteVisibility(); detachRouteVisibility = null }
  detachRouteVisibility = attachRouteVisibilityHandler(map, clusterGroup, () => routeEntries)

  // Rutas (todas las que tienen coords completas)
  const routeable = items.filter(o => o.destinationLat != null && o.destinationLon != null)
  await addOrderRoutes(routeable, token, markerByKey)
}

// Debounce: al teclear en el filtro evitamos relanzar updateMapOrders
// (que hace peticiones OSRM por ruta) en cada pulsación.
let filterDebounce = null
watch(filtered, () => {
  if (!map) return
  clearTimeout(filterDebounce)
  filterDebounce = setTimeout(() => updateMapOrders(), 250)
})
onUnmounted(() => clearTimeout(filterDebounce))

async function deleteOrder(e, id) {
  e.stopPropagation()
  confirm.require(buildDeleteConfirmOptions(t, t('orders.deleteConfirm'), async () => {
    const res = await dataApi.del(`/orders/${id}`)
    if (res.ok) orders.value = orders.value.filter(o => o.id !== id)
  }))
}

onMounted(async () => {
  loadConfig()
  await nextTick()
  initMap()
})

onBeforeRouteLeave(() => { if (map) { map.remove(); map = null } })

onUnmounted(() => { if (map) { map.remove(); map = null } })

watch(orders, async () => {
  await nextTick()
  if (map) updateMapOrders()
  else initMap()
})
</script>

<template>
  <div class="surface-card orders-page card-full">
    <div class="orders-split">
      <!-- Panel izquierdo: lista -->
      <div class="orders-list-panel">
        <div class="list-header">
          <h1>{{ t('orders.listTitle') }}</h1>
          <div class="header-actions">
            <PButton
              v-if="auth.canCreateOrders"
              :label="t('orders.new')"
              icon="pi pi-plus"
              @click="router.push('/orders/new')"
            />
          </div>
        </div>

        <div class="filters-wrapper">
          <span class="filters-label">{{ t('common.filters') }}</span>
          <div class="filters-box">
            <div class="filter-row">
              <label for="orders-filter-ref" class="filter-group-label">{{ t('orders.reference') }}</label>
              <input
                id="orders-filter-ref"
                v-model="filterText"
                :placeholder="t('orders.reference') + '...'"
                class="filter-search"
                type="text"
              />
            </div>
            <div class="filter-row">
              <label for="orders-filter-status" class="filter-group-label">{{ t('orders.statusLabel') }}</label>
              <PSelect
                input-id="orders-filter-status"
                v-model="filterStatus"
                :options="statusOptions"
                option-label="label"
                option-value="value"
                class="filter-select"
              />
              <label for="orders-filter-priority" class="filter-group-label">{{ t('orders.priority.label') }}</label>
              <PSelect
                input-id="orders-filter-priority"
                v-model="filterPriority"
                :options="priorityOptions"
                option-label="label"
                option-value="value"
                class="filter-select"
              />
            </div>
          </div>
        </div>

        <PMessage v-if="successMsg" severity="success" :closable="false">{{ successMsg }}</PMessage>
        <PMessage v-if="error" severity="error" :closable="false">{{ error }}</PMessage>

        <div class="list-scroll">
        <DataTable
          :value="filtered"
          :loading="loading"
          paginator
          :rows="10"
          striped-rows
          row-hover
          @row-click="e => router.push(`/orders/${e.data.id}`)"
        >
          <template #empty>
            <EmptyState icon="pi-send" :message="t('orders.empty')">
              <PButton
                v-if="auth.canCreateOrders"
                :label="t('orders.new')"
                icon="pi pi-plus"
                size="small"
                @click="router.push('/orders/new')"
              />
            </EmptyState>
          </template>
          <Column field="reference" :header="t('orders.reference')" style="width:150px;font-weight:600" />
          <Column :header="t('orders.route')">
            <template #body="{ data }">
              <span>{{ data.originName }}</span>
              <i class="pi pi-arrow-right" style="margin:0 6px;font-size:11px;color:#94a3b8" />
              <span v-if="data.destinationName">{{ data.destinationName }}</span>
              <span v-else class="recipient-label">{{ data.recipientName || data.recipientEmail }}</span>
            </template>
          </Column>
          <Column :header="t('orders.statusLabel')" style="width:130px">
            <template #body="{ data }">
              <PTag
                :value="t(`orders.status.${data.status}`)"
                :severity="statusSeverity[data.status] ?? 'secondary'"
              />
            </template>
          </Column>
          <Column :header="t('orders.date')" style="width:110px">
            <template #body="{ data }">{{ formatDate(data.createdAt) }}</template>
          </Column>
          <Column v-if="auth.isCompanyAdmin" style="width:48px;padding:0">
            <template #body="{ data }">
              <PButton icon="pi pi-times" severity="danger" text rounded size="small" class="delete-btn"
                       :aria-label="t('common.delete')" v-tooltip.top="t('common.delete')"
                       @click="deleteOrder($event, data.id)" />
            </template>
          </Column>
        </DataTable>
        </div>
      </div>

      <!-- Panel derecho: mapa -->
      <div class="orders-map-panel">
        <div ref="mapEl" class="orders-map-el" />
        <div v-if="mappableOrders.length === 0 && !loading" class="map-no-coords">
          {{ t('orders.noMapOrders') }}
        </div>
        <!-- Leyenda -->
        <div v-if="mappableOrders.length > 0" class="map-legend">
          <span class="legend-item"><span class="legend-dot" style="background:#7c3aed"></span>{{ t('map.legend.ownUnit') }}</span>
          <span class="legend-item"><span class="legend-dot" style="background:#a78bfa"></span>{{ t('map.legend.otherUnit') }}</span>
          <span class="legend-item"><span class="legend-line legend-line--solid"></span>{{ t('map.legend.routeSolid') }}</span>
          <span class="legend-item"><span class="legend-line legend-line--dashed"></span>{{ t('map.legend.routeDashed') }}</span>
        </div>
        <!-- Listado lateral de pedidos activos sin coordenadas (DSI-22.3) -->
        <aside v-if="unmappableOrders.length > 0" class="orders-no-coords">
          <p class="orders-no-coords-title">{{ t('orders.withoutCoordinates') }} ({{ unmappableOrders.length }})</p>
          <ul>
            <li v-for="o in unmappableOrders" :key="o.id">
              <a href="#" @click.prevent="router.push(`/orders/${o.id}`)">{{ o.reference }}</a>
              <span class="orders-no-coords-meta">{{ o.originName }} → {{ o.destinationName || o.recipientName || o.recipientEmail || '—' }}</span>
            </li>
          </ul>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped src="./OrdersView.css"></style>
