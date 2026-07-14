<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useApi } from '@/composables/useApi'
import { useFormatDate } from '@/composables/useFormatDate'
import Chart from 'primevue/chart'
import L from 'leaflet'
import {
  createMap, addMarker, addRoute, clusterOptions, fitBounds,
  attachRouteVisibilityHandler,
} from '@/composables/useDeliveraMap'
import { MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_COUNTRY } from '@/constants/map'

const { t } = useI18n()
const api = useApi()
const { formatDate } = useFormatDate()

// ── Tabs ──────────────────────────────────────────────────────────────────────
const TABS = ['home', 'activity', 'administration', 'settings']
const activeTab = ref('home')

// ── Home ──────────────────────────────────────────────────────────────────────
const METRIC_KEYS = ['totalOrganizations', 'totalCompanies', 'totalOrdersThisMonth', 'totalActiveUsers']
const METRIC_ICONS = {
  totalOrganizations: 'pi-building',
  totalCompanies: 'pi-briefcase',
  totalOrdersThisMonth: 'pi-send',
  totalActiveUsers: 'pi-users',
}
const metrics = ref(null)
const metricsLoading = ref(false)
const homeChartData = ref(null)
const mapUnits = ref([])
const mapRoutes = ref([])
const mapEl = ref(null)
let routeEntries = []
let map = null

// ── Activity ──────────────────────────────────────────────────────────────────
const PERIODS = ['TODAY', 'WEEK', 'MONTH']
const ACTIVITY_METRIC_KEYS = ['totalOrders', 'completedOrders', 'cancelledOrders', 'activeOrders']
const ACTIVITY_METRIC_ICONS = {
  totalOrders: 'pi-send',
  completedOrders: 'pi-check-circle',
  cancelledOrders: 'pi-times-circle',
  activeOrders: 'pi-sync',
}
const ACTIVITY_METRIC_COLORS = {
  totalOrders: '#7c3aed',
  completedOrders: '#22c55e',
  cancelledOrders: '#ef4444',
  activeOrders: '#3b82f6',
}
const period = ref('MONTH')
const activityMetrics = ref(null)
const chartData = ref(null)
const companyRanking = ref([])
const activityLoading = ref(false)
const activityError = ref('')

const deliveryRate = computed(() => {
  if (!activityMetrics.value?.totalOrders) return null
  return Math.round((activityMetrics.value.completedOrders / activityMetrics.value.totalOrders) * 100)
})

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: false } },
  scales: {
    x: { grid: { display: false }, ticks: { font: { size: 10 } } },
    y: { beginAtZero: true, grace: '15%', ticks: { stepSize: 1, precision: 0 } },
  },
}))

// ── Administration ────────────────────────────────────────────────────────────
const ENTITIES = ['organizations', 'companies', 'orders', 'users', 'workers']
const activeEntity = ref('organizations')

const organizations = ref([])
const companies = ref([])
const orders = ref([])
const users = ref([])
const workers = ref([])
const entityLoading = ref(false)
const entityError = ref('')
const deleteConfirmId = ref(null)
const deleteLoading = ref(false)
const deleteError = ref('')

// ── Settings ──────────────────────────────────────────────────────────────────
const resetConfirming = ref(false)
const resetLoading = ref(false)
const resetError = ref('')
const resetSuccess = ref(false)

// ── Load functions ────────────────────────────────────────────────────────────
async function loadHome() {
  metricsLoading.value = true
  homeChartData.value = null
  try {
    const [metricsRes, chartRes, unitsRes, routesRes] = await Promise.all([
      api.get('/admin/metrics'),
      api.get('/admin/activity/orders-by-day?period=MONTH'),
      api.get('/admin/units'),
      api.get('/admin/routes'),
    ])
    if (metricsRes.ok) metrics.value = await metricsRes.json()
    if (chartRes.ok) {
      const raw = await chartRes.json()
      const entries = fillDateRange(raw, 'MONTH')
      if (entries.some(e => e.count > 0)) {
        homeChartData.value = {
          labels: entries.map(e => formatDateLabel(e.date)),
          datasets: [{
            label: t('home.ordersChart'),
            data: entries.map(e => e.count),
            backgroundColor: 'rgba(124, 58, 237, 0.75)',
            borderRadius: 4,
          }],
        }
      }
    }
    if (unitsRes.ok) mapUnits.value = await unitsRes.json()
    if (routesRes.ok) mapRoutes.value = await routesRes.json()
  } finally {
    metricsLoading.value = false
  }
  await nextTick()
  await initHomeMap()
}

