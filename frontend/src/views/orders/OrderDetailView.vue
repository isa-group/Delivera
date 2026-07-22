<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useAppConfig } from '@/composables/useAppConfig'
import { useFormatDate } from '@/composables/useFormatDate'
import TimelineList from '@/components/TimelineList.vue'
import { createMap, addMarker, addRoute, fitBounds, currentLocationOf } from '@/composables/useDeliveraMap'
import { WORKER_ROLES } from '@/constants/roles'
import { useServices } from '@/composables/useServices'

const { t } = useI18n()
const { formatDateTime } = useFormatDate()
const route = useRoute()
const router = useRouter()
const dataApi = useServices("data-service")
const auth = useAuthStore()
const { load: loadConfig, statusSeverity, prioritySeverity, getNextStatuses } = useAppConfig()

const order = ref(null)
const loading = ref(false)
const error = ref('')
const updating = ref(false)
const updateError = ref('')
const updateSuccess = ref('')
const newStatus = ref('')
const statusNote = ref('')
const activeTab = ref(0)
watch(activeTab, () => { newStatus.value = ''; statusNote.value = '' })

const messages = ref([])
const chatLoading = ref(false)
const newMessage = ref('')
const sendingMessage = ref(false)

const mapEl = ref(null)
let map = null
let mapInvalidateTimer = null
const routeFailed = ref(false)

const hasMap = computed(() =>
  order.value?.originLat != null && order.value?.originLon != null
)

function destKind(o) {
  if (o.destinationId) {
    return o.destinationCompanyId && auth.companyId && o.destinationCompanyId === auth.companyId
      ? 'OWN_UNIT' : 'OTHER_UNIT'
  }
  return 'CUSTOMER'
}
function destNav(o) {
  if (o.destinationId && o.destinationCompanyId && auth.companyId && o.destinationCompanyId === auth.companyId) {
    return `/units/${o.destinationId}`
  }
  if (!o.destinationId && o.loyalUserId) return `/loyal-users/${o.loyalUserId}`
  return null
}

async function addDestinationToMap(mapInstance, o, bounds, originLat, originLon) {
  const destLat = Number.parseFloat(o.destinationLat)
  const destLon = Number.parseFloat(o.destinationLon)
  const kind = destKind(o)
  const navTo = destNav(o)
  const navLabel = navTo ? t('loyalUsers.detail') : null
  const destActionLabel = kind === 'OWN_UNIT' ? t('units.detail') : navLabel
  addMarker(mapInstance, {
    id: o.destinationId || o.loyalUserId || 'customer',
    lat: destLat, lon: destLon, kind,
    title: o.destinationName || o.recipientName || o.recipientEmail || '',
    subtitle: o.destinationCompanyName || (kind === 'CUSTOMER' ? t('orders.recipientName') : ''),
    actionLabel: destActionLabel,
    navigateTo: navTo,
    router,
  }).addTo(mapInstance)
  bounds.push([destLat, destLon])
  const entry = await addRoute(mapInstance, {
    orderId: o.id,
    origin: { lat: originLat, lon: originLon },
    dest:   { lat: destLat, lon: destLon },
    popupTitle: o.reference,
    popupSubtitle: `${o.originName} → ${o.destinationName || o.recipientName || o.recipientEmail || ''}`,
    actionLabel: t('orders.viewDetail'),
    router,
    status: o.status,
    currentLocation: currentLocationOf(o),
  })
  if (entry && !entry.solid) routeFailed.value = true
  try { entry?.layer?.bringToFront?.() } catch { /* layer aún sin parentNode si el panel no tiene tamaño todavía */ }
}

async function initOrderMap() {
  if (!mapEl.value || !order.value?.originLat) return
  if (map) { map.remove(); map = null }

  const o = order.value
  const originLat = Number.parseFloat(o.originLat)
  const originLon = Number.parseFloat(o.originLon)

  map = createMap(mapEl.value)
  // Forzar a Leaflet a recalcular el tamaño antes de añadir capas: el panel
  // del grid puede haberse mostrado tras el v-if y aún tener dimensiones cero.
  map.invalidateSize()

  const bounds = [[originLat, originLon]]
  const ownOrigin = o.originCompanyId && auth.companyId && o.originCompanyId === auth.companyId
  addMarker(map, {
    id: o.originId, lat: originLat, lon: originLon,
    kind: ownOrigin ? 'OWN_UNIT' : 'OTHER_UNIT',
    title: o.originName,
    subtitle: o.originCompanyName || '',
    actionLabel: t('units.detail'),
    navigateTo: ownOrigin ? `/units/${o.originId}` : null,
    router,
  }).addTo(map)

  if (o.destinationLat != null && o.destinationLon != null) {
    await addDestinationToMap(map, o, bounds, originLat, originLon)
  }

  fitBounds(map, bounds)
  // Leaflet a veces calcula mal el tamaño cuando el contenedor acaba de montarse.
  clearTimeout(mapInvalidateTimer)
  mapInvalidateTimer = setTimeout(() => { if (map) map.invalidateSize() }, 100)
}

