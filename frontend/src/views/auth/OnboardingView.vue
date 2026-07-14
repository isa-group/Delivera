<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { WORKER_ROLES } from '@/constants/roles'

const { t } = useI18n()
const router = useRouter()
const api = useApi()

const step = ref(1) // 1 = crear unidad, 2 = invitar trabajador

// Step 1: unidad
const unitName = ref('')
const unitType = ref('WAREHOUSE')
const unitAddress = ref('')
const unitSaving = ref(false)
const unitError = ref('')

const UNIT_TYPES = ['WAREHOUSE', 'STORE', 'FACTORY', 'LOGISTICS_CENTER']
const unitTypeOptions = computed(() => UNIT_TYPES.map(type => ({ label: t(`units.${type}`), value: type })))

// Step 2: trabajador
const workerEmail = ref('')
const workerRole = ref('OPERATOR')
const workerSaving = ref(false)
const workerError = ref('')

const roleOptions = WORKER_ROLES.map(r => ({ label: t('workers.roles.' + r), value: r }))

async function createUnit() {
  if (!unitName.value.trim()) { unitError.value = t('validation.required', { field: t('fields.unitName') }); return }
  if (!unitAddress.value.trim()) { unitError.value = t('validation.required', { field: t('fields.address') }); return }
  unitSaving.value = true
  unitError.value = ''
  try {
    const res = await api.post('/units', { name: unitName.value.trim(), type: unitType.value, address: unitAddress.value.trim() })
    if (res.ok) step.value = 2
    else { const d = await res.json(); unitError.value = api.translateError(d, 'error.saveFailed') }
  } catch { unitError.value = t('error.connection') }
  finally { unitSaving.value = false }
}

async function inviteWorker() {
  if (!workerEmail.value.trim()) { workerError.value = t('validation.required', { field: t('workers.email') }); return }
  workerSaving.value = true
  workerError.value = ''
  try {
    const res = await api.post('/workers/invite', { email: workerEmail.value.trim(), role: workerRole.value })
    if (res.ok) router.push('/home')
    else { const d = await res.json(); workerError.value = api.translateError(d, 'error.saveFailed') }
  } catch { workerError.value = t('error.connection') }
  finally { workerSaving.value = false }
}
</script>

<template>
  <div class="onboarding-wrapper">
    <div class="onboarding-card">
      <div class="onboarding-logo">
        <span class="logo-text">Delivera</span>
      </div>

      <div class="step-indicator">
        <span :class="['step-dot', { active: step >= 1, done: step > 1 }]">1</span>
        <span class="step-line" />
        <span :class="['step-dot', { active: step >= 2 }]">2</span>
      </div>

      <!-- Step 1: Crear unidad -->
      <template v-if="step === 1">
        <h2>{{ t('onboarding.createUnit') }}</h2>
        <p class="step-hint">{{ t('onboarding.createUnitHint') }}</p>

        <div class="form-field">
          <label for="ob-unit-name">{{ t('fields.unitName') }}</label>
          <InputText id="ob-unit-name" v-model="unitName" :placeholder="t('fields.unitNamePlaceholder')" fluid />
        </div>
        <div class="form-field">
          <label for="ob-unit-type">{{ t('fields.type') }}</label>
          <PSelect input-id="ob-unit-type" v-model="unitType" :options="unitTypeOptions" option-label="label" option-value="value" fluid />
        </div>
        <div class="form-field">
          <label for="ob-unit-address">{{ t('fields.address') }}</label>
          <InputText id="ob-unit-address" v-model="unitAddress" :placeholder="t('fields.addressPlaceholder')" fluid />
        </div>

        <PMessage v-if="unitError" severity="error" :closable="false" class="form-message">{{ unitError }}</PMessage>

        <div class="form-actions">
          <PButton :label="t('common.next')" :loading="unitSaving" icon-pos="right" icon="pi pi-arrow-right" @click="createUnit" />
          <PButton :label="t('onboarding.skip')" severity="secondary" text icon="pi pi-forward" @click="step = 2" />
        </div>
      </template>

      <!-- Step 2: Invitar trabajador -->
      <template v-else>
        <h2>{{ t('onboarding.inviteWorker') }}</h2>
        <p class="step-hint">{{ t('onboarding.inviteWorkerHint') }}</p>

        <div class="form-field">
          <label for="ob-worker-email">{{ t('workers.email') }}</label>
          <InputText id="ob-worker-email" v-model="workerEmail" type="email" :placeholder="t('fields.emailPlaceholder')" fluid />
        </div>
        <div class="form-field">
          <label for="ob-worker-role">{{ t('workers.role') }}</label>
          <PSelect input-id="ob-worker-role" v-model="workerRole" :options="roleOptions" option-label="label" option-value="value" fluid />
        </div>

        <PMessage v-if="workerError" severity="error" :closable="false" class="form-message">{{ workerError }}</PMessage>

        <div class="form-actions">
          <PButton :label="t('onboarding.finish')" icon="pi pi-check" :loading="workerSaving" @click="inviteWorker" />
          <PButton :label="t('onboarding.skip')" severity="secondary" text icon="pi pi-forward" @click="router.push('/home')" />
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped src="./OnboardingView.css"></style>
