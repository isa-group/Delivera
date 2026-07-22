<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Chart from 'primevue/chart'
import L from 'leaflet'
import {
  createMap, addMarker, addRoute, clusterOptions,
  attachRouteVisibilityHandler, currentLocationOf,
} from '@/composables/useDeliveraMap'
import { MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_COUNTRY } from '@/constants/map'
import { useServices } from '@/composables/useServices'

const { t } = useI18n()
const router = useRouter()

const dataApi = useServices("data-service")
const auth = useAuthStore()

const units = ref([])
const orders = ref([])
const loadingStats = ref(false)
const mapEl = ref(null)
let map = null
let routeEntries = []

const firstName = computed(() => auth.user?.firstName || auth.user?.email || '')
const isAdmin = computed(() => auth.role === 'COMPANY_ADMIN')
const isAnalyst = computed(() => auth.role === 'ANALYST')

const totalOrders = computed(() => orders.value.length)
const activeOrders = computed(() => orders.value.filter(o => o.status === 'PENDING' || o.status === 'IN_TRANSIT').length)
const deliveredOrders = computed(() => orders.value.filter(o => o.status === 'DELIVERED').length)
const pendingOrders = computed(() => orders.value.filter(o => o.status === 'PENDING').length)

const showSetupBanner = computed(() =>
  isAdmin.value && !loadingStats.value && units.value.length === 0
)

// Chart: pedidos por día agrupados de los últimos 14 días.
// Agregación en una sola pasada por `orders` → O(N) en lugar de O(días × estados × N).
const chartData = computed(() => {
  if (!orders.value.length) return null
  const days = 14
  const statuses = ['PENDING', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED']
  const labels = []
  const indexByDate = new Map()
  const now = new Date()
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    labels.push(d.toLocaleDateString(undefined, { day: '2-digit', month: 'short' }))
    indexByDate.set(d.toISOString().slice(0, 10), days - 1 - i)
  }
  const byStatus = Object.fromEntries(statuses.map(s => [s, Array.from({ length: days }, () => 0)]))
  for (const o of orders.value) {
    const idx = indexByDate.get((o.createdAt || '').slice(0, 10))
    if (idx !== undefined && byStatus[o.status]) byStatus[o.status][idx]++
  }
  const colors = { PENDING: '#f59e0b', IN_TRANSIT: '#3b82f6', DELIVERED: '#22c55e', CANCELLED: '#ef4444' }
  return {
    labels,
    datasets: statuses.map(s => ({
      label: t(`orders.status.${s}`),
      data: byStatus[s],
      backgroundColor: colors[s],
      borderRadius: 3,
    })),
  }
})

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  layout: { padding: { top: 16, bottom: 0 } },
  plugins: {
    legend: { position: 'bottom', labels: { boxWidth: 12, font: { size: 11 } } },
  },
  scales: {
    x: {
      stacked: true,
      grid: { display: false },
      ticks: { font: { size: 10 }, maxRotation: 45, autoSkip: true, maxTicksLimit: 10 },
    },
    y: {
      stacked: true,
      beginAtZero: true,
      grace: '10%',
      ticks: { stepSize: 1, precision: 0 },
    },
  },
}

function isOwnCompany(companyId) {
  return companyId && auth.companyId && companyId === auth.companyId
}

function destKindFor(o) {
  if (o.destinationId) {
    return isOwnCompany(o.destinationCompanyId) ? 'OWN_UNIT' : 'OTHER_UNIT'
  }
  return 'CUSTOMER'
}

function destNavFor(o) {
  if (o.destinationId && isOwnCompany(o.destinationCompanyId)) {
    return `/units/${o.destinationId}`
  }
  if (!o.destinationId && o.loyalUserId) return `/loyal-users/${o.loyalUserId}`
  return null
}

