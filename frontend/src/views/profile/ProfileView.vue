<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useApi } from '@/composables/useApi'
import { useValidation } from '@/composables/useValidation'
import { useFormatDate } from '@/composables/useFormatDate'
import { useGeolocation } from '@/composables/useGeolocation'
import { useAppConfig } from '@/composables/useAppConfig'
import { useServices } from '@/composables/useServices'

const { t, locale } = useI18n()
const { formatDate } = useFormatDate()

const languageOptions = [
  { label: 'Español', value: 'es' },
  { label: 'English', value: 'en' },
]
function setLocale(val) {
  locale.value = val
  if (auth.user?.email) {
    localStorage.setItem(`locale_${auth.user.email}`, val)
  }
}

const router = useRouter()
const auth = useAuthStore()
const api = useApi()
const authApi = useServices("auth-service")
const { validate, required, minLength, maxLength, pattern, passwordStrength, usernameFormat, firstError, errors, invalids } = useValidation()

const showAddressFields = computed(() => !auth.isWorker)

const profile = ref(null)
const editing = ref(false)
const error = ref('')
const success = ref('')

const form = ref({ username: '', firstName: '', lastName: '', phone: '', address: '', latitude: null, longitude: null })
const { locating, getPosition } = useGeolocation()
const { fileMaxUploadBytes, load: loadAppConfig } = useAppConfig()
const changingPassword = ref(false)
const passwordForm = ref({ currentPassword: '', newPassword: '' })

// Avatar + crop interactivo
const avatarInput = ref(null)
const avatarSaving = ref(false)
const avatarPreview = ref(null)
const ACCEPTED_AVATAR_TYPES = new Set(['image/jpeg', 'image/png'])
const CANVAS_SIZE = 280

const cropCanvas = ref(null)
const cropImg = ref(null)
const cropScale = ref(1)
const cropOffset = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0, ox: 0, oy: 0 })
let lastTouchDist = 0

function getMinScale() {
  if (!cropImg.value) return 1
  return Math.max(CANVAS_SIZE / cropImg.value.naturalWidth, CANVAS_SIZE / cropImg.value.naturalHeight)
}

function clampOffset(ox, oy) {
  if (!cropImg.value) return { x: 0, y: 0 }
  const imgW = cropImg.value.naturalWidth * cropScale.value
  const imgH = cropImg.value.naturalHeight * cropScale.value
  const halfDiffX = Math.max(0, (imgW - CANVAS_SIZE) / 2)
  const halfDiffY = Math.max(0, (imgH - CANVAS_SIZE) / 2)
  return {
    x: Math.max(-halfDiffX, Math.min(halfDiffX, ox)),
    y: Math.max(-halfDiffY, Math.min(halfDiffY, oy)),
  }
}

function drawCrop() {
  const canvas = cropCanvas.value
  if (!canvas || !cropImg.value) return
  const ctx = canvas.getContext('2d')
  const imgW = cropImg.value.naturalWidth * cropScale.value
  const imgH = cropImg.value.naturalHeight * cropScale.value
  const x = (CANVAS_SIZE - imgW) / 2 + cropOffset.value.x
  const y = (CANVAS_SIZE - imgH) / 2 + cropOffset.value.y
  ctx.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE)
  ctx.drawImage(cropImg.value, x, y, imgW, imgH)
}

function initCrop(dataURL) {
  const img = new Image()
  img.onload = () => {
    cropImg.value = img
    cropScale.value = getMinScale()
    cropOffset.value = { x: 0, y: 0 }
    drawCrop()
  }
  img.src = dataURL
}

watch(avatarPreview, async (val) => {
  if (val) { await nextTick(); initCrop(val) }
  else { cropImg.value = null }
})

function onCropWheel(e) {
  const delta = e.deltaY < 0 ? 0.1 : -0.1
  const newScale = Math.max(getMinScale(), Math.min(4, cropScale.value * (1 + delta)))
  cropScale.value = newScale
  cropOffset.value = clampOffset(cropOffset.value.x, cropOffset.value.y)
  drawCrop()
}

function onCropMouseDown(e) {
  isDragging.value = true
  dragStart.value = { x: e.clientX, y: e.clientY, ox: cropOffset.value.x, oy: cropOffset.value.y }
}

function onCropMouseMove(e) {
  if (!isDragging.value) return
  cropOffset.value = clampOffset(
    dragStart.value.ox + e.clientX - dragStart.value.x,
    dragStart.value.oy + e.clientY - dragStart.value.y,
  )
  drawCrop()
}