async function initHomeMap() {
  routeEntries.forEach(e => { if (map && e?.layer) map.removeLayer(e.layer) })
  routeEntries = []
  if (!mapEl.value) return
  if (map) { map.remove(); map = null }
  map = createMap(mapEl.value)

  if (!mapUnits.value.length && !mapRoutes.value.length) {
    map.setView(MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_COUNTRY)
    return
  }

  const cluster = L.markerClusterGroup(clusterOptions())
  const bounds = []
  const markerByKey = new Map()

  mapUnits.value.forEach(u => {
    const lat = parseFloat(u.latitude)
    const lng = parseFloat(u.longitude)
    const mk = addMarker(map, {
      id: u.id, lat, lon: lng, kind: 'OWN_UNIT',
      title: u.name, subtitle: u.companyName,
      actionLabel: null, navigateTo: null, router: null,
    })
    markerByKey.set('u:' + u.id, mk)
    cluster.addLayer(mk)
    bounds.push([lat, lng])
  })

  map.addLayer(cluster)
  if (bounds.length) fitBounds(map, bounds)
  else map.setView(MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_COUNTRY)

  attachRouteVisibilityHandler(map, cluster, () => routeEntries)

  for (const r of mapRoutes.value) {
    if (!map) return
    const entry = await addRoute(map, {
      orderId: null,
      origin: { lat: r.originLat, lon: r.originLon },
      dest: { lat: r.destinationLat, lon: r.destinationLon },
      popupTitle: r.reference,
      popupSubtitle: `${r.originName} → ${r.destinationName}`,
      actionLabel: null,
      router: null,
      originMarker: markerByKey.get('u:' + r.originId) || null,
      destMarker: markerByKey.get('u:' + r.destinationId) || null,
      status: r.status,
      currentLocation: null,
    })
    routeEntries.push(entry)
    entry.layer?.bringToFront?.()
  }
}

function destroyMap() {
  routeEntries.forEach(e => { if (map && e?.layer) map.removeLayer(e.layer) })
  routeEntries = []
  if (map) { map.remove(); map = null }
}

async function loadActivity() {
  activityLoading.value = true
  activityError.value = ''
  chartData.value = null
  companyRanking.value = []
  try {
    const [metricsRes, chartRes, rankingRes] = await Promise.all([
      api.get(`/admin/activity?period=${period.value}`),
      api.get(`/admin/activity/orders-by-day?period=${period.value}`),
      api.get(`/admin/activity/company-ranking?period=${period.value}`),
    ])
    if (metricsRes.ok) activityMetrics.value = await metricsRes.json()
    else activityError.value = t('error.connection')
    if (chartRes.ok) {
      const raw = await chartRes.json()
      const entries = fillDateRange(raw, period.value)
      chartData.value = {
        labels: entries.map(e => formatDateLabel(e.date)),
        datasets: [{
          label: t('activity.panel.chart.orders'),
          data: entries.map(e => e.count),
          backgroundColor: 'rgba(124, 58, 237, 0.75)',
          borderRadius: 4,
        }],
      }
    }
    if (rankingRes.ok) companyRanking.value = await rankingRes.json()
  } catch {
    activityError.value = t('error.connection')
  } finally {
    activityLoading.value = false
  }
}

