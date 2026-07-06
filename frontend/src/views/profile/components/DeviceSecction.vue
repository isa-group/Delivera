<script setup>
import { useServices } from '@/composables/useServices'
import { useValidation } from '@/composables/useValidation'
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const devices = ref([])
const api = useServices("auth-service")
const error = ref('')
const loading = ref(false)
const password = ref('')
const { validate, required, errors, invalids } = useValidation()

async function getDevices() {
  error.value = ''
  loading.value = true
  try {
    const res = await api.get('/auth/device')
    if (res.ok) {
       devices.value = await res.json()
    } else {
      const data = await res.json()
      error.value = api.translateError(data, 'error.FORBIDDEN')
    }
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}

async function handleDelete() {
  error.value = ''
  if (!validate({
    password: [required(password.value, 'password')],
  })) return


  loading.value = true
  try {
    const res = await api.del('/auth/device/others', { password: password.value })
    if (res.status === 204) {
      password.value = ''
      await getDevices()
    } else {
      const data = await res.json()
      error.value = api.translateError(data, 'error.invalidCredentials')
    }
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}

async function handleRevoke() {
  error.value = ''
  if (!validate({
    password: [required(password.value, 'password')],
  })) return


  loading.value = true
  try {
    const res = await api.put('/auth/device/others/revoke', { password: password.value })
    if (res.status === 204) {
      password.value = ''
      await getDevices()
    } else {
      const data = await res.json()
      error.value = api.translateError(data, 'error.invalidCredentials')
    }
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}



onMounted(getDevices)
</script>




<template>
    <div class="devices-section">
        <h3 class="section-title">{{t('settings.devices.name')}}</h3>
        <p class="section-subtitle">
        {{t('settings.devices.message')}}
        </p>
        
        <div class="form-field">
            <label for="login-password">{{t('settings.devices.passwordRequired')}}</label>
            <PPassword
            id="login-password"
            v-model="password"
            :feedback="false"
            toggle-mask
            :placeholder="t('fields.password')"
            :invalid="!!invalids.password"
            :pt="{ pcinput: { root: { autocomplete: 'current-password' } } }"
            fluid
            />
            <small v-if="errors.password" class="field-error">{{ errors.password }}</small>
        </div>
      
        <div class="devices-actions">
            <PButton
                severity="warn"
                icon="pi pi-lock"
                :label="t('settings.devices.revokeOthers')"
                @click="handleRevoke"
            />

            <PButton
                severity="danger"
                icon="pi pi-shield"
                :label="t('settings.devices.logoutOthers')"
                @click="handleDelete"
            />
        </div>

      
        <div v-if="loading">
            {{t('common.loading')}}
        </div>

        
        <PMessage
            v-if="error"
            severity="error"
            :closable="false"
        >
            {{ error }}
        </PMessage>
        
        <div
            v-for="device in devices"
            :key="device.ip + device.createdAt"
            class="device-card"
        >
            <div class="device-header">
                <strong>{{ device.userAgent }}</strong>

                <span
                v-if="device.currentSession"
                class="current-session"
                >
                {{ t('settings.devices.currentSession') }}
                </span>
            </div>

            <div class="device-info">
                <div>IP: {{ device.ip }}</div>

                <div>
                {{ t('settings.devices.lastUsed') }}:
                {{ new Date(device.lastUsed).toLocaleString() }}
                </div>

                <div
                v-if="device.suspicious"
                class="suspicious"
                >
                ⚠ {{ t('settings.devices.suspicious') }}
                </div>
                
                <div
                v-if="device.revoked"
                class="revoked"
                >
                🚫 {{ t('settings.devices.revoked') }}
                </div>

            </div>
        </div>
        <div v-if="devices.length === 0" class="empty-state">
            {{t('settings.devices.empty')}}
        </div>
    </div>
</template>
  


<style scoped>
    .devices-actions {
        display: flex;
        justify-content: space-evenly;
        margin: 5px;
    }

    .devices-section {
        padding: 1rem;
    }

    .section-title {
        margin-bottom: 0.25rem;
    }

    .section-subtitle {
        color: #64748b;
        margin-bottom: 1.5rem;
    }

    .empty-state {
        padding: 2rem;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        text-align: center;
    }
    .device-card {
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        padding: 1rem;
        margin-bottom: 1rem;
    }

    .device-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    .device-info {
        margin-top: 0.75rem;
        display: flex;
        flex-direction: column;
        gap: 0.4rem;
    }

    
    .revoked {
        color: #dc2626;
        font-weight: 600;
    }


    .current-session {
        background: #dcfce7;
        color: #166534;
        padding: 0.25rem 0.5rem;
        border-radius: 999px;
        font-size: 0.85rem;
    }

    .suspicious {
        color: #b45309;
        font-weight: 600;
    }
  </style>
  