async function loadMessages(orderId) {
  chatLoading.value = true
  try {
    const res = await dataApi.get(`/orders/${orderId}/messages`)
    if (res.ok) messages.value = await res.json()
  } catch { /* silent */ } finally {
    chatLoading.value = false
  }
}

async function sendMessage() {
  const text = newMessage.value.trim()
  if (!text) return
  sendingMessage.value = true
  try {
    const res = await dataApi.post(`/orders/${order.value.id}/messages`, { content: text })
    if (res.ok) {
      messages.value.push(await res.json())
      newMessage.value = ''
    }
  } catch { /* silent */ } finally {
    sendingMessage.value = false
  }
}

const viteEnvBase = `${import.meta.env.VITE_APP_URL || globalThis.location?.origin || ''}`

async function copyTrackingUrl(token) {
  try {
    await navigator.clipboard.writeText(`${viteEnvBase}/track/${token}`)
  } catch {
    // clipboard not available (non-secure context or permission denied)
  }
}

const availableNextStatuses = computed(() => order.value ? getNextStatuses(order.value.status) : [])
const canUpdateStatus = computed(() => WORKER_ROLES.includes(auth.role))

async function loadOrder() {
  loading.value = true
  try {
    const res = await dataApi.get(`/orders/${route.params.id}`)
    if (res.ok) {
      order.value = await res.json()
      loadMessages(order.value.id)
      console.log(order.value)
    } else error.value = t('error.ORDER_NOT_FOUND')
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  loadConfig()
  await loadOrder()
  await nextTick()
  try { await initOrderMap() } catch (e) { console.error('initOrderMap failed', e) }
})

onBeforeRouteLeave(() => {
  clearTimeout(mapInvalidateTimer)
  if (map) { map.remove(); map = null }
})

onUnmounted(() => {
  clearTimeout(mapInvalidateTimer)
  if (map) { map.remove(); map = null }
})

async function submitStatusUpdate() {
  if (!newStatus.value) return
  updating.value = true
  updateError.value = ''
  updateSuccess.value = ''
  try {
    const res = await dataApi.patch(`/orders/${order.value.id}/status`, {
      status: newStatus.value,
      note: statusNote.value || null,
    })
    if (res.ok) {
      order.value = await res.json()
      updateSuccess.value = t('orders.statusUpdated')
      newStatus.value = ''
      statusNote.value = ''
    } else {
      const data = await res.json()
      updateError.value = dataApi.translateError(data, 'error.saveFailed')
    }
  } catch {
    updateError.value = t('error.connection')
  } finally {
    updating.value = false
  }
}
</script>