async function loadEntity(entity) {
  entityLoading.value = true
  entityError.value = ''
  deleteConfirmId.value = null
  try {
    const res = await api.get(`/admin/${entity}`)
    if (res.ok) {
      const data = await res.json()
      if (entity === 'organizations') organizations.value = data
      else if (entity === 'companies') companies.value = data
      else if (entity === 'orders') orders.value = data
      else if (entity === 'users') users.value = data
      else if (entity === 'workers') workers.value = data
    } else {
      entityError.value = t('error.connection')
    }
  } catch {
    entityError.value = t('error.connection')
  } finally {
    entityLoading.value = false
  }
}

function confirmDelete(id) {
  deleteConfirmId.value = id
  deleteError.value = ''
}

async function executeDelete() {
  if (!deleteConfirmId.value) return
  deleteLoading.value = true
  deleteError.value = ''
  try {
    const res = await api.del(`/admin/${activeEntity.value}/${deleteConfirmId.value}`)
    if (res.ok || res.status === 204) {
      deleteConfirmId.value = null
      await loadEntity(activeEntity.value)
      await loadHome()
    } else {
      const data = await res.json().catch(() => ({}))
      deleteError.value = data.code ? t('error.' + data.code) : t('error.connection')
    }
  } catch {
    deleteError.value = t('error.connection')
  } finally {
    deleteLoading.value = false
  }
}

async function executeReset() {
  resetLoading.value = true
  resetError.value = ''
  resetSuccess.value = false
  try {
    const res = await api.post('/admin/reset', {})
    if (res.ok || res.status === 204) {
      resetConfirming.value = false
      resetSuccess.value = true
      await loadHome()
    } else {
      resetError.value = t('error.connection')
    }
  } catch {
    resetError.value = t('error.connection')
  } finally {
    resetLoading.value = false
  }
}

function formatDateLabel(dateStr) {
  const d = new Date(dateStr + 'T00:00:00')
  return d.toLocaleDateString(undefined, { day: '2-digit', month: 'short' })
}

function fillDateRange(entries, p) {
  const daysMap = { TODAY: 1, WEEK: 7 }
  const days = daysMap[p] ?? 30
  const dataMap = Object.fromEntries(entries.map(e => [e.date, e.count]))
  const result = []
  const now = new Date()
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    const dateStr = d.toISOString().slice(0, 10)
    result.push({ date: dateStr, count: dataMap[dateStr] ?? 0 })
  }
  return result
}

function selectTab(tab) {
  activeTab.value = tab
  deleteConfirmId.value = null
  deleteError.value = ''
  resetConfirming.value = false
  resetSuccess.value = false
  if (tab !== 'home') destroyMap()
}

function selectEntity(entity) {
  activeEntity.value = entity
  deleteConfirmId.value = null
  deleteError.value = ''
  loadEntity(entity)
}

watch(period, loadActivity)

onMounted(loadHome)

watch(activeTab, tab => {
  if (tab === 'home') loadHome()
  else if (tab === 'activity') loadActivity()
  else if (tab === 'administration') loadEntity(activeEntity.value)
})

onUnmounted(destroyMap)
</script>

