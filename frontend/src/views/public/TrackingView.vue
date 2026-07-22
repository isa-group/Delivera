<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppConfig } from '@/composables/useAppConfig'
import { useAuthStore } from '@/stores/auth'
import TimelineList from '@/components/TimelineList.vue'
import { startAuthRefresh } from '@/composables/useRefreshToken'
import { getDeviceId } from '../../composables/useRefreshToken'
import { useValidation } from '@/composables/useValidation'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { load: loadConfig, statusSeverity } = useAppConfig()
const dataService = import.meta.env.VITE_DATA_API_URL
const deliveraService = import.meta.env.VITE_API_URL
const {
  validate,
  required,
  email: emailRule,
  minLength,
  maxLength,
  usernameFormat,
  passwordStrength,
  errors,
  invalids
} = useValidation()

const order = ref(null)
const loading = ref(false)
const error = ref('')
const searchRef = ref('')

// Claim registration
const claimFirstName = ref('')
const claimLastName = ref('')
const claimEmail = ref('')
const claimUsername = ref('')
const claimPassword = ref('')
const claimLoading = ref(false)
const claimError = ref('')

async function submitClaim() {
  const valid = validate({
    firstName: [
      required(claimFirstName.value, 'firstName'),
      maxLength(claimFirstName.value, 100, 'firstName'),
    ],
    lastName: [
      maxLength(claimLastName.value, 100, 'lastName'),
    ],
    email: [
      required(claimEmail.value, 'email'),
      emailRule(claimEmail.value),
    ],
    username: [
      required(claimUsername.value, 'username'),
      minLength(claimUsername.value, 3, 'username'),
      maxLength(claimUsername.value, 50, 'username'),
      usernameFormat(claimUsername.value),
    ],
    password: [
      required(claimPassword.value, 'password'),
      minLength(claimPassword.value, 8, 'password'),
      passwordStrength(claimPassword.value),
  ],
  })

  if (!valid) {
    return
  }
  claimError.value = ''
  claimLoading.value = true
  try {
    // useApi uses TOKEN automatically
    const res = await fetch(`${deliveraService}/api/v2/orders/public/track/${route.params.token}/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json','X-Device-Id': getDeviceId() },
      credentials: 'include',
      body: JSON.stringify({
        firstName: claimFirstName.value.trim(),
        lastName: claimLastName.value.trim(),
        email: claimEmail.value.trim(),
        username: claimUsername.value.trim(),
        password: claimPassword.value,
      }),
    })

    const data = await res.json()
    if (res.ok) {
      auth.applyLoginData(data)
      startAuthRefresh()
      router.push('/my-orders')
    } else {
      const code = data?.code
      if (code === 'ORDER_ALREADY_CLAIMED') claimError.value = t('tracking.claim.alreadyClaimed')
      else if (code === 'ORDER_CLAIM_EMAIL_MISMATCH') claimError.value = t('tracking.claim.emailMismatch')
      else if (code === 'EMAIL_ALREADY_EXISTS') claimError.value = t('tracking.claim.emailExists')
      else claimError.value = t('error.connection')
    }
  } catch {
    claimError.value = t('error.connection')
  } finally {
    claimLoading.value = false
  }
}

async function fetchByToken(token) {
  loading.value = true
  error.value = ''
  order.value = null
  try {
    const res = await fetch(`${dataService}/api/v2/orders/public/track/${token}`)
    if (res.ok) order.value = await res.json()
    else error.value = t('tracking.notFound')
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}

async function fetchByReference() {
  if (!searchRef.value.trim()) return
  loading.value = true
  error.value = ''
  order.value = null
  try {
    const res = await fetch(`${dataService}/api/v2/orders/public/search?reference=${encodeURIComponent(searchRef.value.trim())}`)
    if (res.ok) order.value = await res.json()
    else error.value = t('tracking.notFound')
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadConfig()
  if (route.params.token) fetchByToken(route.params.token)
  else if (route.query.q) { searchRef.value = route.query.q; fetchByReference() }
})
</script>

<template>
  <div class="tracking-page">
    <div class="tracking-card">
      <div class="tracking-brand">
        <i class="pi pi-truck" />
        <span>Delivera</span>
      </div>

      <h1>{{ t('tracking.title') }}</h1>

      <template v-if="!route.params.token">
        <p class="tracking-subtitle">{{ t('tracking.searchTitle') }}</p>
        <div class="search-row">
          <PInputText
            v-model="searchRef"
            :placeholder="t('tracking.searchPlaceholder')"
            @keyup.enter="fetchByReference"
            fluid
          />
          <PButton :label="t('tracking.search')" :loading="loading" @click="fetchByReference" />
        </div>
      </template>

      <div v-if="loading" class="tracking-loading">
        <i class="pi pi-spin pi-spinner" style="font-size:24px" />
      </div>

      <PMessage v-else-if="error" severity="error" :closable="false">{{ error }}</PMessage>

      <template v-else-if="order">
        <div class="tracking-summary">
          <div class="tracking-ref">{{ order.reference }}</div>
          <PTag :value="t(`orders.status.${order.status}`)" :severity="statusSeverity[order.status]" />
        </div>

        <div class="tracking-info">
          <div class="info-row">
            <span class="info-label">{{ t('tracking.company') }}</span>
            <span>{{ order.companyName }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">{{ t('tracking.origin') }}</span>
            <span>{{ order.originName }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">{{ t('tracking.destination') }}</span>
            <span>{{ order.destinationName || order.recipientName || '—' }}</span>
          </div>
        </div>

        <div class="tracking-timeline">
          <h3>{{ t('orders.timeline') }}</h3>
          <TimelineList :events="order.events" />
        </div>
        <!-- Pedido reclamado: pide login -->
        <template v-if="!order.claimable && route.params.token">
          <div class="claimed-notice">
            <i class="pi pi-lock" />
            <div>
              <div class="claimed-notice-title">{{ t('tracking.alreadyClaimed') }}</div>
              <RouterLink class="claimed-notice-link" to="/">{{ t('tracking.signInToView') }}</RouterLink>
            </div>
          </div>
        </template>

        <template v-if="order.claimable && route.params.token">
          <div class="claim-panel">
            <div class="claim-header">
              <i class="pi pi-user-plus" />
              <div>
                <div class="claim-title">{{ t('tracking.claim.title') }}</div>
                <div class="claim-desc">{{ t('tracking.claim.description') }}</div>
              </div>
            </div>
            <p v-if="order.recipientEmailHint" class="claim-hint">
              {{ t('tracking.claim.emailHint', { hint: order.recipientEmailHint }) }}
            </p>
            <form class="claim-form" @submit.prevent="submitClaim">
              <div class="claim-row">
                <div class="claim-field">
                  <label for="claim-first-name">{{ t('tracking.claim.firstName') }}</label>
                  <PInputText id="claim-first-name" v-model="claimFirstName" required fluid />
                  <small v-if="errors.firstName" class="field-error">{{ errors.firstName }}</small>
                </div>
                <div class="claim-field">
                  <label for="claim-last-name">{{ t('tracking.claim.lastName') }}</label>
                  <PInputText id="claim-last-name" v-model="claimLastName" required fluid />
                  <small v-if="errors.lastName" class="field-error">{{ errors.lastName }}</small>
                </div>
              </div>
              <div class="claim-field">
                <label for="claim-email">{{ t('tracking.claim.email') }}</label>
                <PInputText id="claim-email" v-model="claimEmail" type="email" required fluid />
                <small v-if="errors.email" class="field-error">{{ errors.email }}</small>
              </div>
              <div class="claim-field">
                <label for="claim-username">{{ t('fields.username') }}</label>
                <PInputText id="claim-username" v-model="claimUsername" required fluid />
                <small v-if="errors.username" class="field-error">{{ errors.username }}</small>
                <small v-else-if="usernameState === 'taken'" class="field-error">{{ t('error.USERNAME_ALREADY_EXISTS') }}</small>
                <small v-else class="field-hint">{{ t('fields.usernameHint') }}</small>
              </div>
              <div class="claim-field">
                <label for="claim-password">{{ t('tracking.claim.password') }}</label>
                <PPassword id="claim-password" v-model="claimPassword" :feedback="false" toggle-mask required fluid />
                <small v-if="errors.password" class="field-error">{{ errors.password }}</small>
              </div>
              <PMessage v-if="claimError" severity="error" :closable="false" class="claim-msg">{{ claimError }}</PMessage>
              <PButton
                type="submit"
                :label="t('tracking.claim.submit')"
                :loading="claimLoading"
                fluid
              />
            </form>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped src="./TrackingView.css"></style>