function onCropMouseUp() { isDragging.value = false }

function onCropTouchStart(e) {
  if (e.touches.length === 1) {
    isDragging.value = true
    dragStart.value = { x: e.touches[0].clientX, y: e.touches[0].clientY, ox: cropOffset.value.x, oy: cropOffset.value.y }
  } else if (e.touches.length === 2) {
    isDragging.value = false
    lastTouchDist = Math.hypot(e.touches[1].clientX - e.touches[0].clientX, e.touches[1].clientY - e.touches[0].clientY)
  }
}

function onCropTouchMove(e) {
  if (e.touches.length === 1 && isDragging.value) {
    cropOffset.value = clampOffset(
      dragStart.value.ox + e.touches[0].clientX - dragStart.value.x,
      dragStart.value.oy + e.touches[0].clientY - dragStart.value.y,
    )
    drawCrop()
  } else if (e.touches.length === 2) {
    const dist = Math.hypot(e.touches[1].clientX - e.touches[0].clientX, e.touches[1].clientY - e.touches[0].clientY)
    const factor = dist / lastTouchDist
    lastTouchDist = dist
    const newScale = Math.max(getMinScale(), Math.min(4, cropScale.value * factor))
    cropScale.value = newScale
    cropOffset.value = clampOffset(cropOffset.value.x, cropOffset.value.y)
    drawCrop()
  }
}

function onCropTouchEnd() { isDragging.value = false }

const initials = computed(() => {
  if (!profile.value) return '?'
  const f = profile.value.firstName?.[0] || ''
  const l = profile.value.lastName?.[0] || ''
  return (f + l).toUpperCase() || profile.value.email?.[0]?.toUpperCase() || '?'
})

const displayName = computed(() => {
  if (!profile.value) return ''
  const parts = [profile.value.firstName, profile.value.lastName].filter(Boolean)
  return parts.length ? parts.join(' ') : (profile.value.username || profile.value.email)
})

async function fetchProfile() {
  error.value = ''
  try {
    const response = await api.get('/user/profile')
    if (response.ok) {
      profile.value = await response.json()
      auth.setUser(profile.value)
      form.value.username = profile.value.username || ''
      form.value.firstName = profile.value.firstName || ''
      form.value.lastName = profile.value.lastName || ''
      form.value.phone = profile.value.phone || ''
      form.value.address = profile.value.address || ''
      form.value.latitude = profile.value.latitude ?? null
      form.value.longitude = profile.value.longitude ?? null
    }
  } catch {
    error.value = t('error.connection')
  }
}

function startEditing() { editing.value = true; success.value = '' }
function cancelEditing() {
  editing.value = false; error.value = ''
  form.value.username = profile.value.username || ''
  form.value.firstName = profile.value.firstName || ''
  form.value.lastName = profile.value.lastName || ''
  form.value.phone = profile.value.phone || ''
  form.value.address = profile.value.address || ''
  form.value.latitude = profile.value.latitude ?? null
  form.value.longitude = profile.value.longitude ?? null
}