<template>
  <div class="surface-card order-detail-page card-full">
    <div v-if="loading" class="loading-state">
      <i class="pi pi-spin pi-spinner" style="font-size:24px" />
    </div>

    <PMessage v-else-if="error" severity="error" :closable="false">{{ error }}</PMessage>

    <div v-else-if="order" class="order-detail-split">
      <div class="order-detail-panel">
        <PButton
          type="button"
          text
          severity="secondary"
          icon="pi pi-arrow-left"
          class="detail-back-btn"
          @click="router.push('/orders')"
        />

        <h1 class="detail-title">{{ order.reference }}</h1>

        <div class="detail-badges">
          <div class="badge-group">
            <span class="info-label">{{ t('orders.statusLabel') }}</span>
            <PTag :value="t(`orders.status.${order.status}`)" :severity="statusSeverity[order.status]" />
          </div>
          <div class="badge-group">
            <span class="info-label">{{ t('orders.priorityLabel') }}</span>
            <PTag :value="t(`orders.priority.${order.priority}`)" :severity="prioritySeverity[order.priority]" />
          </div>
        </div>

        <PTabs v-model:value="activeTab">
          <PTabList>
            <PTab :value="0">{{ t('orders.details') }}</PTab>
            <PTab :value="1">{{ t('orders.timeline') }}</PTab>
            <PTab :value="2">{{ t('orders.chat') }}</PTab>
          </PTabList>

          <PTabPanels>
            <PTabPanel :value="0">
              <div class="info-grid">
                <div class="info-item">
                  <span class="info-label">{{ t('orders.origin') }}</span>
                  <span class="info-value">{{ order.originName }}</span>
                  <span class="info-sub">{{ order.originCompanyName }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">{{ order.destinationName ? t('orders.destination') : t('tracking.recipient') }}</span>
                  <span class="info-value">{{ order.destinationName || order.recipientName || order.recipientEmail }}</span>
                  <span v-if="order.destinationCompanyName" class="info-sub">{{ order.destinationCompanyName }}</span>
                  <span v-else-if="order.recipientName && order.recipientEmail" class="info-sub">{{ order.recipientEmail }}</span>
                </div>
                <div class="info-item info-item--full">
                  <span class="info-label">{{ t('orders.date') }}</span>
                  <span class="info-value">{{ formatDateTime(order.createdAt) }}</span>
                </div>
                <div v-if="order.notes" class="info-item info-item--full">
                  <span class="info-label">{{ t('orders.notes') }}</span>
                  <span class="info-value info-notes">{{ order.notes }}</span>
                </div>
              </div>

              <div v-if="order.trackingToken && !order.claimed" class="tracking-section">
                <div class="tracking-header">
                  <i class="pi pi-link" />
                  <div>
                    <div class="tracking-label">{{ t('orders.trackingUrl') }}</div>
                    <div class="tracking-hint">{{ t('orders.trackingUrlHint') }}</div>
                  </div>
                </div>
                <div class="tracking-url-row">
                  <code class="tracking-url">{{ `${viteEnvBase}/track/${order.trackingToken}` }}</code>
                  <PButton
                    icon="pi pi-copy"
                    text
                    severity="secondary"
                    size="small"
                    @click="copyTrackingUrl(order.trackingToken)"
                  />
                </div>
              </div>
              <div v-else-if="order.trackingToken && order.claimed" class="claimed-section">
                <i class="pi pi-check-circle" />
                <div>
                  <div class="claimed-label">{{ t('orders.claimed') }}</div>
                  <div class="claimed-hint">{{ t('orders.claimedHint') }}</div>
                </div>
              </div>
            </PTabPanel>

            <PTabPanel :value="1">
              <TimelineList :events="order.events" :show-author="true" />

              <div v-if="canUpdateStatus && availableNextStatuses.length" class="status-section">
                <div class="timeline-divider" />
                <p class="status-section-title">{{ t('orders.updateStatus') }}</p>
                <div class="status-form">
                  <div class="status-field">
                    <label for="order-new-status" class="info-label">{{ t('orders.newStatus') }}</label>
                    <PSelect
                      input-id="order-new-status"
                      v-model="newStatus"
                      :options="availableNextStatuses.map(s => ({ label: t(`orders.status.${s}`), value: s }))"
                      option-label="label"
                      option-value="value"
                      :placeholder="t('orders.newStatus')"
                      style="width:100%"
                    />
                  </div>
                  <div class="status-field">
                    <label for="order-status-note" class="info-label">{{ t('orders.statusNote') }}</label>
                    <PTextarea
                      id="order-status-note"
                      v-model="statusNote"
                      :placeholder="t('orders.statusNotePlaceholder')"
                      rows="2"
                      style="width:100%"
                    />
                  </div>
                  <PButton
                    :label="updating ? t('common.loading') : t('orders.updateStatus')"
                    icon="pi pi-sync"
                    :loading="updating"
                    :disabled="!newStatus"
                    @click="submitStatusUpdate"
                  />
                </div>
                <PMessage v-if="updateError" severity="error" :closable="false" class="status-msg">{{ updateError }}</PMessage>
                <PMessage v-if="updateSuccess" severity="success" :closable="false" class="status-msg">{{ updateSuccess }}</PMessage>
              </div>
            </PTabPanel>

            <PTabPanel :value="2">
              <div class="chat-panel" data-testid="chat-panel">
                <div class="chat-messages" data-testid="chat-messages">
                  <div v-if="chatLoading" class="chat-empty">
                    <i class="pi pi-spin pi-spinner" />
                  </div>
                  <div v-else-if="messages.length === 0" class="chat-empty">{{ t('orders.chatEmpty') }}</div>
                  <div
                    v-for="msg in messages"
                    :key="msg.id"
                    class="chat-message"
                    :data-testid="`chat-message-${msg.id}`"
                  >
                    <div class="chat-message-header">
                      <span class="chat-sender">{{ msg.senderName }}</span>
                      <span class="chat-time">{{ formatDateTime(msg.createdAt) }}</span>
                    </div>
                    <p class="chat-content">{{ msg.content }}</p>
                  </div>
                </div>
                <div class="chat-input-row">
                  <PTextarea
                    v-model="newMessage"
                    :placeholder="t('orders.chatPlaceholder')"
                    rows="2"
                    class="chat-textarea"
                    @keydown.enter.exact.prevent="sendMessage"
                  />
                  <PButton
                    icon="pi pi-send"
                    :loading="sendingMessage"
                    :disabled="!newMessage.trim()"
                    data-testid="chat-send"
                    @click="sendMessage"
                  />
                </div>
              </div>
            </PTabPanel>
          </PTabPanels>
        </PTabs>
      </div>

      <div class="order-map-panel">
        <div v-if="hasMap" ref="mapEl" class="order-map-el" />
        <div v-else class="map-no-coords">
          <i class="pi pi-map-marker" style="font-size:28px;color:#cbd5e1;margin-bottom:8px" />
          <p>{{ t('orders.noMapCoords') }}</p>
        </div>
        <div v-if="routeFailed" class="route-failed-msg">
          <i class="pi pi-exclamation-triangle" />
          {{ t('orders.routeUnavailable') }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped src="./OrderDetailView.css"></style>
