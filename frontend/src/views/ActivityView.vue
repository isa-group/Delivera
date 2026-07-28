<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import Chart from 'primevue/chart'
import { useServices } from '@/composables/useServices'

const { t } = useI18n()
const api = useApi()
const dataApi = useServices("data-service")
const router = useRouter()

const period = ref('MONTH')
const metrics = ref(null)
const chartData = ref(null)
const unitRanking = ref([])
const loading = ref(false)
const error = ref('')

const PERIODS = ['TODAY', 'WEEK', 'MONTH']

const METRIC_KEYS = ['totalOrders', 'completedOrders', 'cancelledOrders', 'activeOrders', 'newLoyalUsers']
const METRIC_ICONS = {
  totalOrders: 'pi-send',
  completedOrders: 'pi-check-circle',
  cancelledOrders: 'pi-times-circle',
  activeOrders: 'pi-sync',
  newLoyalUsers: 'pi-user-plus',
}
const METRIC_COLORS = {
  totalOrders: '#7c3aed',
  completedOrders: '#22c55e',
  cancelledOrders: '#ef4444',
  activeOrders: '#3b82f6',
  newLoyalUsers: '#f59e0b',
}

const deliveryRate = computed(() => {
  if (!metrics.value || !metrics.value.totalOrders) return null
  return Math.round((metrics.value.completedOrders / metrics.value.totalOrders) * 100)
})

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      position: 'bottom',
      labels: { boxWidth: 12, font: { size: 11 } }
    },
    onClick: (_e, elements) => {
      if (elements.length > 0) router.push('/orders')
    },
  },
  onClick: (_e, elements) => {
    if (elements.length > 0) router.push('/orders')
  },
  scales: {
    x: { stacked: true, grid: { display: false }, ticks: { font: { size: 10 } } },
    y: { stacked: true, beginAtZero: true, grace: '15%', ticks: { stepSize: 1, precision: 0 } },
  },
  layout: { padding: { top: 12 } },
}))

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


async function load() {
  loading.value = true
  error.value = ''
  chartData.value = null
  unitRanking.value = []
  try {
    const [metricsRes, chartRes, rankingRes,loyalUserMetricsRes] = await Promise.all([
      dataApi.get(`/activity/metrics?period=${period.value}`),
      dataApi.get(`/activity/orders-by-day?period=${period.value}`),
      dataApi.get(`/activity/unit-ranking?period=${period.value}`),
      api.get(`/activity/metrics?period=${period.value}`),
    ])
    if (metricsRes.ok) {
      metrics.value = await metricsRes.json()
      if (loyalUserMetricsRes.ok){
        const countLoyalUser = await loyalUserMetricsRes.json()
        metrics.value.newLoyalUsers = countLoyalUser?.newLoyalUsers
      }
    } else{
      error.value = t('error.connection')
    } 
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

    if (rankingRes.ok) unitRanking.value = await rankingRes.json()
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}

function selectPeriod(p) {
  period.value = p
  load()
}

onMounted(load)
</script>

<template>
  <div class="surface-card card-full activity-view">
    <!-- Header -->
    <div class="activity-header">
      <div>
        <h1>{{ t('activity.panel.title') }}</h1>
        <p class="activity-subtitle">{{ t('activity.panel.subtitle') }}</p>
      </div>
      <div class="period-tabs">
        <button
          v-for="p in PERIODS" :key="p"
          :class="['period-btn', { 'period-btn--active': period === p }]"
          @click="selectPeriod(p)"
        >{{ t('activity.panel.period.' + p) }}</button>
      </div>
    </div>

    <PMessage v-if="error" severity="error" :closable="false" class="form-message">{{ error }}</PMessage>

    <!-- Métricas principales -->
    <div v-if="metrics && !loading" class="metrics-grid">
      <div v-for="key in METRIC_KEYS" :key="key" class="metric-card" :style="{ '--mc': METRIC_COLORS[key] }">
        <div class="metric-icon-wrap">
          <i :class="['pi', METRIC_ICONS[key]]" />
        </div>
        <div class="metric-body">
          <span class="metric-value">{{ metrics[key] }}</span>
          <span class="metric-label">{{ t('activity.panel.metric.' + key) }}</span>
        </div>
      </div>

      <!-- Tasa de entrega -->
      <div v-if="deliveryRate !== null" class="metric-card" style="--mc:#10b981">
        <div class="metric-icon-wrap">
          <i class="pi pi-chart-pie" />
        </div>
        <div class="metric-body">
          <span class="metric-value">{{ deliveryRate }}%</span>
          <span class="metric-label">{{ t('activity.panel.metric.deliveryRate') }}</span>
          <PProgressBar :value="deliveryRate" class="rate-bar" />
        </div>
      </div>
    </div>

    <!-- Skeleton métricas -->
    <div v-else-if="loading" class="metrics-grid">
      <div v-for="i in 6" :key="i" class="metric-card metric-card--skeleton" />
    </div>

    <!-- Gráfica + Ranking en dos columnas -->
    <div v-if="!loading" class="activity-content">
      <div v-if="chartData" class="surface-panel activity-chart-card">
        <h2>{{ t('activity.panel.chart.title') }}</h2>
        <div class="chart-wrapper">
          <Chart type="bar" :data="chartData" :options="chartOptions" />
        </div>
        <p v-if="!chartData.labels.length" class="chart-empty">{{ t('activity.panel.chart.empty') }}</p>
      </div>

      <div class="surface-panel activity-ranking-card">
        <h2>{{ t('activity.panel.ranking.title') }}</h2>
        <table v-if="unitRanking.length" class="ranking-table">
          <thead>
            <tr>
              <th>#</th>
              <th>{{ t('activity.panel.ranking.unit') }}</th>
              <th>{{ t('activity.panel.ranking.type') }}</th>
              <th>{{ t('activity.panel.ranking.orders') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(entry, idx) in unitRanking" :key="entry.unitId"
              class="ranking-row"
              style="cursor:pointer"
              @click="router.push('/units/' + entry.unitId)"
            >
              <td class="ranking-pos">{{ idx + 1 }}</td>
              <td>{{ entry.unitName }}</td>
              <td>
                <PTag :value="t('units.' + entry.unitType)" severity="secondary" />
              </td>
              <td class="ranking-count">
                <div class="count-cell">
                  <div class="count-bar-wrap">
                    <span class="count-bar" :style="{ width: Math.min(100, Math.max(0, entry.orderCount / unitRanking[0].orderCount * 100)) + '%' }" />
                  </div>
                  {{ entry.orderCount }}
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="ranking-empty">{{ t('activity.panel.ranking.empty') }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped src="./ActivityView.css"></style>
