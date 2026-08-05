import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { useValidation } from '@/composables/useValidation'
import { useGeolocation } from '@/composables/useGeolocation'
import { useServices } from './useServices'
import { useAuthStore } from '@/stores/auth'
import { useLoad } from './useLoad'

export function useOrderForm() {
  const { t } = useI18n()
  const { executeLoad } = useLoad()
  const router = useRouter()
  const api = useApi()
  const auth = useAuthStore()
  const dataApi = useServices("data-service")
  const { validate, required, email: emailRule, errors, invalids } = useValidation()

  const organizationCompaniesCache = new Map()
  const companyUnitsCache = new Map()

  const units = ref([])
  const externalUnits = ref([])
  const loyalUsers = ref([])
  const loadError = ref('')
  const orderType = ref(null) // 'INTERNAL' | 'B2C' | 'B2B'
  const organizationsLoaded = ref(false)
  const organizationCompanies = ref([])
  const companyUnits = ref([])
  const loyalUsersLoaded = ref(false)
  const originId = ref('')
  const destinationId = ref('')
  const b2bOrgId = ref('')
  const b2bCompanyId = ref('')
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
  
  const organizations = ref([])

  const destinationOptions = computed(() =>
    units.value.filter(u => u.id !== originId.value)
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
  onMounted(async () =>{
    await executeLoad(dataApi,'/units',units,loadError)
  })

  const organizationList = (data) => {
    return Object.entries(data).map( ([k,v]) => {
      return {id: k, name: v}
    }).filter((entry) => auth.orgId != entry.id)
  }

  const companyList = (data) => {
    return Object.entries(data).map( ([k,v]) => {
      return {id: k, name: v}
    }).filter((entry) => auth.companyId != entry.id)
  }

  const unitList = (data) => {
    return Object.entries(data).map( ([k,v]) => {
      return {id: k, name: v}
    })
  }

  const executeLoadB2B = async () => {
    await executeLoad(
      api,'/organizations/names', organizations, 
      loadError, organizationsLoaded, organizationList
    )
  }

  const executeLoadB2C = async () => {
    await executeLoad(
      api,'/loyal-users',loyalUsers,loadError, loyalUsersLoaded
    )
  }
 

  watch(orderType, async () => {
    if (!orderType.value) return;

    const loaders = {
      "B2B": executeLoadB2B,
      "B2C": executeLoadB2C
    }
    const loader = loaders[orderType.value]

    if (loader) {
      await loader()
    }
  })

  watch(b2bOrgId, async () => {
    if (!b2bOrgId.value) return;
    if (organizationCompaniesCache.has(b2bOrgId.value)) {
      organizationCompanies.value = organizationCompaniesCache.get(b2bOrgId.value)
      return
    }
    await executeLoad(api,`/companies/names?orgId=${b2bOrgId.value}`,organizationCompanies , 
      loadError, null, companyList)
    organizationCompaniesCache.set(b2bOrgId.value, organizationCompanies.value)
  })

  watch(b2bCompanyId, async () => {
    if (!b2bCompanyId.value) return;
    b2bDestinationId.value = ""
    if (companyUnitsCache.has(b2bCompanyId.value)) {
      companyUnits.value = companyUnitsCache.get(b2bCompanyId.value)
      return
    }
    await executeLoad(dataApi,`/units/names?companyId=${b2bCompanyId.value}`,companyUnits , 
      loadError, null, unitList)
    companyUnitsCache.set(b2bCompanyId.value, companyUnits.value)
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
      let res
      if (orderType.value === 'B2C') {
        res = await api.post('/orders/B2C', body)
      } else {
        res = await dataApi.post('/orders', body)
      }

      if (res?.ok) {
        const data = await res.json()
        router.push({ path: '/orders', query: { created: data.reference } })
      } else {
        const data = res? await res.json() : null
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
    orderType, originId, destinationId, b2bOrgId, b2bDestinationId,companyUnits,
    recipientEmail, recipientName,b2bCompanyId,organizationCompanies,
    recipientAddress, recipientLatitude, recipientLongitude, locating, captureLocation,
    priority, notes, loading, error, errors, invalids,organizations,
    destinationOptions, handleSubmit,
  }
}
