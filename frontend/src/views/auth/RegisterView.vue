<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useApi } from '@/composables/useApi'
import { useValidation } from '@/composables/useValidation'
import { useAvailabilityCheck } from '@/composables/useAvailabilityCheck'
import { useGeolocation } from '@/composables/useGeolocation'
import BaseLayout from '@/components/BaseLayout.vue'
import AvailabilityBadge from '@/components/AvailabilityBadge.vue'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const api = useApi()
const { validate, required, email: emailRule, minLength, maxLength, pattern, passwordStrength, usernameFormat, errors, invalids } = useValidation()

const mode = ref(null)
const hovered = ref(null)
const selectedTheme = ref(null)
const activeTheme = computed(() => hovered.value ?? selectedTheme.value)
const email = ref('')
const username = ref('')
const firstName = ref('')
const lastName = ref('')
const phone = ref('')
const address = ref('')
const latitude = ref(null)
const longitude = ref(null)
const locationCaptured = ref(false)
const password = ref('')
const error = ref('')
const { locating, getPosition } = useGeolocation()

async function captureLocation() {
  try {
    const { lat, lon } = await getPosition()
    latitude.value = lat
    longitude.value = lon
    locationCaptured.value = true
  } catch {
    error.value = t('profile.locationDenied')
  }
}

function isUsernameFormat(val) {
  return /^[a-z0-9_-]{3,50}$/.test(val)
}

const { checking: usernameChecking, available: usernameAvailable } =
  useAvailabilityCheck(username, {
    endpoint: '/auth/check-username',
    paramName: 'username',
    validate: isUsernameFormat
  })

const usernameState = computed(() => {
  if (!username.value || !isUsernameFormat(username.value)) return 'invalid'
  if (usernameChecking.value) return 'checking'
  if (usernameAvailable.value === true) return 'available'
  if (usernameAvailable.value === false) return 'taken'
  return 'idle'
})

function handleSignIn() {
  selectedTheme.value = null
  hovered.value = null
  setTimeout(() => router.push('/'), 300)
}

async function handleRegister() {
  error.value = ''
  const phoneRegex = /^[+0-9\s()-]+$/
  const valid = validate({
    email: [required(email.value, 'email'), emailRule(email.value)],
    username: [
      required(username.value, 'username'),
      minLength(username.value, 3, 'username'),
      maxLength(username.value, 50, 'username'),
      usernameFormat(username.value),
    ],
    firstName: [
      required(firstName.value, 'firstName'),
      maxLength(firstName.value, 100, 'firstName'),
    ],
    lastName: [maxLength(lastName.value, 100, 'lastName')],
    phone: [
      maxLength(phone.value, 20, 'phone'),
      pattern(phone.value, phoneRegex, 'validation.phoneFormat'),
    ],
    password: [required(password.value, 'password'), minLength(password.value, 8, 'password'), passwordStrength(password.value)],
  })
  if (!valid) return

  const hasAddress = address.value.trim().length > 0
  const hasCoords = latitude.value != null && longitude.value != null
  if (!hasAddress && !hasCoords) {
    error.value = t('error.MISSING_USER_LOCATION')
    return
  }

  try {
    const response = await api.post('/auth/register', {
      email: email.value,
      username: username.value,
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim() || null,
      phone: phone.value || null,
      password: password.value,
      address: address.value.trim() || null,
      latitude: latitude.value,
      longitude: longitude.value,
    })
    const data = await response.json()
    if (response.ok) {
      auth.setToken(data.token)
      auth.setRole(data.role ?? null)
      router.push(data.role === 'LOYAL_USER' ? '/my-orders' : '/profile')
    } else {
      error.value = api.translateError(data, 'error.registerFailed')
    }
  } catch {
    error.value = t('error.connection')
  }
}
</script>