async function reverseGeocode(lat, lon) {
  try {
    const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lon}&accept-language=${locale.value}`
    const res = await fetch(url, { headers: { 'Accept': 'application/json' } })
    if (!res.ok) return null
    const data = await res.json()
    return data.display_name || null
  } catch {
    return null
  }
}

async function captureLocation() {
  error.value = ''
  try {
    const { lat, lon } = await getPosition()
    form.value.latitude = lat
    form.value.longitude = lon
    const display = await reverseGeocode(lat, lon)
    if (display) form.value.address = display
    success.value = 'profile.locationCaptured'
  } catch {
    error.value = t('profile.locationDenied')
  }
}

async function saveProfile() {
  error.value = ''; success.value = ''
  const phoneRegex = /^[+0-9\s()-]+$/
  const valid = validate({
    username: [
      required(form.value.username, 'username'),
      minLength(form.value.username, 3, 'username'),
      maxLength(form.value.username, 50, 'username'),
      usernameFormat(form.value.username),
    ],
    firstName: [
      maxLength(form.value.firstName, 100, 'firstName'),
    ],
    lastName: [maxLength(form.value.lastName, 100, 'lastName')],
    phone: [
      maxLength(form.value.phone, 20, 'phone'),
      pattern(form.value.phone, phoneRegex, 'validation.phoneFormat'),
    ],
    address: [maxLength(form.value.address, 500, 'address')],
  })
  if (!valid) { error.value = firstError(); return }
  try {
    const response = await api.put('/user/profile', form.value)
    if (response.ok) {
      profile.value = await response.json()
      auth.setUser(profile.value)
      editing.value = false
      success.value = 'profile.updated'
    } else {
      const data = await response.json()
      error.value = api.translateError(data, 'error.saveFailed')
    }
  } catch {
    error.value = t('error.connection')
  }
}

function startChangingPassword() { changingPassword.value = true; success.value = ''; error.value = ''; passwordForm.value = { currentPassword: '', newPassword: '' } }
function cancelChangingPassword() { changingPassword.value = false; error.value = '' }

async function savePassword() {
  error.value = ''; success.value = ''
  const valid = validate({
    currentPassword: [required(passwordForm.value.currentPassword, 'currentPassword')],
    newPassword: [
      required(passwordForm.value.newPassword, 'newPassword'),
      minLength(passwordForm.value.newPassword, 8, 'newPassword'),
      passwordStrength(passwordForm.value.newPassword),
    ],
  })
  if (!valid) { error.value = firstError(); return }
  try {
    const response = await authApi.put('/auth/password', { currentPassword: passwordForm.value.currentPassword, newPassword: passwordForm.value.newPassword })
    if (response.ok) { 
      changingPassword.value = false; 
      success.value = 'profile.passwordChanged' 
      const data = await response.json()
      auth.applyLoginData(data)
    }
    else { const data = await response.json(); error.value = api.translateError(data, 'error.passwordChangeFailed') }
  } catch {
    error.value = t('error.connection')
  }
}

function triggerAvatarInput() {
  if (!editing.value) return
  avatarInput.value?.click()
}

function handleAvatarChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (!ACCEPTED_AVATAR_TYPES.includes(file.type)) { error.value = t('profile.avatarInvalidType'); return }
  if (file.size > fileMaxUploadBytes.value) { error.value = t('profile.avatarTooLarge'); return }
  error.value = ''
  const reader = new FileReader()
  reader.onload = (ev) => { avatarPreview.value = ev.target.result }
  reader.readAsDataURL(file)
}

function cancelAvatarPreview() { avatarPreview.value = null; cropImg.value = null }

async function confirmAvatar() {
  if (!cropImg.value) return
  avatarSaving.value = true
  try {
    // Exportar la región visible del crop a 256×256
    const output = document.createElement('canvas')
    output.width = 256
    output.height = 256
    const ctx = output.getContext('2d')
    const ratio = 256 / CANVAS_SIZE
    const imgW = cropImg.value.naturalWidth * cropScale.value * ratio
    const imgH = cropImg.value.naturalHeight * cropScale.value * ratio
    const x = (256 - imgW) / 2 + cropOffset.value.x * ratio
    const y = (256 - imgH) / 2 + cropOffset.value.y * ratio
    ctx.drawImage(cropImg.value, x, y, imgW, imgH)
    const dataURL = output.toDataURL('image/jpeg', 0.9)

    const res = await api.put('/user/avatar', { data: dataURL })
    if (res.ok) {
      profile.value = await res.json()
      auth.setUser(profile.value)
      avatarPreview.value = null
      cropImg.value = null
    } else {
      error.value = t('error.saveFailed')
    }
  } catch {
    error.value = t('error.connection')
  } finally {
    avatarSaving.value = false
  }
}

function handleLogout() {
  locale.value = navigator.language?.startsWith('en') ? 'en' : 'es'
  auth.logout()
  router.push('/')
}

onMounted(() => { fetchProfile(); loadAppConfig() })
</script>

<template>
  <div class="profile-page">
    <!-- Skeleton -->
    <div v-if="!profile" class="surface-card profile-card">
      <PSkeleton width="7rem" height="1.4rem" border-radius="8px" class="mb-4" style="margin-inline:auto" />
      <div class="profile-header">
        <PSkeleton shape="circle" size="3.25rem" />
        <div style="flex:1">
          <PSkeleton width="55%" height="0.9rem" class="mb-2" />
          <PSkeleton width="75%" height="0.75rem" />
        </div>
      </div>
      <div class="fields">
        <div v-for="i in 3" :key="i" class="field">
          <PSkeleton width="4rem" height="0.6rem" class="mb-2" border-radius="4px" />
          <PSkeleton width="65%" height="0.875rem" border-radius="4px" />
        </div>
      </div>
    </div>

    <div v-else class="surface-card profile-card">
      <!-- Cabecera siempre presente -->
      <div class="profile-header">
        <div
          class="avatar-wrap"
          :class="{ 'avatar-editable': editing }"
          @click="triggerAvatarInput"
          :title="editing ? t('profile.uploadAvatar') : ''"
        >
          <img v-if="profile.avatarData" :src="profile.avatarData" class="avatar-img" alt="Avatar" />
          <PAvatar v-else :label="initials" size="large" shape="circle" class="profile-avatar" />
          <div v-if="editing" class="avatar-overlay">
            <i class="pi pi-camera" />
          </div>
          <input ref="avatarInput" type="file" accept="image/jpeg,image/png" style="display:none" @change="handleAvatarChange" />
        </div>
        <div class="profile-header-info">
          <div class="profile-name">{{ displayName }}</div>
          <div class="profile-email">{{ profile.email }}</div>
          <small v-if="editing" class="field-hint">{{ t('profile.avatarHint') }}</small>
          <small v-if="avatarSaving" class="field-hint"><i class="pi pi-spin pi-spinner" /> {{ t('common.loading') }}</small>
        </div>
        <!-- Botón Editar anclado a la cabecera, solo en vista lectura -->
        <PButton
          v-if="!editing && !changingPassword"
          :label="t('profile.edit')"
          icon="pi pi-pencil"
          size="small"
          severity="secondary"
          outlined
          class="btn-header-edit"
          @click="startEditing"
        />
      </div>

      <PMessage v-if="success" severity="success" :closable="false" class="form-message">{{ t(success) }}</PMessage>
      <PMessage v-if="error" severity="error" :closable="false" class="form-message">{{ error }}</PMessage>

      <!-- Modo vista -->
      <template v-if="!editing && !changingPassword">
        <div class="fields fields-grid">
          <div class="field">
            <span class="field-label">{{ t('fields.firstName') }}</span>
            <span class="field-value">{{ profile.firstName || t('fields.empty') }}</span>
          </div>
          <div class="field">
            <span class="field-label">{{ t('fields.lastName') }}</span>
            <span class="field-value">{{ profile.lastName || t('fields.empty') }}</span>
          </div>
          <div class="field">
            <span class="field-label">{{ t('fields.username') }}</span>
            <span class="field-value">{{ profile.username || t('fields.empty') }}</span>
          </div>
          <div class="field">
            <span class="field-label">{{ t('fields.phone') }}</span>
            <span class="field-value">{{ profile.phone || t('fields.empty') }}</span>
          </div>
          <div v-if="showAddressFields" class="field field-wide">
            <span class="field-label">{{ t('fields.address') }}</span>
            <span class="field-value">{{ profile.address || t('fields.empty') }}</span>
          </div>
          <div class="field">
            <span class="field-label">{{ t('profile.memberSince') }}</span>
            <span class="field-value">{{ formatDate(profile.createdAt) ?? t('fields.empty') }}</span>
          </div>
          <div class="field field-language">
            <span class="field-label">{{ t('profile.language') }}</span>
            <SelectButton :model-value="locale" :options="languageOptions" option-label="label" option-value="value" @update:model-value="setLocale" />
          </div>
        </div>
        <div class="profile-actions">
          <PButton :label="t('profile.changePassword')" icon="pi pi-lock" severity="secondary" outlined size="small" @click="startChangingPassword" />
          <PButton :label="t('auth.logout')" icon="pi pi-sign-out" severity="danger" text size="small" class="btn-logout" @click="handleLogout" />
        </div>
      </template>

      <!-- Modo edición -->
      <form v-else-if="editing" @submit.prevent="saveProfile" class="edit-form">
        <div class="fields fields-grid">
          <div class="field">
            <span class="field-label">{{ t('fields.firstName') }}</span>
            <InputText id="profile-first-name" v-model="form.firstName" :placeholder="t('fields.firstName')" maxlength="100" :invalid="!!invalids.firstName" fluid />
            <small v-if="errors.firstName" class="field-error">{{ errors.firstName }}</small>
          </div>
          <div class="field">
            <span class="field-label">{{ t('fields.lastName') }}</span>
            <InputText id="profile-last-name" v-model="form.lastName" :placeholder="t('fields.lastName')" maxlength="100" :invalid="!!invalids.lastName" fluid />
            <small v-if="errors.lastName" class="field-error">{{ errors.lastName }}</small>
          </div>
          <div class="field">
            <span class="field-label">{{ t('fields.username') }}</span>
            <InputText id="profile-username" v-model="form.username" type="text" autocomplete="username" :placeholder="t('fields.usernamePlaceholder')" maxlength="50" :invalid="!!invalids.username" fluid />
            <small v-if="errors.username" class="field-error">{{ errors.username }}</small>
            <small v-else class="field-hint">{{ t('fields.usernameHint') }}</small>
          </div>
          <div class="field">
            <span class="field-label">{{ t('fields.phone') }}</span>
            <InputText id="profile-phone" v-model="form.phone" type="tel" :placeholder="t('fields.phonePlaceholder')" maxlength="20" :invalid="!!invalids.phone" fluid />
            <small v-if="errors.phone" class="field-error">{{ errors.phone }}</small>
          </div>
          <div v-if="showAddressFields" class="field field-wide">
            <span class="field-label">{{ t('fields.address') }}</span>
            <InputText id="profile-address" v-model="form.address" :placeholder="t('fields.addressPlaceholder')" maxlength="500" :invalid="!!invalids.address" fluid />
            <small v-if="errors.address" class="field-error">{{ errors.address }}</small>
            <small v-else class="field-hint">{{ t('profile.addressHint') }}</small>
            <PButton type="button" :label="t('profile.useCurrentLocation')" icon="pi pi-map-marker" severity="secondary" outlined size="small" :loading="locating" @click="captureLocation" style="margin-top:8px" />
            <small v-if="form.latitude" class="field-hint">{{ form.latitude }}, {{ form.longitude }}</small>
          </div>
        </div>
        <div class="profile-actions profile-actions--form">
          <PButton type="button" :label="t('profile.cancel')" icon="pi pi-times" severity="secondary" outlined size="small" @click="cancelEditing" />
          <PButton type="submit" :label="t('profile.save')" icon="pi pi-check" size="small" />
        </div>
      </form>

      <!-- Cambiar contraseña -->
      <div v-else class="edit-form">
        <!-- Input trampa: evita que el navegador autocomplete los campos reales -->
        <input type="password" style="display:none" tabindex="-1" autocomplete="new-password" aria-hidden="true" />
        <div class="fields">
          <div class="field">
            <span class="field-label">{{ t('fields.currentPassword') }}</span>
            <PPassword
              id="current-password"
              v-model="passwordForm.currentPassword"
              :feedback="false"
              toggle-mask
              :placeholder="t('fields.currentPassword')"
              :pt="{ pcInputText: { autocomplete: 'new-password' } }"
              fluid
            />
          </div>
          <div class="field">
            <span class="field-label">{{ t('fields.newPassword') }}</span>
            <PPassword
              id="new-password"
              v-model="passwordForm.newPassword"
              :feedback="false"
              toggle-mask
              :placeholder="t('fields.passwordHint')"
              :pt="{ pcInputText: { autocomplete: 'new-password' } }"
              fluid
            />
            <small class="field-hint">{{ t('fields.passwordHint') }}</small>
          </div>
        </div>
        <div class="profile-actions profile-actions--form">
          <PButton type="button" :label="t('profile.cancel')" icon="pi pi-times" severity="secondary" outlined size="small" @click="cancelChangingPassword" />
          <PButton type="button" :label="t('profile.save')" icon="pi pi-check" size="small" @click="savePassword" />
        </div>
      </div>
    </div>

    <!-- Modal de crop del avatar -->
    <div v-if="avatarPreview" class="avatar-modal-backdrop" @click.self="cancelAvatarPreview">
      <div class="avatar-modal">
        <h3 class="avatar-modal-title">{{ t('profile.avatarPreviewTitle') }}</h3>
        <p class="avatar-modal-hint">{{ t('profile.avatarPreviewHint') }}</p>
        <div class="crop-stage">
          <canvas
            ref="cropCanvas"
            :width="CANVAS_SIZE"
            :height="CANVAS_SIZE"
            class="crop-canvas"
            :class="{ dragging: isDragging }"
            @wheel.prevent="onCropWheel"
            @mousedown="onCropMouseDown"
            @mousemove="onCropMouseMove"
            @mouseup="onCropMouseUp"
            @mouseleave="onCropMouseUp"
            @touchstart.prevent="onCropTouchStart"
            @touchmove.prevent="onCropTouchMove"
            @touchend="onCropTouchEnd"
          />
          <div class="crop-overlay" />
        </div>
        <p class="crop-hint">{{ t('profile.avatarCropHint') }}</p>
        <div class="avatar-modal-actions">
          <PButton :label="t('profile.avatarCancel')" severity="secondary" outlined size="small" @click="cancelAvatarPreview" :disabled="avatarSaving" />
          <PButton :label="t('profile.avatarConfirm')" icon="pi pi-check" size="small" :loading="avatarSaving" @click="confirmAvatar" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped src="./ProfileView.css"></style>