<template>
  <div class="surface-card card-full admin-view">
    <!-- Header -->
    <div class="admin-header">
      <div class="admin-title-block">
        <h1>{{ t('admin.title') }}</h1>
        <p class="admin-subtitle">{{ t('admin.subtitle') }}</p>
      </div>
    </div>

    <!-- Tab nav -->
    <div class="admin-tabs">
      <button
        v-for="tab in TABS"
        :key="tab"
        :class="['admin-tab-btn', { 'admin-tab-btn--active': activeTab === tab }]"
        @click="selectTab(tab)"
      >{{ t('admin.tab.' + tab) }}</button>
    </div>

    <!-- HOME TAB -->
    <template v-if="activeTab === 'home'">
      <div v-if="metricsLoading" class="stats-grid">
        <div v-for="i in 4" :key="i" class="stat-card stat-card--skeleton" />
      </div>
      <div v-else-if="metrics" class="stats-grid">
        <div v-for="key in METRIC_KEYS" :key="key" class="stat-card">
          <i :class="['pi', METRIC_ICONS[key], 'stat-icon']" />
          <div class="stat-body">
            <span class="stat-value">{{ metrics[key] }}</span>
            <span class="stat-label">{{ t('admin.metric.' + key) }}</span>
          </div>
        </div>
      </div>

      <div class="home-content">
        <div class="surface-panel home-card">
          <h2 class="section-title">{{ t('admin.home.chart') }}</h2>
          <div v-if="homeChartData" class="chart-wrapper">
            <Chart type="bar" :data="homeChartData" :options="chartOptions" />
          </div>
          <p v-else class="empty-hint">{{ t('home.noOrdersChart') }}</p>
        </div>
        <div class="surface-panel home-card">
          <h2 class="section-title">{{ t('admin.home.map') }}</h2>
          <div ref="mapEl" class="home-map" />
          <p v-if="!metricsLoading && !mapUnits.length" class="map-hint">{{ t('units.noCoords') }}</p>
        </div>
      </div>
    </template>

    <!-- ACTIVITY TAB -->
    <template v-else-if="activeTab === 'activity'">
      <div class="activity-period-row">
        <button
          v-for="p in PERIODS" :key="p"
          :class="['period-btn', { 'period-btn--active': period === p }]"
          @click="period = p"
        >{{ t('activity.panel.period.' + p) }}</button>
      </div>

      <PMessage v-if="activityError" severity="error" :closable="false" class="form-message">{{ activityError }}</PMessage>

      <div v-if="activityMetrics && !activityLoading" class="metrics-grid">
        <div
          v-for="key in ACTIVITY_METRIC_KEYS" :key="key"
          class="metric-card"
          :style="{ '--mc': ACTIVITY_METRIC_COLORS[key] }"
        >
          <div class="metric-icon-wrap"><i :class="['pi', ACTIVITY_METRIC_ICONS[key]]" /></div>
          <div class="metric-body">
            <span class="metric-value">{{ activityMetrics[key] }}</span>
            <span class="metric-label">{{ t('activity.panel.metric.' + key) }}</span>
          </div>
        </div>
        <div v-if="deliveryRate !== null" class="metric-card" style="--mc:#10b981">
          <div class="metric-icon-wrap"><i class="pi pi-chart-pie" /></div>
          <div class="metric-body">
            <span class="metric-value">{{ deliveryRate }}%</span>
            <span class="metric-label">{{ t('activity.panel.metric.deliveryRate') }}</span>
          </div>
        </div>
      </div>
      <div v-else-if="activityLoading" class="metrics-grid">
        <div v-for="i in 4" :key="i" class="metric-card metric-card--skeleton" />
      </div>

      <div v-if="!activityLoading" class="activity-content">
        <div class="surface-panel activity-chart-card">
          <h2>{{ t('activity.panel.chart.title') }}</h2>
          <div v-if="chartData" class="chart-wrapper">
            <Chart type="bar" :data="chartData" :options="chartOptions" />
          </div>
          <p v-else class="empty-hint">{{ t('activity.panel.chart.empty') }}</p>
        </div>
        <div class="surface-panel activity-ranking-card">
          <h2>{{ t('admin.ranking.title') }}</h2>
          <table v-if="companyRanking.length" class="ranking-table">
            <thead>
              <tr>
                <th>#</th>
                <th>{{ t('admin.ranking.company') }}</th>
                <th>{{ t('admin.ranking.org') }}</th>
                <th>{{ t('admin.ranking.orders') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(entry, idx) in companyRanking" :key="entry.companyId" class="ranking-row">
                <td class="ranking-pos">{{ idx + 1 }}</td>
                <td>{{ entry.companyName }}</td>
                <td><span class="text-muted">{{ entry.orgName }}</span></td>
                <td class="ranking-count">
                  <div class="count-cell">
                    <div class="count-bar-wrap">
                      <span
                        class="count-bar"
                        :style="{ width: Math.min(100, Math.max(0, entry.orderCount / companyRanking[0].orderCount * 100)) + '%' }"
                      />
                    </div>
                    {{ entry.orderCount }}
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else class="ranking-empty">{{ t('admin.ranking.empty') }}</p>
        </div>
      </div>
    </template>

    <!-- ADMINISTRATION TAB -->
    <template v-else-if="activeTab === 'administration'">
      <div class="entity-tabs">
        <button
          v-for="entity in ENTITIES"
          :key="entity"
          :class="['entity-tab-btn', { 'entity-tab-btn--active': activeEntity === entity }]"
          @click="selectEntity(entity)"
        >{{ t('admin.entity.' + entity) }}</button>
      </div>

      <PMessage v-if="entityError" severity="error" :closable="false" class="form-message">{{ entityError }}</PMessage>
      <PMessage v-if="deleteError" severity="error" :closable="false" class="form-message">{{ deleteError }}</PMessage>

      <!-- ORGANIZATIONS -->
      <template v-if="activeEntity === 'organizations'">
        <DataTable :value="organizations" :loading="entityLoading" striped-rows row-hover :rows="10" paginator>
          <template #empty><EmptyState icon="pi-building" :message="t('admin.empty')" /></template>
          <Column field="name" :header="t('admin.orgName')">
            <template #body="{ data }"><span class="entity-name">{{ data.name }}</span></template>
          </Column>
          <Column field="handle" :header="t('admin.orgHandle')">
            <template #body="{ data }"><code class="org-handle">{{ data.handle }}</code></template>
          </Column>
          <Column :header="t('admin.companies')" style="width:100px">
            <template #body="{ data }"><PTag :value="String(data.companyCount)" severity="secondary" /></template>
          </Column>
          <Column :header="t('admin.workers')" style="width:100px">
            <template #body="{ data }"><PTag :value="String(data.workerCount)" severity="info" /></template>
          </Column>
          <Column :header="t('admin.orders')" style="width:100px">
            <template #body="{ data }"><PTag :value="String(data.orderCount)" severity="warn" /></template>
          </Column>
          <Column :header="t('admin.createdAt')" style="width:130px;white-space:nowrap">
            <template #body="{ data }">{{ formatDate(data.createdAt) }}</template>
          </Column>
          <Column style="width:60px">
            <template #body="{ data }">
              <template v-if="deleteConfirmId === data.id">
                <div class="delete-inline">
                  <span class="delete-inline-label">{{ t('admin.deleteOrg') }}</span>
                  <PButton icon="pi pi-check" severity="danger" size="small" :loading="deleteLoading" @click="executeDelete" />
                  <PButton icon="pi pi-times" severity="secondary" size="small" outlined @click="deleteConfirmId = null" />
                </div>
              </template>
              <PButton v-else icon="pi pi-times" severity="danger" text size="small" class="delete-btn" @click="confirmDelete(data.id)" />
            </template>
          </Column>
        </DataTable>
      </template>

      <!-- COMPANIES -->
      <template v-else-if="activeEntity === 'companies'">
        <DataTable :value="companies" :loading="entityLoading" striped-rows row-hover :rows="10" paginator>
          <template #empty><EmptyState icon="pi-briefcase" :message="t('admin.emptyCompanies')" /></template>
          <Column field="name" :header="t('admin.orgName')">
            <template #body="{ data }"><span class="entity-name">{{ data.name }}</span></template>
          </Column>
          <Column :header="t('fields.organization')">
            <template #body="{ data }"><span class="text-muted">{{ data.orgName }}</span></template>
          </Column>
          <Column :header="t('admin.workers')" style="width:100px">
            <template #body="{ data }"><PTag :value="String(data.workerCount)" severity="info" /></template>
          </Column>
          <Column :header="t('admin.orders')" style="width:100px">
            <template #body="{ data }"><PTag :value="String(data.orderCount)" severity="warn" /></template>
          </Column>
          <Column :header="t('admin.createdAt')" style="width:130px;white-space:nowrap">
            <template #body="{ data }">{{ formatDate(data.createdAt) }}</template>
          </Column>
          <Column style="width:60px">
            <template #body="{ data }">
              <template v-if="deleteConfirmId === data.id">
                <div class="delete-inline">
                  <span class="delete-inline-label">{{ t('admin.deleteCompany') }}</span>
                  <PButton icon="pi pi-check" severity="danger" size="small" :loading="deleteLoading" @click="executeDelete" />
                  <PButton icon="pi pi-times" severity="secondary" size="small" outlined @click="deleteConfirmId = null" />
                </div>
              </template>
              <PButton v-else icon="pi pi-times" severity="danger" text size="small" class="delete-btn" @click="confirmDelete(data.id)" />
            </template>
          </Column>
        </DataTable>
      </template>

      <!-- ORDERS -->
      <template v-else-if="activeEntity === 'orders'">
        <DataTable :value="orders" :loading="entityLoading" striped-rows row-hover :rows="10" paginator>
          <template #empty><EmptyState icon="pi-send" :message="t('admin.emptyOrders')" /></template>
          <Column field="reference" :header="t('orders.reference')">
            <template #body="{ data }"><code class="org-handle">{{ data.reference }}</code></template>
          </Column>
          <Column :header="t('orders.statusLabel')">
            <template #body="{ data }"><PTag :value="t('orders.status.' + data.status)" /></template>
          </Column>
          <Column :header="t('admin.companyName')">
            <template #body="{ data }"><span class="text-muted">{{ data.companyName }}</span></template>
          </Column>
          <Column :header="t('fields.organization')">
            <template #body="{ data }"><span class="text-muted">{{ data.orgName }}</span></template>
          </Column>
          <Column :header="t('admin.createdAt')" style="width:130px;white-space:nowrap">
            <template #body="{ data }">{{ formatDate(data.createdAt) }}</template>
          </Column>
          <Column style="width:60px">
            <template #body="{ data }">
              <template v-if="deleteConfirmId === data.id">
                <div class="delete-inline">
                  <span class="delete-inline-label">{{ t('admin.deleteOrder') }}</span>
                  <PButton icon="pi pi-check" severity="danger" size="small" :loading="deleteLoading" @click="executeDelete" />
                  <PButton icon="pi pi-times" severity="secondary" size="small" outlined @click="deleteConfirmId = null" />
                </div>
              </template>
              <PButton v-else icon="pi pi-times" severity="danger" text size="small" class="delete-btn" @click="confirmDelete(data.id)" />
            </template>
          </Column>
        </DataTable>
      </template>

      <!-- USERS -->
      <template v-else-if="activeEntity === 'users'">
        <DataTable :value="users" :loading="entityLoading" striped-rows row-hover :rows="10" paginator>
          <template #empty><EmptyState icon="pi-user" :message="t('admin.emptyUsers')" /></template>
          <Column field="email" :header="t('admin.email')">
            <template #body="{ data }"><span class="entity-name">{{ data.email }}</span></template>
          </Column>
          <Column :header="t('fields.firstName')">
            <template #body="{ data }">{{ data.firstName }} {{ data.lastName }}</template>
          </Column>
          <Column :header="t('admin.isWorker')" style="width:100px">
            <template #body="{ data }">
              <PTag v-if="data.isWorker" :value="t('admin.isWorker')" severity="info" />
            </template>
          </Column>
          <Column :header="t('admin.createdAt')" style="width:130px;white-space:nowrap">
            <template #body="{ data }">{{ formatDate(data.createdAt) }}</template>
          </Column>
          <Column style="width:60px">
            <template #body="{ data }">
              <template v-if="!data.isGlobalAdmin">
                <template v-if="deleteConfirmId === data.id">
                  <div class="delete-inline">
                    <span class="delete-inline-label">{{ t('admin.deleteUser') }}</span>
                    <PButton icon="pi pi-check" severity="danger" size="small" :loading="deleteLoading" @click="executeDelete" />
                    <PButton icon="pi pi-times" severity="secondary" size="small" outlined @click="deleteConfirmId = null" />
                  </div>
                </template>
                <PButton v-else icon="pi pi-times" severity="danger" text size="small" class="delete-btn" @click="confirmDelete(data.id)" />
              </template>
            </template>
          </Column>
        </DataTable>
      </template>

      <!-- WORKERS -->
      <template v-else-if="activeEntity === 'workers'">
        <DataTable :value="workers" :loading="entityLoading" striped-rows row-hover :rows="10" paginator>
          <template #empty><EmptyState icon="pi-id-card" :message="t('admin.emptyWorkers')" /></template>
          <Column field="email" :header="t('admin.email')">
            <template #body="{ data }"><span class="entity-name">{{ data.email }}</span></template>
          </Column>
          <Column :header="t('admin.role')">
            <template #body="{ data }"><PTag :value="t('workers.roles.' + data.role)" /></template>
          </Column>
          <Column :header="t('admin.companyName')">
            <template #body="{ data }"><span class="text-muted">{{ data.companyName }}</span></template>
          </Column>
          <Column :header="t('fields.organization')">
            <template #body="{ data }"><span class="text-muted">{{ data.orgName }}</span></template>
          </Column>
          <Column :header="t('admin.createdAt')" style="width:130px;white-space:nowrap">
            <template #body="{ data }">{{ formatDate(data.createdAt) }}</template>
          </Column>
          <Column style="width:60px">
            <template #body="{ data }">
              <template v-if="deleteConfirmId === data.id">
                <div class="delete-inline">
                  <span class="delete-inline-label">{{ t('admin.deleteWorker') }}</span>
                  <PButton icon="pi pi-check" severity="danger" size="small" :loading="deleteLoading" @click="executeDelete" />
                  <PButton icon="pi pi-times" severity="secondary" size="small" outlined @click="deleteConfirmId = null" />
                </div>
              </template>
              <PButton v-else icon="pi pi-times" severity="danger" text size="small" class="delete-btn" @click="confirmDelete(data.id)" />
            </template>
          </Column>
        </DataTable>
      </template>
    </template>

    <!-- SETTINGS TAB -->
    <template v-else-if="activeTab === 'settings'">
      <div class="settings-section">
        <div class="settings-card danger-zone">
          <div class="settings-card-header">
            <i class="pi pi-exclamation-triangle settings-danger-icon" />
            <div>
              <h2>{{ t('admin.resetTitle') }}</h2>
              <p class="settings-hint">{{ t('admin.resetHint') }}</p>
            </div>
          </div>

          <PMessage v-if="resetError" severity="error" :closable="false" class="form-message">{{ resetError }}</PMessage>
          <PMessage v-if="resetSuccess" severity="success" :closable="false" class="form-message">{{ t('admin.resetSuccess') }}</PMessage>

          <template v-if="!resetConfirming">
            <PButton
              :label="t('admin.resetConfirm')"
              icon="pi pi-trash"
              severity="danger"
              outlined
              @click="resetConfirming = true; resetSuccess = false"
            />
          </template>
          <template v-else>
            <div class="reset-confirm-row">
              <span class="reset-confirm-label">{{ t('admin.resetConfirm') }}?</span>
              <PButton
                :label="resetLoading ? t('common.loading') : t('admin.resetConfirm')"
                icon="pi pi-check"
                severity="danger"
                :loading="resetLoading"
                @click="executeReset"
              />
              <PButton
                :label="t('common.cancel')"
                icon="pi pi-times"
                severity="secondary"
                outlined
                :disabled="resetLoading"
                @click="resetConfirming = false"
              />
            </div>
          </template>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped src="./AdminDashboardView.css"></style>
