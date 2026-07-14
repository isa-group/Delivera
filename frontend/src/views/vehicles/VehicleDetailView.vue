<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useFormatDate } from '@/composables/useFormatDate'
import { useServices } from '@/composables/useServices'

const { t } = useI18n()
const { formatDate } = useFormatDate()
const route = useRoute()
const router = useRouter()
const api = useServices("data-service")
const auth = useAuthStore()

const vehicle = ref(null)
const loading = ref(false)
const error = ref('')

const isAdmin = () => auth.role === 'COMPANY_ADMIN'

let jsonLdEl = null

watch(vehicle, (val) => {
  if (jsonLdEl) { jsonLdEl.remove(); jsonLdEl = null }
  if (!val) return
  jsonLdEl = document.createElement('script')
  jsonLdEl.type = 'application/ld+json'
  jsonLdEl.textContent = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'Vehicle',
    name: val.plate,
    vehicleIdentificationNumber: val.id,
    cargoVolume: { '@type': 'QuantitativeValue', value: val.capacity, unitCode: 'KGM' },
    vehicleConfiguration: val.depotName,
  })
  document.head.appendChild(jsonLdEl)
})

onUnmounted(() => {
  if (jsonLdEl) { jsonLdEl.remove(); jsonLdEl = null }
})

async function load() {
  loading.value = true
  try {
    const res = await api.get(`/vehicles/${route.params.id}`)
    if (res.ok) vehicle.value = await res.json()
    else if (res.status === 404) error.value = t('vehicles.notFound')
    else error.value = t('error.connection')
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="surface-card vehicle-detail-page card-full" :aria-labelledby="vehicle ? 'vehicle-detail-title' : undefined">

    <div v-if="loading" class="loading-state" style="padding:48px;text-align:center" role="status" aria-live="polite">
      <i class="pi pi-spin pi-spinner" style="font-size:24px" aria-hidden="true" />
      <span class="visually-hidden">{{ t('common.loading') }}</span>
    </div>

    <div v-else-if="error" role="alert">
      <PMessage severity="error" :closable="false" style="margin:24px">{{ error }}</PMessage>
    </div>

    <article v-else-if="vehicle" class="vehicle-detail-split" aria-label="vehicle-info">
      <div class="vehicle-detail-panel">
        <nav class="detail-actions" aria-label="breadcrumb">
          <PButton
            type="button"
            text
            severity="secondary"
            icon="pi pi-arrow-left"
            :aria-label="t('common.back')"
            @click="router.push('/vehicles')"
          />
          <PButton
            v-if="isAdmin()"
            type="button"
            icon="pi pi-pencil"
            severity="secondary"
            outlined
            size="small"
            :label="t('vehicles.edit')"
            :aria-label="`${t('vehicles.edit')}: ${vehicle.plate}`"
            @click="router.push(`/vehicles/${vehicle.id}/edit`)"
          />
        </nav>

        <div class="detail-header">
          <div class="detail-icon" aria-hidden="true">
            <i class="pi pi-truck" aria-hidden="true" />
          </div>
          <div>
            <h1 id="vehicle-detail-title" class="detail-title">{{ vehicle.plate }}</h1>
            <p class="detail-subtitle">{{ vehicle.depotName }}</p>
          </div>
        </div>

        <dl class="info-grid">
          <div class="info-item">
            <dt class="info-label">{{ t('vehicles.plate') }}</dt>
            <dd class="info-value plate-display">{{ vehicle.plate }}</dd>
          </div>
          <div class="info-item">
            <dt class="info-label">{{ t('vehicles.capacity') }}</dt>
            <dd class="info-value">
              <span class="capacity-pill">{{ vehicle.capacity }}</span>
            </dd>
          </div>
          <div class="info-item info-item--full">
            <dt class="info-label">{{ t('vehicles.depot') }}</dt>
            <dd class="info-value">{{ vehicle.depotName || '—' }}</dd>
          </div>
          <div class="info-item info-item--full">
            <dt class="info-label">{{ t('loyalUsers.since') }}</dt>
            <dd class="info-value">
              <time :datetime="vehicle.createdAt">{{ formatDate(vehicle.createdAt) || '—' }}</time>
            </dd>
          </div>
        </dl>
      </div>

      <aside class="vehicle-detail-visual" aria-label="vehicle-summary">
        <div class="visual-hero">
          <div class="hero-circle" aria-hidden="true">
            <i class="pi pi-truck hero-truck" aria-hidden="true" />
          </div>
          <div class="hero-plate">{{ vehicle.plate }}</div>
          <div class="hero-stats">
            <div class="hero-stat">
              <span class="hero-stat-value">{{ vehicle.capacity }}</span>
              <span class="hero-stat-label">{{ t('vehicles.capacity') }}</span>
            </div>
            <div class="hero-stat-divider" aria-hidden="true" />
            <div class="hero-stat">
              <span class="hero-stat-value">{{ vehicle.depotName || '—' }}</span>
              <span class="hero-stat-label">{{ t('vehicles.depot') }}</span>
            </div>
          </div>
        </div>
      </aside>
    </article>
  </main>
</template>

<style scoped src="./VehicleDetailView.css"></style>
