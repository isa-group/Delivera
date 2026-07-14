<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useVehicleForm } from '@/composables/useVehicleForm'
import { useServices } from '@/composables/useServices'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const api = useServices("data-service")

const vehicleId = computed(() => route.params.id || null)
const isEdit = computed(() => !!vehicleId.value)

const { plate, capacity, depotId, error, success, loading, errors, invalids, submitVehicle, loadFromVehicle } = useVehicleForm()

const depots = ref([])
const depotsLoading = ref(false)
const loadError = ref('')

async function loadDepots() {
  depotsLoading.value = true
  try {
    const res = await api.get('/units')
    if (res.ok) depots.value = await res.json()
  } catch { /* silent */ } finally {
    depotsLoading.value = false
  }
}

async function loadVehicle(id) {
  if (!id) return
  loadError.value = ''
  try {
    const res = await api.get(`/vehicles/${id}`)
    if (res.ok) {
      const vehicle = await res.json()
      loadFromVehicle(vehicle)
    } else {
      loadError.value = t('vehicles.notFound')
    }
  } catch {
    loadError.value = t('error.connection')
  }
}

onMounted(async () => {
  await loadDepots()
  if (isEdit.value) await loadVehicle(vehicleId.value)
})

function handleSubmit() {
  submitVehicle({ isEdit: isEdit.value, vehicleId: vehicleId.value })
}
</script>

<template>
  <main class="surface-card vehicle-form-page card-full" :aria-labelledby="isEdit ? 'vehicle-edit-title' : 'vehicle-new-title'">
    <form class="vehicle-form-panel" @submit.prevent="handleSubmit" novalidate>
      <PButton
        type="button"
        text
        severity="secondary"
        icon="pi pi-arrow-left"
        class="back-btn-inline"
        :aria-label="t('common.back')"
        @click="router.push('/vehicles')"
      />

      <div class="form-header">
        <div class="form-icon" aria-hidden="true">
          <i class="pi pi-truck" aria-hidden="true" />
        </div>
        <div>
          <h1 :id="isEdit ? 'vehicle-edit-title' : 'vehicle-new-title'">{{ t(isEdit ? 'vehicles.edit' : 'vehicles.new') }}</h1>
          <p class="form-subtitle">{{ isEdit ? t('vehicles.edit') : t('vehicles.new') }}</p>
        </div>
      </div>

      <div v-if="loadError" role="alert">
        <PMessage severity="error" :closable="false" class="form-message">{{ loadError }}</PMessage>
      </div>

      <div class="form-field">
        <label for="vehicle-plate">{{ t('vehicles.plate') }}</label>
        <InputText
          id="vehicle-plate"
          v-model="plate"
          :placeholder="t('vehicles.platePlaceholder')"
          maxlength="20"
          :invalid="!!invalids.plate"
          :aria-invalid="!!invalids.plate"
          :aria-describedby="errors.plate ? 'vehicle-plate-error' : undefined"
          class="plate-input"
          fluid
        />
        <small v-if="errors.plate" id="vehicle-plate-error" class="field-error" role="alert">{{ errors.plate }}</small>
      </div>

      <div class="form-field">
        <label for="vehicle-capacity">{{ t('vehicles.capacity') }}</label>
        <InputText
          id="vehicle-capacity"
          v-model="capacity"
          type="number"
          min="1"
          placeholder="1"
          :invalid="!!invalids.capacity"
          :aria-invalid="!!invalids.capacity"
          :aria-describedby="errors.capacity ? 'vehicle-capacity-error' : undefined"
          fluid
        />
        <small v-if="errors.capacity" id="vehicle-capacity-error" class="field-error" role="alert">{{ errors.capacity }}</small>
      </div>

      <div class="form-field">
        <label for="vehicle-depot">{{ t('vehicles.depot') }}</label>
        <PSelect
          id="vehicle-depot"
          v-model="depotId"
          :options="depots"
          option-label="name"
          option-value="id"
          :placeholder="t('vehicles.depotPlaceholder')"
          :loading="depotsLoading"
          :aria-label="t('vehicles.depotPlaceholder')"
          fluid
        />
      </div>

      <div role="alert" aria-live="polite">
        <PMessage v-if="error" severity="error" :closable="false" class="form-message">{{ error }}</PMessage>
        <PMessage v-if="success" severity="success" :closable="false" class="form-message">{{ t(success) }}</PMessage>
      </div>

      <PButton
        type="submit"
        :label="loading ? t('common.loading') : t('common.save')"
        :loading="loading"
        fluid
        class="submit-btn"
      />
    </form>

    <aside class="vehicle-form-visual" aria-label="vehicle-preview">
      <div class="visual-card">
        <div class="truck-icon-wrap" aria-hidden="true">
          <i class="pi pi-truck truck-icon" aria-hidden="true" />
        </div>
        <div v-if="plate" class="visual-plate" aria-live="polite">{{ plate.toUpperCase() }}</div>
        <div v-else class="visual-plate visual-plate--muted" aria-live="polite">—</div>
        <div class="visual-capacity" aria-live="polite">{{ t('vehicles.capacity') }}: {{ capacity || '—' }}</div>
      </div>
    </aside>
  </main>
</template>

<style scoped src="./VehicleFormView.css"></style>