<template>
  <div class="bg-overlay bg-celeste" :class="{ active: activeTheme === 'personal' }"></div>
  <div class="bg-overlay bg-purple" :class="{ active: activeTheme === 'company' }"></div>
  <BaseLayout>
    <!-- Selector tipo de cuenta -->
    <div v-if="mode === null" class="card card-wide">
      <h1>{{ t('app.name') }}</h1>
      <p class="subtitle">{{ t('auth.chooseAccountType') }}</p>

      <div class="register-type-grid">
        <button class="register-type-option" type="button" :style="hovered === 'personal' ? { borderColor: '#0ea5e9', boxShadow: '0 0 0 3px rgba(14, 165, 233, 0.1)' } : {}" @click="mode = 'individual'; selectedTheme = 'personal'" @mouseenter="hovered = 'personal'" @mouseleave="hovered = null">
          <svg class="option-icon" :style="{ color: activeTheme === 'personal' ? '#0ea5e9' : undefined }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="8" r="4" />
            <path stroke-linecap="round" d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
          </svg>
          <div class="option-title">{{ t('auth.individualTitle') }}</div>
          <div class="option-desc">{{ t('auth.individualDesc') }}</div>
        </button>

        <router-link to="/register/company" class="register-type-option" :style="hovered === 'company' ? { borderColor: '#8b5cf6', boxShadow: '0 0 0 3px rgba(139, 92, 246, 0.1)' } : {}" @click="selectedTheme = 'company'" @mouseenter="hovered = 'company'" @mouseleave="hovered = null">
          <svg class="option-icon" :style="{ color: activeTheme === 'company' ? '#8b5cf6' : undefined }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="3" y="9" width="18" height="13" rx="1" />
            <path stroke-linecap="round" d="M8 22V9M16 22V9M3 13h18M7 9V6a5 5 0 0110 0v3" />
          </svg>
          <div class="option-title">{{ t('auth.companyTitle') }}</div>
          <div class="option-desc">{{ t('auth.companyDesc') }}</div>
        </router-link>
      </div>

      <p class="form-link">{{ t('auth.hasAccount') }} <a href="/" :style="{ color: activeTheme === 'personal' ? '#0ea5e9' : activeTheme === 'company' ? '#8b5cf6' : undefined }" @click.prevent="handleSignIn">{{ t('auth.signIn') }}</a></p>
    </div>

    <!-- Formulario individual -->
    <form v-else class="card theme-personal" @submit.prevent="handleRegister">
      <PButton
        type="button"
        text
        severity="secondary"
        icon="pi pi-arrow-left"
        class="back-btn"
        rounded
        @click="mode = null; selectedTheme = null"
      />
      <h1>{{ t('app.name') }}</h1>
      <p class="subtitle">{{ t('auth.createAccount') }}</p>

      <div class="form-field">
        <label for="register-email">{{ t('fields.email') }}</label>
        <InputText id="register-email" v-model="email" type="email" :placeholder="t('fields.emailPersonalPlaceholder')" maxlength="255" :invalid="!!invalids.email" fluid />
        <small v-if="errors.email" class="field-error">{{ errors.email }}</small>
      </div>
      <div class="form-field">
        <label for="register-username">{{ t('fields.username') }}</label>
        <div class="check-field">
          <InputText id="register-username" v-model="username" type="text" :placeholder="t('fields.usernamePlaceholder')" maxlength="50" :invalid="!!invalids.username || usernameState === 'taken'" autocomplete="username" fluid />
          <AvailabilityBadge :state="usernameState" />
        </div>
        <small v-if="errors.username" class="field-error">{{ errors.username }}</small>
        <small v-else-if="usernameState === 'taken'" class="field-error">{{ t('error.USERNAME_ALREADY_EXISTS') }}</small>
        <small v-else class="field-hint">{{ t('fields.usernameHint') }}</small>
      </div>
      <div class="form-row">
        <div class="form-field">
          <label for="register-first-name">{{ t('fields.firstName') }}</label>
          <InputText id="register-first-name" v-model="firstName" type="text" :placeholder="t('fields.firstName')" maxlength="100" :invalid="!!invalids.firstName" fluid />
          <small v-if="errors.firstName" class="field-error">{{ errors.firstName }}</small>
        </div>
        <div class="form-field">
          <label for="register-last-name">{{ t('fields.lastName') }}</label>
          <InputText id="register-last-name" v-model="lastName" type="text" :placeholder="t('fields.lastName')" maxlength="100" :invalid="!!invalids.lastName" fluid />
          <small v-if="errors.lastName" class="field-error">{{ errors.lastName }}</small>
        </div>
      </div>
      <div class="form-field">
        <label for="register-phone">{{ t('fields.phone') }}</label>
        <InputText id="register-phone" v-model="phone" type="tel" :placeholder="t('fields.phonePlaceholder')" maxlength="20" :invalid="!!invalids.phone" fluid />
        <small v-if="errors.phone" class="field-error">{{ errors.phone }}</small>
      </div>
      <div class="form-field">
        <label for="register-address">{{ t('fields.address') }} <span style="color:var(--p-red-500)">*</span></label>
        <InputText id="register-address" v-model="address" :placeholder="t('fields.addressPlaceholder')" maxlength="500" fluid />
        <div style="display:flex;justify-content:center;margin-top:6px">
          <PButton type="button" :label="locationCaptured ? t('profile.locationCaptured') : t('profile.useCurrentLocation')" :icon="locationCaptured ? 'pi pi-check' : 'pi pi-map-marker'" :severity="locationCaptured ? 'success' : 'secondary'" outlined size="small" :loading="locating" @click="captureLocation" />
        </div>
      </div>
      <div class="form-field">
        <label for="register-password">{{ t('fields.password') }}</label>
        <PPassword id="register-password" v-model="password" :feedback="false" toggle-mask :placeholder="t('fields.password')" :invalid="!!invalids.password" fluid />
        <small v-if="errors.password" class="field-error">{{ errors.password }}</small>
      </div>

      <PMessage v-if="error" severity="error" :closable="false" class="form-message">{{ error }}</PMessage>

      <PButton type="submit" :label="t('auth.register')" fluid class="submit-btn" />
      <p class="form-link">{{ t('auth.hasAccount') }} <a href="/" @click.prevent="handleSignIn">{{ t('auth.signIn') }}</a></p>
    </form>
  </BaseLayout>
</template>