async function initMap(unitList) {
  if (!mapEl.value) return
  routeEntries.forEach(e => { if (map && e?.layer) map.removeLayer(e.layer) })
  routeEntries = []
  if (map) { map.remove(); map = null }
  map = createMap(mapEl.value)

  const mapped = unitList.filter(u => u.latitude != null && u.longitude != null)
  if (mapped.length === 0) {
    map.setView(MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_COUNTRY)
    return
  }

  const cluster = L.markerClusterGroup(clusterOptions())
  const bounds = []
  const markerByKey = new Map()

  function addOnce(key, opts) {
    if (markerByKey.has(key)) return markerByKey.get(key)
    const mk = addMarker(map, opts)
    markerByKey.set(key, mk)
    cluster.addLayer(mk)
    return mk
  }

  mapped.forEach(u => {
    const lat = Number.parseFloat(u.latitude)
    const lng = Number.parseFloat(u.longitude)
    addOnce('u:' + u.id, {
      id: u.id, lat, lon: lng, kind: 'OWN_UNIT',
      title: u.name, subtitle: t('units.' + u.type),
      actionLabel: t('units.detail'),
      navigateTo: `/units/${u.id}`,
      router,
    })
    bounds.push([lat, lng])
  })

  // Destinos (unidades ajenas o clientes) para pedidos activos con coords
  const routeable = orders.value.filter(o =>
    (o.status === 'PENDING' || o.status === 'IN_TRANSIT') &&
    o.originLat != null && o.originLon != null &&
    o.destinationLat != null && o.destinationLon != null,
  )
  routeable.forEach(o => {
    // Origen — si no es de mi empresa, añadirlo como OTHER_UNIT (no está en /units).
    if (!isOwnCompany(o.originCompanyId)) {
      const oLat = Number.parseFloat(o.originLat)
      const oLon = Number.parseFloat(o.originLon)
      addOnce('u:' + o.originId, {
        id: o.originId, lat: oLat, lon: oLon, kind: 'OTHER_UNIT',
        title: o.originName, subtitle: t('units.detail'),
        actionLabel: null, navigateTo: null, router,
      })
      bounds.push([oLat, oLon])
    }

    const dLat = Number.parseFloat(o.destinationLat)
    const dLon = Number.parseFloat(o.destinationLon)
    const destSuffix = o.loyalUserId ? 'l:' + o.loyalUserId : `c:${dLat},${dLon}`
    const key = o.destinationId ? 'u:' + o.destinationId : destSuffix
    const kind = destKindFor(o)
    const navTo = destNavFor(o)
    const detailLabel = kind === 'OWN_UNIT' ? t('units.detail') : t('loyalUsers.detail')
    addOnce(key, {
      id: key, lat: dLat, lon: dLon, kind,
      title: o.destinationName || o.recipientName || o.recipientEmail || '',
      subtitle: kind === 'CUSTOMER' ? t('orders.recipientName') : t('units.detail'),
      actionLabel: navTo ? detailLabel : null,
      navigateTo: navTo, router,
    })
    bounds.push([dLat, dLon])
  })

  map.addLayer(cluster)
  map.fitBounds(bounds, { padding: [40, 40] })
  attachRouteVisibilityHandler(map, cluster, () => routeEntries)

  // Rutas (todas las activas con coords completas)
  for (const p of routeable) {
    if (!map) return
    const originKey = 'u:' + p.originId
    const destKeySuffix = p.loyalUserId ? 'l:' + p.loyalUserId : `c:${p.destinationLat},${p.destinationLon}`
    const destKey = p.destinationId ? 'u:' + p.destinationId : destKeySuffix
    const entry = await addRoute(map, {
      orderId: p.id,
      origin: { lat: Number.parseFloat(p.originLat), lon: Number.parseFloat(p.originLon) },
      dest:   { lat: Number.parseFloat(p.destinationLat), lon: Number.parseFloat(p.destinationLon) },
      popupTitle: p.reference,
      popupSubtitle: `${p.originName} → ${p.destinationName || p.recipientName || p.recipientEmail || ''}`,
      actionLabel: t('orders.viewDetail'),
      router,
      originMarker: markerByKey.get(originKey) || null,
      destMarker: markerByKey.get(destKey) || null,
      status: p.status,
      currentLocation: currentLocationOf(p),
    })
    routeEntries.push(entry)
    entry.layer?.bringToFront?.()
  }
}

async function loadData() {
  loadingStats.value = true
  try {
    const [unitsRes, ordersRes] = await Promise.all([
      dataApi.get('/units'),
      dataApi.get('/orders'),
    ])
    if (unitsRes.ok) units.value = await unitsRes.json()
    if (ordersRes.ok) orders.value = await ordersRes.json()
  } catch {
    // stats son opcionales
  } finally {
    loadingStats.value = false
  }
}

onMounted(async () => {
  await loadData()
  await nextTick()
  initMap(units.value)
})

