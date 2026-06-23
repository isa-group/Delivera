import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { useValidation } from '@/composables/useValidation'

export function useVehicleForm() {
  const { t } = useI18n()
  const router = useRouter()
  const api = useApi()
  const { validate, required, errors, invalids } = useValidation()

  const plate = ref('')
  const capacity = ref(null)
  const depotId = ref(null)
  const error = ref('')
  const success = ref('')
  const loading = ref(false)

  async function submitVehicle({ isEdit, vehicleId }) {
    if (loading.value) return
    error.value = ''
    success.value = ''

    const fieldValid = validate({
      plate: [required(plate.value, 'plate')],
      capacity: [required(capacity.value, 'capacity')],
    })

    if (!depotId.value) {
      error.value = t('validation.required', { field: t('fields.depot') })
      return
    }

    if (!fieldValid) return

    if (isEdit && !vehicleId) {
      error.value = t('error.saveFailed')
      return
    }

    loading.value = true
    try {
      const body = {
        plate: plate.value.trim(),
        capacity: Number(capacity.value),
        depotId: depotId.value,
      }
      const res = isEdit
        ? await api.put(`/vehicles/${vehicleId}`, body)
        : await api.post('/vehicles', body)

      if (res.ok) {
        if (isEdit) {
          success.value = 'vehicles.updated'
        } else {
          router.push('/vehicles')
        }
      } else {
        const data = await res.json()
        error.value = api.translateError(data, 'error.saveFailed')
      }
    } catch {
      error.value = t('error.connection')
    } finally {
      loading.value = false
    }
  }

  function loadFromVehicle(vehicle) {
    if (!vehicle) return
    plate.value = vehicle.plate ?? ''
    capacity.value = vehicle.capacity ?? null
    depotId.value = vehicle.depotId ?? null
  }

  return { plate, capacity, depotId, error, success, loading, errors, invalids, submitVehicle, loadFromVehicle }
}
