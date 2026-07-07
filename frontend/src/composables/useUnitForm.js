import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { useValidation } from '@/composables/useValidation'

function parseCoord(val) {
  const trimmed = String(val).trim()
  if (!trimmed) return null
  const n = Number.parseFloat(trimmed)
  return Number.isFinite(n) ? n : Number.NaN
}

function validateCoords(latVal, lonVal) {
  const lat = parseCoord(latVal)
  const lon = parseCoord(lonVal)
  const hasLat = String(latVal).trim() !== ''
  const hasLon = String(lonVal).trim() !== ''
  if (hasLat !== hasLon) return { lat, lon, err: 'validation.coordinatesIncomplete' }
  if (hasLat && hasLon && (!Number.isFinite(lat) || !Number.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180))
    return { lat, lon, err: 'validation.coordinatesInvalid' }
  return { lat, lon, err: null }
}

export function useUnitForm() {
  const { t } = useI18n()
  const router = useRouter()
  const api = useApi()
  const { validate, required, errors, invalids } = useValidation()

  const name = ref('')
  const unitType = ref(null)
  const address = ref('')
  const latitude = ref('')
  const longitude = ref('')
  const defaultPriority = ref(null)
  const error = ref('')
  const success = ref('')
  const loading = ref(false)

  async function submitUnit({ isEdit, unitId }) {
    if (loading.value) return
    error.value = ''
    success.value = ''

    const fieldValid = validate({
      name: [required(name.value, 'unitName')],
      unitType: [() => unitType.value ? null : { message: '', type: 'required' }],
    })

    const { lat, lon, err: coordErr } = validateCoords(latitude.value, longitude.value)
    if (coordErr) { error.value = t(coordErr); return }
    if (!fieldValid) return

    const hasAddress = address.value?.trim().length > 0
    const hasCoords = Number.isFinite(lat) && Number.isFinite(lon)
    if (!hasAddress && !hasCoords) { error.value = t('validation.unitLocationRequired'); return }

    if (isEdit && !unitId) {
      error.value = t('error.saveFailed')
      return
    }

    loading.value = true
    try {
      const body = {
        name: name.value.trim(),
        type: unitType.value,
        address: address.value?.trim() || null,
        latitude: Number.isFinite(lat) ? lat : null,
        longitude: Number.isFinite(lon) ? lon : null,
        defaultPriority: defaultPriority.value || null,
      }
      const res = isEdit
        ? await api.put(`/units/${unitId}`, body)
        : await api.post('/units', body)

      if (res.ok) {
        if (isEdit) {
          success.value = 'units.updated'
        } else {
          router.push('/units')
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

  // Inicializa los campos del formulario desde un objeto unidad recibido del backend.
  function loadFromUnit(unit) {
    if (!unit) return
    name.value = unit.name ?? ''
    unitType.value = unit.type ?? null
    address.value = unit.address ?? ''
    latitude.value = unit.latitude ?? ''
    longitude.value = unit.longitude ?? ''
    defaultPriority.value = unit.defaultPriority ?? null
  }

  return { name, unitType, address, latitude, longitude, defaultPriority, error, success, loading, errors, invalids, submitUnit, loadFromUnit }
}
