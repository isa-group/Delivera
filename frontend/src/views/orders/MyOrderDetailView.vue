<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppConfig } from '@/composables/useAppConfig'
import { fetchPublicOrder, useApi } from '@/composables/useApi'
import { useFormatDate } from '@/composables/useFormatDate'
import TimelineList from '@/components/TimelineList.vue'
import { createMap, addMarker, addRoute, fitBounds, currentLocationOf } from '@/composables/useDeliveraMap'

const { t } = useI18n()
const { formatDateTime } = useFormatDate()
const route = useRoute()
const router = useRouter()
const api = useApi()
const { load: loadConfig, statusSeverity } = useAppConfig()

const order = ref(null)
const loading = ref(false)
const error = ref('')
const activeTab = ref(0)

const messages = ref([])
const newMessage = ref('')
const sendingMessage = ref(false)

const mapEl = ref(null)
let map = null
let mapInvalidateTimer = null

const hasMap = computed(() =>
  order.value?.originLat != null && order.value?.originLon != null
    && order.value?.destinationLat != null && order.value?.destinationLon != null
)

async function initMap() {
  await nextTick()
  if (!mapEl.value || !hasMap.value) return
  if (map) { map.remove(); map = null }

  const o = order.value
  const originLat = Number.parseFloat(o.originLat)
  const originLon = Number.parseFloat(o.originLon)
  const destLat = Number.parseFloat(o.destinationLat)
  const destLon = Number.parseFloat(o.destinationLon)

  map = createMap(mapEl.value)
  map.invalidateSize()
  fitBounds(map, [[originLat, originLon], [destLat, destLon]])

  addMarker(map, {
    id: 'origin', lat: originLat, lon: originLon, kind: 'OTHER_UNIT',
    title: o.originName, subtitle: o.companyName || '',
    actionLabel: null, navigateTo: null, router,
  }).addTo(map)

  addMarker(map, {
    id: 'dest', lat: destLat, lon: destLon, kind: 'SELF',
    title: o.destinationName || o.recipientName || t('tracking.destination'),
    subtitle: o.recipientAddress || '',
    actionLabel: null, navigateTo: null, router,
  }).addTo(map)

  const entry = await addRoute(map, {
    orderId: o.id,
    origin: { lat: originLat, lon: originLon },
    dest:   { lat: destLat, lon: destLon },
    popupTitle: o.reference,
    popupSubtitle: `${o.originName} → ${o.destinationName || o.recipientName || ''}`,
    actionLabel: null,
    router,
    status: o.status,
    currentLocation: currentLocationOf(o),
  })
  entry?.layer?.bringToFront?.()

  clearTimeout(mapInvalidateTimer)
  mapInvalidateTimer = setTimeout(() => { if (map) map.invalidateSize() }, 100)
}

async function fetchOrder() {
  const reference = route.query.q
  if (!reference) { error.value = t('tracking.notFound'); return }
  loading.value = true
  try {
    order.value = await fetchPublicOrder(reference)
    loadMessages()
  } catch (e) {
    error.value = e.message === 'not_found' ? t('tracking.notFound') : t('error.connection')
  } finally {
    loading.value = false
  }
  if (order.value) initMap()
}

onBeforeRouteLeave(() => {
  clearTimeout(mapInvalidateTimer)
  if (map) { map.remove(); map = null }
})

onUnmounted(() => {
  clearTimeout(mapInvalidateTimer)
  if (map) { map.remove(); map = null }
})

async function loadMessages() {
  if (!order.value?.id) return
  try {
    const res = await api.get(`/orders/${order.value.id}/messages`)
    if (res.ok) messages.value = await res.json()
  } catch { /* silent */ }
}

async function sendMessage() {
  const text = newMessage.value.trim()
  if (!text || !order.value?.id) return
  sendingMessage.value = true
  try {
    const res = await api.post(`/orders/${order.value.id}/messages`, { content: text })
    if (res.ok) {
      messages.value.push(await res.json())
      newMessage.value = ''
    }
  } catch { /* silent */ } finally {
    sendingMessage.value = false
  }
}

onMounted(() => { loadConfig(); fetchOrder() })
</script>

<template>
  <div class="surface-card my-order-page card-full">
    <div v-if="loading" class="loading-state">
      <i class="pi pi-spin pi-spinner" style="font-size:24px" />
    </div>

    <PMessage v-else-if="error" severity="error" :closable="false">{{ error }}</PMessage>

    <div v-else-if="order" class="my-order-split">
      <div class="my-order-panel">
        <PButton
          type="button"
          text
          severity="secondary"
          icon="pi pi-arrow-left"
          class="detail-back-btn"
          @click="router.push('/my-orders')"
        />

        <div class="detail-summary">
          <span class="detail-ref">{{ order.reference }}</span>
          <PTag :value="t(`orders.status.${order.status}`)" :severity="statusSeverity[order.status]" />
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
                <span class="info-label">{{ t('tracking.company') }}</span>
                <span class="info-value">{{ order.companyName }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">{{ t('tracking.origin') }}</span>
                <span class="info-value">{{ order.originName }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">{{ t('tracking.destination') }}</span>
                <span class="info-value">{{ order.destinationName || order.recipientName || order.recipientEmail || '—' }}</span>
              </div>
            </div>
          </PTabPanel>

          <PTabPanel :value="1">
            <TimelineList :events="order.events ?? []" />
          </PTabPanel>

          <PTabPanel :value="2">
            <div class="chat-panel" data-testid="chat-panel">
              <div class="chat-messages" data-testid="chat-messages">
                <div v-if="messages.length === 0" class="chat-empty">{{ t('orders.chatEmpty') }}</div>
                <div v-for="msg in messages" :key="msg.id" class="chat-message">
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

      <div class="my-order-map-panel">
        <div v-if="hasMap" ref="mapEl" class="my-order-map-el" data-testid="my-order-map" />
        <div v-else class="map-no-coords">
          <i class="pi pi-map-marker" style="font-size:28px;color:#cbd5e1;margin-bottom:8px" />
          <span>{{ t('units.noCoords') }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped src="./MyOrderDetailView.css"></style>
