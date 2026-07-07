import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { useValidation } from '@/composables/useValidation'
import { useGeolocation } from '@/composables/useGeolocation'

export function useOrderForm() {
  const { t } = useI18n()
  const router = useRouter()
  const api = useApi()
  const { validate, required, email: emailRule, errors, invalids } = useValidation()

  const units = ref([])
  const externalUnits = ref([])
  const loyalUsers = ref([])
  const loadError = ref('')
  const orderType = ref('INTERNAL') // 'INTERNAL' | 'B2C' | 'B2B'
  const originId = ref('')
  const destinationId = ref('')
  const b2bOrgId = ref('')
  const b2bDestinationId = ref('')
  const recipientEmail = ref('')
  const recipientName = ref('')
  const recipientAddress = ref('')
  const recipientLatitude = ref(null)
  const recipientLongitude = ref(null)
  const { locating, getPosition } = useGeolocation()
  const addressPrefilled = ref(false)
  const priority = ref('NORMAL')
  const notes = ref('')
  const loading = ref(false)
  const error = ref('')

  const destinationOptions = computed(() =>
    units.value.filter(u => u.id !== originId.value)
  )

  const b2bOrganizations = computed(() => {
    const seen = new Set()
    return externalUnits.value
      .filter(u => !seen.has(u.orgId) && seen.add(u.orgId))
      .map(u => ({ id: u.orgId, name: u.orgName }))
  })

  const b2bUnitOptions = computed(() =>
    externalUnits.value
      .filter(u => u.orgId === b2bOrgId.value)
      .map(u => ({ ...u, displayName: `${u.companyName} · ${u.name}` }))
  )

  const loyalUserMatch = computed(() => {
    if (orderType.value !== 'B2C' || !recipientEmail.value) return null
    return loyalUsers.value.find(lu => lu.email.toLowerCase() === recipientEmail.value.toLowerCase().trim()) || null
  })

  watch(b2bOrgId, () => { b2bDestinationId.value = '' })

  watch(recipientEmail, () => {
    if (orderType.value !== 'B2C') return
    if (!recipientEmail.value) { recipientName.value = ''; return }
    const match = loyalUserMatch.value
    if (match?.address && match?.latitude && match?.longitude) {
      if (!recipientAddress.value || addressPrefilled.value) {
        recipientAddress.value = match.address
        recipientLatitude.value = match.latitude
        recipientLongitude.value = match.longitude
        addressPrefilled.value = true
      }
    }
  })

  async function captureLocation() {
    try {
      const { lat, lon } = await getPosition()
      recipientLatitude.value = lat
      recipientLongitude.value = lon
    } catch { /* permiso denegado o no disponible */ }
  }

  onMounted(async () => {
    try {
      const [unitsRes, externalRes, luRes] = await Promise.all([
        api.get('/units'),
        api.get('/units/external'),
        api.get('/loyal-users'),
      ])
      if (unitsRes.ok) units.value = await unitsRes.json()
      else {
        const data = await unitsRes.json().catch(() => null)
        loadError.value = api.translateError(data, 'error.connection')
      }
      if (externalRes.ok) externalUnits.value = await externalRes.json()
      if (luRes.ok) loyalUsers.value = await luRes.json()
    } catch {
      loadError.value = t('error.connection')
    }
  })

  async function handleSubmit() {
    if (loading.value) return
    error.value = ''

    const rules = { originId: [required(originId.value, 'unitName')] }
    if (orderType.value === 'INTERNAL') {
      rules.destinationId = [required(destinationId.value, 'unitName')]
    } else if (orderType.value === 'B2C') {
      rules.recipientEmail = [required(recipientEmail.value, 'email'), emailRule(recipientEmail.value)]
    } else if (orderType.value === 'B2B') {
      rules.b2bOrgId = [required(b2bOrgId.value, 'organization')]
      rules.b2bDestinationId = [required(b2bDestinationId.value, 'unitName')]
    }
    if (!validate(rules)) return

    if (orderType.value === 'B2C') {
      const hasAddr = recipientAddress.value?.trim().length > 0
      const hasCoords = recipientLatitude.value != null && recipientLongitude.value != null
      if (!hasAddr && !hasCoords) {
        error.value = t('validation.recipientLocationRequired')
        return
      }
    }

    if (orderType.value === 'INTERNAL' && originId.value === destinationId.value) {
      error.value = t('orders.sameUnit')
      return
    }

    loading.value = true
    try {
      const body = {
        originId: originId.value,
        orderType: orderType.value,
        priority: priority.value,
        notes: notes.value.trim() || null,
      }
      if (orderType.value === 'INTERNAL') {
        body.destinationId = destinationId.value
      } else if (orderType.value === 'B2B') {
        body.destinationId = b2bDestinationId.value
      } else {
        body.recipientName = recipientName.value.trim() || null
        body.recipientEmail = recipientEmail.value.trim() || null
        body.recipientAddress = recipientAddress.value.trim() || null
        body.recipientLatitude = recipientLatitude.value
        body.recipientLongitude = recipientLongitude.value
      }

      const res = await api.post('/orders', body)
      if (res.ok) {
        const data = await res.json()
        router.push({ path: '/orders', query: { created: data.reference } })
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

  return {
    units, loyalUsers, loyalUserMatch, loadError,
    orderType, originId, destinationId, b2bOrgId, b2bDestinationId,
    recipientEmail, recipientName,
    recipientAddress, recipientLatitude, recipientLongitude, locating, captureLocation,
    priority, notes, loading, error, errors, invalids,
    destinationOptions, b2bOrganizations, b2bUnitOptions, handleSubmit,
  }
}
