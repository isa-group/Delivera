<script setup>
import { useServices } from '@/composables/useServices'
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const devices = ref([])
const api = useServices("auth-service")
const error = ref('')
const loading = ref(false)

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

onMounted(getDevices)
</script>


<template>
    <div class="devices-section">
      <h3 class="section-title">{{t('settings.devices.name')}}</h3>
      <p class="section-subtitle">
        {{t('settings.devices.message')}}
      </p>
      
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
            </div>
        </div>
        <div v-if="devices.length === 0" class="empty-state">
            {{t('settings.devices.empty')}}
        </div>
    </div>
</template>
  


<style scoped>
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
  