watch(() => auth.companyId, async (newId, oldId) => {
  if (newId && newId !== oldId) {
    await loadData()
    await nextTick()
    initMap(units.value)
  }
})

onBeforeRouteLeave(() => {
  if (map) { map.remove(); map = null }
  routeEntries = []
})

onUnmounted(() => {
  if (map) { map.remove(); map = null }
  routeEntries = []
})
</script>

<template>
  <div class="home-view">
    <!-- Bienvenida + stats inline -->
    <div class="home-header">
      <div class="home-welcome">
        <h1>{{ t('home.welcome', { name: firstName }) }}</h1>
        <PTag v-if="auth.companyName" :value="auth.companyName" severity="secondary" />
      </div>

      <!-- Stats inline (admin/analyst) -->
      <div v-if="!loadingStats && (isAdmin || isAnalyst)" class="stats-inline">
        <span class="stat-chip">
          <i class="pi pi-building" />
          <strong>{{ units.length }}</strong>
          {{ t('home.stats.units') }}
        </span>
        <span class="stat-chip">
          <i class="pi pi-send" />
          <strong>{{ totalOrders }}</strong>
          {{ t('home.stats.totalOrders') }}
        </span>
        <span class="stat-chip stat-chip--active">
          <i class="pi pi-sync" />
          <strong>{{ activeOrders }}</strong>
          {{ t('home.stats.active') }}
        </span>
        <span class="stat-chip stat-chip--done">
          <i class="pi pi-check-circle" />
          <strong>{{ deliveredOrders }}</strong>
          {{ t('home.stats.delivered') }}
        </span>
      </div>
    </div>

    <!-- Banner configuración pendiente -->
    <div v-if="showSetupBanner" class="setup-banner">
      <span class="setup-banner__text">{{ t('home.setupBanner') }}</span>
      <PButton :label="t('home.createFirstUnit')" icon="pi pi-plus" size="small" @click="router.push('/units/new')" />
    </div>

    <!-- Gráfica + Mapa en dos columnas -->
    <div v-if="!loadingStats && (isAdmin || isAnalyst)" class="home-content">
      <!-- Gráfica pedidos por día -->
      <div class="surface-panel home-card">
        <h2>{{ t('home.ordersChart') }}</h2>
        <div v-if="chartData" class="chart-wrapper">
          <Chart type="bar" :data="chartData" :options="chartOptions" />
        </div>
        <div v-else class="chart-empty">{{ t('home.noOrdersChart') }}</div>
      </div>

      <!-- Mapa unidades -->
      <div class="surface-panel home-card">
        <h2>{{ t('home.unitsMap') }}</h2>
        <div ref="mapEl" class="home-map" />
        <p v-if="!loadingStats && units.length === 0" class="map-hint">{{ t('units.noCoords') }}</p>
      </div>
    </div>

    <!-- Accesos rápidos rediseñados -->
    <div class="quick-section">
      <h2>{{ t('home.quickActions') }}</h2>
      <div class="quick-grid">
        <button v-if="auth.canCreateOrders" class="quick-item" @click="router.push('/orders/new')">
          <i class="pi pi-plus-circle quick-icon" />
          <span class="quick-label">{{ t('orders.new') }}</span>
          <span class="quick-desc">{{ t('home.quick.newOrderDesc') }}</span>
        </button>
        <button v-if="isAdmin || isAnalyst" class="quick-item" @click="router.push('/orders?filter=PENDING')">
          <i class="pi pi-send quick-icon" />
          <span class="quick-label">{{ t('home.quick.pendingOrders') }}</span>
          <span class="quick-desc">{{ t('home.quick.pendingOrdersDesc', { n: pendingOrders }) }}</span>
        </button>
        <button v-if="isAdmin" class="quick-item" @click="router.push('/units/new')">
          <i class="pi pi-building quick-icon" />
          <span class="quick-label">{{ t('units.new') }}</span>
          <span class="quick-desc">{{ t('home.quick.newUnitDesc') }}</span>
        </button>
        <button v-if="isAdmin" class="quick-item" @click="router.push('/workers')">
          <i class="pi pi-id-card quick-icon" />
          <span class="quick-label">{{ t('home.quick.team') }}</span>
          <span class="quick-desc">{{ t('home.quick.teamDesc') }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped src="./HomeView.css"></style>
