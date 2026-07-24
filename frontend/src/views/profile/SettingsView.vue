<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { useAuthStore } from '@/stores/auth'
import { useValidation } from '@/composables/useValidation'
import { useActivityTypes } from '@/composables/useActivityTypes'
import { buildPriorityOptions } from '@/composables/useOrderPriority'
import { useAppConfig } from '@/composables/useAppConfig'
import DeleteConfirmPanel from '@/components/DeleteConfirmPanel.vue'
import ApiKeysSection from './ApiKeysSection.vue'
import DeviceSecction from './components/DeviceSecction.vue'
import { useServices } from '@/composables/useServices.js'

const { t } = useI18n()
const router = useRouter()
const api = useApi()
const dataApi = useServices("data-service")
const authApi = useServices("auth-service")
const auth = useAuthStore()
const { validate, required, errors: vErrors, invalids } = useValidation()
const { activityTypes, load: loadActivityTypes } = useActivityTypes()
const { fileMaxUploadBytes, load: loadAppConfig } = useAppConfig()

const settings = ref(null)
const allCompanies = ref([])
const subscription = ref(null)
const loadError = ref('')
const activeTab = ref(0)

// Edición org
const editingOrg = ref(false)
const orgName = ref('')
const orgHandle = ref('')
const orgSaving = ref(false)
const orgError = ref('')
const orgSuccess = ref(false)

// Edición empresa
const editingCompanyId = ref(null)
const companyName = ref('')
const activityType = ref(null)
const defaultPriority = ref(null)
const defaultPriorityLocked = ref(false)
const companySaving = ref(false)
const companyError = ref('')
const companySuccess = ref(false)
const prioritySettingsSaving = ref(false)
const priorityError = ref('')

// Búsqueda y paginación de empresas
const companySearch = ref('')
const companyTypeFilter = ref('ALL')
const companyPage = ref(1)
const PAGE_SIZE = 5
const companiesFiltered = computed(() =>
  allCompanies.value.filter(c => {
    if (companySearch.value && !c.name.toLowerCase().includes(companySearch.value.toLowerCase())) return false
    if (companyTypeFilter.value !== 'ALL' && c.activityType !== companyTypeFilter.value) return false
    return true
  })
)
const companiesPaged = computed(() => {
  const start = (companyPage.value - 1) * PAGE_SIZE
  return companiesFiltered.value.slice(start, start + PAGE_SIZE)
})
const totalPages = computed(() => Math.ceil(companiesFiltered.value.length / PAGE_SIZE))
watch([companySearch, companyTypeFilter], () => { companyPage.value = 1 })

const companyTypeOptions = computed(() => [
  { label: t('settings.filterAll'), value: 'ALL' },
  ...activityTypes.value.map(a => ({ label: a.label, value: a.value }))
])

const defaultPriorityOptions = computed(() => buildPriorityOptions(t))

// Logo crop (igual que avatar en perfil)
const LOGO_CANVAS_SIZE = 280
const logoFileInput = ref(null)
const logoCropPreview = ref(null)
const logoCropSaving = ref(false)
const logoCropCanvas = ref(null)
const logoCropImg = ref(null)
const logoCropScale = ref(1)
const logoCropOffset = ref({ x: 0, y: 0 })
const logoIsDragging = ref(false)
const logoDragStart = ref({ x: 0, y: 0, ox: 0, oy: 0 })
let logoLastTouchDist = 0

function getLogoMinScale() {
  if (!logoCropImg.value) return 1
  return Math.max(LOGO_CANVAS_SIZE / logoCropImg.value.naturalWidth, LOGO_CANVAS_SIZE / logoCropImg.value.naturalHeight)
}
function clampLogoOffset(ox, oy) {
  if (!logoCropImg.value) return { x: 0, y: 0 }
  const imgW = logoCropImg.value.naturalWidth * logoCropScale.value
  const imgH = logoCropImg.value.naturalHeight * logoCropScale.value
  const halfDiffX = Math.max(0, (imgW - LOGO_CANVAS_SIZE) / 2)
  const halfDiffY = Math.max(0, (imgH - LOGO_CANVAS_SIZE) / 2)
  return { x: Math.max(-halfDiffX, Math.min(halfDiffX, ox)), y: Math.max(-halfDiffY, Math.min(halfDiffY, oy)) }
}
function drawLogoCrop() {
  const canvas = logoCropCanvas.value
  if (!canvas || !logoCropImg.value) return
  const ctx = canvas.getContext('2d')
  const imgW = logoCropImg.value.naturalWidth * logoCropScale.value
  const imgH = logoCropImg.value.naturalHeight * logoCropScale.value
  const x = (LOGO_CANVAS_SIZE - imgW) / 2 + logoCropOffset.value.x
  const y = (LOGO_CANVAS_SIZE - imgH) / 2 + logoCropOffset.value.y
  ctx.clearRect(0, 0, LOGO_CANVAS_SIZE, LOGO_CANVAS_SIZE)
  ctx.drawImage(logoCropImg.value, x, y, imgW, imgH)
}
function initLogoCrop(dataURL) {
  const img = new Image()
  img.onload = () => { logoCropImg.value = img; logoCropScale.value = getLogoMinScale(); logoCropOffset.value = { x: 0, y: 0 }; drawLogoCrop() }
  img.src = dataURL
}
watch(logoCropPreview, async (val) => { if (val) { await nextTick(); initLogoCrop(val) } else { logoCropImg.value = null } })
function onLogoCropWheel(e) {
  logoCropScale.value = Math.max(getLogoMinScale(), Math.min(4, logoCropScale.value * (e.deltaY < 0 ? 1.1 : 0.9)))
  logoCropOffset.value = clampLogoOffset(logoCropOffset.value.x, logoCropOffset.value.y)
  drawLogoCrop()
}
function onLogoCropMouseDown(e) { logoIsDragging.value = true; logoDragStart.value = { x: e.clientX, y: e.clientY, ox: logoCropOffset.value.x, oy: logoCropOffset.value.y } }
function onLogoCropMouseMove(e) {
  if (!logoIsDragging.value) return
  logoCropOffset.value = clampLogoOffset(logoDragStart.value.ox + e.clientX - logoDragStart.value.x, logoDragStart.value.oy + e.clientY - logoDragStart.value.y)
  drawLogoCrop()
}
function onLogoCropMouseUp() { logoIsDragging.value = false }
function onLogoCropTouchStart(e) {
  if (e.touches.length === 1) { logoIsDragging.value = true; logoDragStart.value = { x: e.touches[0].clientX, y: e.touches[0].clientY, ox: logoCropOffset.value.x, oy: logoCropOffset.value.y } }
  else if (e.touches.length === 2) { logoIsDragging.value = false; logoLastTouchDist = Math.hypot(e.touches[1].clientX - e.touches[0].clientX, e.touches[1].clientY - e.touches[0].clientY) }
}
function onLogoCropTouchMove(e) {
  if (e.touches.length === 1 && logoIsDragging.value) {
    logoCropOffset.value = clampLogoOffset(logoDragStart.value.ox + e.touches[0].clientX - logoDragStart.value.x, logoDragStart.value.oy + e.touches[0].clientY - logoDragStart.value.y)
    drawLogoCrop()
  } else if (e.touches.length === 2) {
    const dist = Math.hypot(e.touches[1].clientX - e.touches[0].clientX, e.touches[1].clientY - e.touches[0].clientY)
    logoCropScale.value = Math.max(getLogoMinScale(), Math.min(4, logoCropScale.value * dist / logoLastTouchDist))
    logoLastTouchDist = dist
    logoCropOffset.value = clampLogoOffset(logoCropOffset.value.x, logoCropOffset.value.y)
    drawLogoCrop()
  }
}
function onLogoCropTouchEnd() { logoIsDragging.value = false }
function triggerLogoFileInput() { logoFileInput.value?.click() }
function handleLogoFileChange(e) {
  const file = e.target.files?.[0]; e.target.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type)) { companyError.value = t('profile.avatarInvalidType'); return }
  if (file.size > fileMaxUploadBytes.value) { companyError.value = t('settings.logoTooLarge'); return }
  companyError.value = ''
  const reader = new FileReader()
  reader.onload = ev => { logoCropPreview.value = ev.target.result }
  reader.readAsDataURL(file)
}
function cancelLogoCrop() { logoCropPreview.value = null; logoCropImg.value = null }
async function confirmLogoCrop() {
  if (!logoCropImg.value) return
  logoCropSaving.value = true
  try {
    const output = document.createElement('canvas')
    output.width = 256; output.height = 256
    const ctx = output.getContext('2d')
    const ratio = 256 / LOGO_CANVAS_SIZE
    ctx.drawImage(logoCropImg.value, (256 - logoCropImg.value.naturalWidth * logoCropScale.value * ratio) / 2 + logoCropOffset.value.x * ratio, (256 - logoCropImg.value.naturalHeight * logoCropScale.value * ratio) / 2 + logoCropOffset.value.y * ratio, logoCropImg.value.naturalWidth * logoCropScale.value * ratio, logoCropImg.value.naturalHeight * logoCropScale.value * ratio)
    const res = await api.put('/settings/company/logo', { data: output.toDataURL('image/jpeg', 0.9) })
    if (res.ok) {
      const updated = await res.json()
      const idx = allCompanies.value.findIndex(c => c.id === editingCompanyId.value)
      if (idx !== -1) allCompanies.value[idx] = { ...allCompanies.value[idx], logoData: updated.logoData }
      auth.loadCompanies()
      logoCropPreview.value = null; logoCropImg.value = null
    } else { companyError.value = t('error.saveFailed') }
  } catch { companyError.value = t('error.connection') }
  finally { logoCropSaving.value = false }
}

// Eliminar empresa
const deletingCompanyId = ref(null)
const deleteHasActiveOrders = ref(false)
const deleteError = ref('')
const deleteLoading = ref(false)

// Añadir empresa
const addingCompany = ref(false)
const newCompanyName = ref('')
const newActivityType = ref(null)
const addSaving = ref(false)
const addError = ref('')

// Plan change
const pendingPlanCode = ref(null)
const planChanging = ref(false)
const planChangeError = ref('')

const activityOptions = computed(() =>
  activityTypes.value.map(a => ({ label: a.label, value: a.value }))
)

function activityLabel(code) {
  return activityTypes.value.find(a => a.value === code)?.label ?? code
}

const PLANS = [
  { code: 'FREE',  name: 'Free',  price: 'Gratis',  units: 3,  workers: 5,  ordersThisMonth: 50,  loyalUsers: 20,  companies: 1,  color: 'free' },
  { code: 'BASIC', name: 'Basic', price: '29€/mes', units: 10, workers: 15, ordersThisMonth: 200, loyalUsers: 100, companies: 5,  color: 'basic' },
  { code: 'PRO',   name: 'Pro',   price: '99€/mes', units: -1, workers: -1, ordersThisMonth: -1,  loyalUsers: -1,  companies: -1, color: 'pro' },
]

const planOrder = { FREE: 0, BASIC: 1, PRO: 2 }

const downgradeRisks = computed(() => {
  if (!pendingPlanCode.value || !subscription.value) return []
  const plan = PLANS.find(p => p.code === pendingPlanCode.value)
  if (!plan) return []
  return ['units', 'workers', 'loyalUsers', 'companies']
    .filter(k => plan[k] !== -1 && subscription.value[k]?.current > plan[k])
    .map(k => ({ key: k, current: subscription.value[k].current, max: plan[k] }))
})

function isDowngrade(targetCode) {
  if (!subscription.value) return false
  return planOrder[targetCode] < planOrder[subscription.value.planCode]
}

function requestPlanChange(planCode) {
  planChangeError.value = ''
  pendingPlanCode.value = planCode
}

async function selectPlan(planCode, force = false) {
  planChangeError.value = ''
  planChanging.value = true
  try {
    const res = await api.patch('/settings/subscription/plan', { planCode, force })
    if (res.ok) {
      subscription.value = await res.json()
      pendingPlanCode.value = null
      // Refrescar lista de empresas: el downgrade puede haber eliminado excedentes
      const compRes = await api.get('/settings/companies')
      if (compRes.ok) allCompanies.value = await compRes.json()
      auth.loadCompanies()
    } else {
      const data = await res.json()
      planChangeError.value = api.translateError(data, 'error.saveFailed')
    }
  } catch {
    planChangeError.value = t('error.connection')
  } finally {
    planChanging.value = false
  }
}

async function reloadSubscription() {
  const res = await api.get('/settings/subscription')
  if (res.ok) subscription.value = await res.json()
}

async function  combineSettings(sett,dataSett) {
  sett.defaultPriority = dataSett.defaultPriority
  sett.defaultPriorityLocked = dataSett.defaultPriorityLocked
  return sett
}

async function load() {
  loadError.value = ''
  try {
    const [settRes, compRes, subRes, dataSettRes ] = await Promise.all([
      api.get('/settings'),
      api.get('/settings/companies'),
      api.get('/settings/subscription'),
      dataApi.get('/settings')
    ])
    if (settRes.ok && dataSettRes.ok) settings.value = await combineSettings(
        await settRes.json(), await dataSettRes.json()
    )
    else loadError.value = t('error.connection')
    console.log(settings.value)
    if (compRes.ok) allCompanies.value = await compRes.json()
    // TODO: PROVISONAL FIX
    allCompanies.value = [...allCompanies.value].map((company) => {
      if (company.id === settings.value.companyId) {
        company.defaultPriority = settings.value.defaultPriority
        company.defaultPriorityLocked = settings.value.defaultPriorityLocked
      }
      return company
    })
    console.log(allCompanies.value)
    if (subRes.ok) subscription.value = await subRes.json()
  } catch {
    loadError.value = t('error.connection')
  }
}

function usagePercent(usage) {
  if (!usage || usage.unlimited) return 0
  return Math.round((usage.current / usage.max) * 100)
}
const showUpgradeBanner = computed(() =>
  subscription.value && ['units','workers','ordersThisMonth','loyalUsers','companies'].some(k => {
    const r = subscription.value[k]
    return !r.unlimited && usagePercent(r) > 85
  })
)


watch(() => subscription.value?.planCode, code => { if (code) auth.setPlanCode(code) })

onMounted(() => { load(); loadActivityTypes(); loadAppConfig() })

function startEditOrg() {
  orgName.value = settings.value.orgName
  orgHandle.value = settings.value.orgHandle
  orgError.value = ''
  orgSuccess.value = false
  editingOrg.value = true
}

async function saveOrg() {
  if (!validate({ orgName: [required(orgName.value, 'orgName')] })) return
  if (!orgHandle.value || !/^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/.test(orgHandle.value)) {
    orgError.value = t('validation.orgCodeInvalid')
    return
  }
  orgSaving.value = true
  orgError.value = ''
  try {
    const res = await api.put('/settings/organization', { name: orgName.value, handle: orgHandle.value })
    if (res.ok) {
      settings.value = await res.json()
      auth.setOrganization(settings.value.orgHandle)
      auth.setOrgName(settings.value.orgName)
      editingOrg.value = false
      orgSuccess.value = true
      setTimeout(() => { orgSuccess.value = false }, 3000)
    } else {
      const data = await res.json()
      orgError.value = api.translateError(data, 'error.saveFailed')
    }
  } catch {
    orgError.value = t('error.connection')
  } finally {
    orgSaving.value = false
  }
}

function startEditCompany(company) {
  if (company.id !== settings.value?.companyId) return
  console.log(company)
  editingCompanyId.value = company.id
  companyName.value = company.name
  activityType.value = company.activityType
  defaultPriority.value = company.defaultPriority || null
  defaultPriorityLocked.value = !!company.defaultPriorityLocked
  companyError.value = ''
  companySuccess.value = false
  prioritySettingsSaving.value = false
  priorityError.value = false
}

function cancelEditCompany() {
  editingCompanyId.value = null
  companyError.value = ''
}

async function saveCompany() {
  if (!validate({ companyName: [required(companyName.value, 'companyName')] })) return
  if (!activityType.value) { companyError.value = t('validation.activityTypeRequired'); return }
  companySaving.value = true
  companyError.value = ''
  try {
    const res = await api.put('/settings/company', { name: companyName.value, activityType: activityType.value, defaultPriority: defaultPriority.value, defaultPriorityLocked: defaultPriorityLocked.value })

    if (res.ok) {
      const updated = await res.json()
      settings.value = updated
      const idx = allCompanies.value.findIndex(c => c.id === editingCompanyId.value)
      if (idx !== -1) allCompanies.value[idx] = { ...allCompanies.value[idx], name: companyName.value, activityType: activityType.value, defaultPriority: defaultPriority.value, defaultPriorityLocked: defaultPriorityLocked.value }
      if (updated.companyId === editingCompanyId.value) auth.setCompanyName(updated.companyName)
      editingCompanyId.value = null
      companySuccess.value = true
      setTimeout(() => { companySuccess.value = false }, 3000)
    } else {
      const data = await res.json()
      companyError.value = api.translateError(data, 'error.saveFailed')
    }
  } catch {
    companyError.value = t('error.connection')
  } finally {
    companySaving.value = false
  }
}

async function savePrioritySettings() {
  prioritySettingsSaving.value = true
  priorityError.value = ''

  try {
    const res = await dataApi.put('/settings', {
      defaultPriority: defaultPriority.value,
      defaultPriorityLocked: defaultPriorityLocked.value
    })
    console.log(res.status)
    if (res.status == 204) {
      settings.value.defaultPriority =
        defaultPriority.value

      settings.value.defaultPriorityLocked =
        defaultPriorityLocked.value

      const idx = allCompanies.value.findIndex(
        c => c.id === editingCompanyId.value
      )

      if (idx !== -1) {
        allCompanies.value[idx] = {
          ...allCompanies.value[idx],
          defaultPriority:defaultPriority.value,
          defaultPriorityLocked: defaultPriorityLocked.value
        }
      }

      priorityError.value = ''
      companySuccess.value = true
      editingCompanyId.value = null
      
      setTimeout(() => {
        companySuccess.value = false
      }, 3000)

    } else {
      const data = await res.json()
      priorityError.value =
        dataApi.translateError(data, 'error.saveFailed')
    }

  } catch {
    priorityError.value = t('error.connection')
  } finally {
    prioritySettingsSaving.value = false
  }
}
  


function startDeleteCompany(id) {
  editingCompanyId.value = null
  deletingCompanyId.value = id
  deleteHasActiveOrders.value = false
  deleteError.value = ''
}

function cancelDelete() {
  deletingCompanyId.value = null
  deleteHasActiveOrders.value = false
  deleteError.value = ''
}

async function confirmDeleteCompany(id) {
  deleteLoading.value = true
  deleteError.value = ''
  const isCurrent = id === settings.value?.companyId
  const force = deleteHasActiveOrders.value
  try {
    if (isCurrent) {
      const next = allCompanies.value.find(c => c.id !== id)
      if (!next) return
      const switchRes = await authApi.post('/auth/switch-company', { companyId: next.id })
      if (!switchRes.ok) { deleteError.value = t('error.connection'); return }
      auth.applyLoginData(await switchRes.json())
    }
    const url = force ? `/settings/companies/${id}?force=true` : `/settings/companies/${id}`
    const res = await api.del(url)
    console.log(res.status
    )
    if (res.status === 204) {
      if (isCurrent) {
        router.push('/settings')
      } else {
        allCompanies.value = allCompanies.value.filter(c => c.id !== id)
        auth.loadCompanies()
        reloadSubscription()
        deletingCompanyId.value = null
        deleteHasActiveOrders.value = false
      }
    } else {
      const data = await res.json()
      if (data?.code === 'COMPANY_HAS_ACTIVE_ORDERS') {
        deleteHasActiveOrders.value = true
        deleteError.value = ''
      } else {
        deleteError.value = api.translateError(data, 'error.saveFailed')
      }
    }
  } catch {
    deleteError.value = t('error.connection')
  } finally {
    deleteLoading.value = false
  }
}

function toggleAddCompany() {
  addError.value = ''
  if (addingCompany.value) {
    addingCompany.value = false
    return
  }
  const sub = subscription.value
  if (sub && !sub.companies.unlimited && sub.companies.current >= sub.companies.max) {
    addError.value = t('settings.companyLimitReached')
    return
  }
  addingCompany.value = true
}

async function addCompany() {
  if (!newCompanyName.value.trim()) { addError.value = t('validation.required', { field: t('fields.companyName') }); return }
  if (!newActivityType.value) { addError.value = t('validation.activityTypeRequired'); return }
  addSaving.value = true
  addError.value = ''
  try {
    const res = await api.post('/settings/companies', { name: newCompanyName.value.trim(), activityType: newActivityType.value })
    if (res.ok) {
      const created = await res.json()
      allCompanies.value.push(created)
      auth.loadCompanies()
      reloadSubscription()
      newCompanyName.value = ''
      newActivityType.value = null
      addingCompany.value = false
    } else {
      const data = await res.json()
      addError.value = api.translateError(data, 'error.saveFailed')
    }
  } catch {
    addError.value = t('error.connection')
  } finally {
    addSaving.value = false
  }
}

async function copyHandle() {
  if (!settings.value?.orgHandle) return
  await navigator.clipboard.writeText(settings.value.orgHandle)
}
</script>

<template>
  <div class="settings-page">
    <div class="surface-card settings-card">
      <h1 class="settings-title">{{ t('settings.title') }}</h1>
      <p class="settings-subtitle">{{ t('settings.subtitle') }}</p>

      <PMessage v-if="loadError" severity="error" :closable="false" class="form-message">{{ loadError }}</PMessage>
      <PMessage v-if="showUpgradeBanner" severity="warn" :closable="true" class="form-message">{{ t('settings.upgradeBanner') }}</PMessage>

      <PTabs v-if="settings" v-model:value="activeTab">
        <PTabList>
          <PTab :value="0">{{ t('settings.orgSection') }}</PTab>
          <PTab :value="1">{{ t('settings.companySection') }}</PTab>
          <PTab :value="2">{{ t('settings.subscriptionSection') }}</PTab>
          <PTab :value="3">{{ t('settings.apiKeysSection') }}</PTab>
          <PTab :value="4">{{ t('settings.devices.name') }}</PTab>
        </PTabList>

        <PTabPanels>
          <!-- === Organización === -->
          <PTabPanel :value="0">
            <div class="settings-section">
              <PMessage v-if="orgSuccess" severity="success" :closable="false" class="form-message">{{ t('settings.saved') }}</PMessage>

              <div v-if="!editingOrg" class="info-panel">
                <div class="info-row">
                  <span class="info-label">{{ t('fields.orgName') }}</span>
                  <span class="info-value">{{ settings.orgName }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">{{ t('settings.orgCode') }}</span>
                  <span class="info-value">
                    <code class="code-badge">{{ settings.orgHandle }}</code>
                    <PButton icon="pi pi-copy" text severity="secondary" size="small" @click="copyHandle" v-tooltip="t('settings.orgCodeCopied')" />
                  </span>
                </div>
                <small class="field-hint">{{ t('settings.orgCodeHint') }}</small>
                <div class="section-actions">
                  <PButton :label="t('settings.editOrg')" icon="pi pi-pencil" severity="secondary" outlined size="small" @click="startEditOrg" />
                </div>
              </div>

              <div v-else class="settings-form">
                <p class="edit-form-title">{{ t('settings.editOrg') }}</p>
                <div class="form-field">
                  <label for="settings-org-name">{{ t('fields.orgName') }}</label>
                  <InputText id="settings-org-name" v-model="orgName" :placeholder="t('fields.orgNamePlaceholder')" maxlength="255" :invalid="!!invalids.orgName" fluid />
                  <small v-if="vErrors.orgName" class="field-error">{{ vErrors.orgName }}</small>
                </div>
                <div class="form-field">
                  <label for="settings-org-handle">{{ t('settings.orgCode') }}</label>
                  <InputText id="settings-org-handle" v-model="orgHandle" :placeholder="t('fields.orgCodePlaceholder')" maxlength="100" fluid />
                  <small class="field-hint">{{ t('fields.orgCodeHint') }}</small>
                </div>
                <PMessage v-if="orgError" severity="error" :closable="false" class="form-message">{{ orgError }}</PMessage>
                <div class="form-actions">
                  <PButton :label="t('common.cancel')" icon="pi pi-times" severity="secondary" outlined size="small" @click="editingOrg = false; orgError = ''" />
                  <PButton :label="t('common.save')" icon="pi pi-check" :loading="orgSaving" size="small" @click="saveOrg" />
                </div>
              </div>
            </div>
          </PTabPanel>

          <!-- === Empresa === -->
          <PTabPanel :value="1">
            <div class="settings-section">
              <PMessage v-if="companySuccess" severity="success" :closable="false" class="form-message">{{ t('settings.saved') }}</PMessage>

              <!-- Buscador y filtro -->
              <div class="company-filters">
                <div class="company-search">
                  <span class="company-search-icon pi pi-search" />
                  <PInputText v-model="companySearch" class="company-search-input" :placeholder="t('settings.searchByName')" />
                </div>
                <PSelect
                  v-model="companyTypeFilter"
                  :options="companyTypeOptions"
                  option-label="label"
                  option-value="value"
                  class="company-type-filter"
                />
              </div>

              <!-- Lista de empresas -->
              <div class="company-list">
                <div v-for="c in companiesPaged" :key="c.id" class="company-item-wrapper">
                  <template v-if="editingCompanyId === c.id">
                    <div class="settings-form company-edit-form">
                      <p class="edit-form-title">{{ c.name }}</p>
                      <!-- Logo (solo empresa actual) -->
                      <div v-if="c.id === settings.companyId" class="logo-upload-wrap">
                        <div class="logo-upload-avatar" @click="triggerLogoFileInput" :title="t('settings.uploadLogo')">
                          <img v-if="c.logoData" :src="c.logoData" class="logo-upload-img" alt="Logo" />
                          <div v-else class="logo-upload-placeholder">{{ c.name[0]?.toUpperCase() }}</div>
                          <div class="logo-upload-overlay"><i class="pi pi-camera" /></div>
                        </div>
                        <div class="logo-upload-hint">
                          <span class="logo-upload-label">{{ t('settings.uploadLogo') }}</span>
                          <small class="field-hint">{{ t('settings.logoHint') }}</small>
                        </div>
                        <input ref="logoFileInput" type="file" accept="image/jpeg,image/png" style="display:none" @change="handleLogoFileChange" />
                      </div>
                      <div class="company-edit-form-section">
                        <h2>{{t('settings.companyInfo')}}</h2>
                        <div class="form-field">
                          <label for="settings-company-name">{{ t('fields.companyName') }}</label>
                          <InputText id="settings-company-name" v-model="companyName" :placeholder="t('fields.companyNamePlaceholder')" maxlength="255" :invalid="!!invalids.companyName" fluid />
                          <small v-if="vErrors.companyName" class="field-error">{{ vErrors.companyName }}</small>
                        </div>
                        <div class="form-field">
                          <label for="settings-activity-type">{{ t('fields.type') }}</label>
                          <PSelect input-id="settings-activity-type" v-model="activityType" :options="activityOptions" option-label="label" option-value="value" fluid />
                        </div>
                        <div class="form-actions">
                            <PButton :label="t('common.cancel')" icon="pi pi-times" severity="secondary" outlined size="small" @click="cancelEditCompany" />
                            <PButton :label="t('common.save')" icon="pi pi-check" :loading="companySaving" size="small" @click="saveCompany" />
                        </div>
                      </div>
                      <div class="company-edit-form-section">
                        <h2>{{t('settings.prioritySettings')}}</h2>
                        <div class="form-field">
                          <label for="settings-default-priority">{{ t('settings.defaultPriority') }}</label>
                          <PSelect input-id="settings-default-priority" v-model="defaultPriority" :options="defaultPriorityOptions" option-label="label" option-value="value" fluid show-clear />
                          <small class="field-help">{{ t('settings.defaultPriorityHelp') }}</small>
                        </div>
                        <div class="form-field">
                          <label for="settings-priority-lock" class="lock-checkbox">
                            <PCheckbox input-id="settings-priority-lock" v-model="defaultPriorityLocked" :binary="true" />
                            {{ t('settings.defaultPriorityLock') }}
                          </label>
                          <small class="field-help">{{ t('settings.defaultPriorityLockHelp') }}</small>
                        </div>
                        <PMessage v-if="priorityError" severity="error" :closable="false" class="form-message">{{ priorityError }}</PMessage>
                        <div class="form-actions">
                          <PButton :label="t('common.cancel')" icon="pi pi-times" severity="secondary" outlined size="small" @click="cancelEditCompany" />
                          <PButton :label="t('common.save')" icon="pi pi-check" :loading="prioritySettingsSaving" size="small" @click="savePrioritySettings" />
                        </div>
                      </div>
                      
                    </div>
                  </template>
                  <template v-else>
                    <div class="company-item-row" @click="startEditCompany(c)">
                      <div class="company-item-info">
                        <div class="company-item-avatar">
                          <img v-if="c.logoData" :src="c.logoData" style="width:100%;height:100%;object-fit:cover;border-radius:8px" alt="" />
                          <span v-else>{{ c.name[0].toUpperCase() }}</span>
                        </div>
                        <div>
                          <span class="company-item-name">{{ c.name }}</span>
                          <span v-if="c.id === settings.companyId" class="company-item-badge">{{ t('settings.current') }}</span>
                          <span class="company-item-type">{{ activityLabel(c.activityType) }}</span>
                        </div>
                      </div>
                      <div class="company-item-meta" @click.stop>
                        <PButton icon="pi pi-pencil" text rounded size="small" :aria-label="t('settings.editCompany')"
                                 v-tooltip.top="t('settings.editCompany')" @click="startEditCompany(c)" />
                        <PButton v-if="allCompanies.length > 1" icon="pi pi-times" text rounded severity="danger" size="small"
                                 :aria-label="t('common.delete')" v-tooltip.top="t('common.delete')"
                                 @click="startDeleteCompany(c.id)" />
                      </div>
                    </div>
                    <Transition name="delete-panel">
                      <DeleteConfirmPanel
                        v-if="deletingCompanyId === c.id"
                        :has-active-orders="deleteHasActiveOrders"
                        :loading="deleteLoading"
                        :error="deleteError"
                        @confirm="confirmDeleteCompany(c.id)"
                        @cancel="cancelDelete"
                      />
                    </Transition>
                  </template>
                </div>
              </div>

              <!-- Empty state filtro -->
              <EmptyState v-if="companiesFiltered.length === 0" icon="pi-building" :message="t('settings.noCompaniesFound')" />

              <!-- Paginación -->
              <div v-if="totalPages > 1" class="company-pagination">
                <PButton icon="pi pi-chevron-left" text size="small" :disabled="companyPage === 1" @click="companyPage--" />
                <span class="pagination-info">{{ companyPage }} / {{ totalPages }}</span>
                <PButton icon="pi pi-chevron-right" text size="small" :disabled="companyPage === totalPages" @click="companyPage++" />
              </div>

              <!-- Añadir empresa -->
              <PMessage v-if="addError && !addingCompany" severity="error" :closable="false" class="form-message">{{ addError }}</PMessage>
              <div v-if="!addingCompany" class="add-company-trigger">
                <PButton :label="t('settings.addCompany')" icon="pi pi-plus" size="small" text @click="toggleAddCompany" />
              </div>
              <Transition name="slide-down">
                <div v-if="addingCompany" class="add-company-form">
                  <div class="form-field">
                    <label for="settings-new-company-name">{{ t('fields.companyName') }}</label>
                    <InputText id="settings-new-company-name" v-model="newCompanyName" :placeholder="t('fields.companyNamePlaceholder')" maxlength="255" fluid />
                  </div>
                  <div class="form-field">
                    <label for="settings-new-activity-type">{{ t('fields.type') }}</label>
                    <PSelect input-id="settings-new-activity-type" v-model="newActivityType" :options="activityOptions" option-label="label" option-value="value" fluid />
                  </div>
                  <PMessage v-if="addError" severity="error" :closable="false" class="form-message">{{ addError }}</PMessage>
                  <div class="form-actions">
                    <PButton :label="t('common.cancel')" icon="pi pi-times" severity="secondary" outlined size="small" @click="toggleAddCompany" />
                    <PButton :label="t('settings.addCompany')" icon="pi pi-check" :loading="addSaving" size="small" @click="addCompany" />
                  </div>
                </div>
              </Transition>
            </div>
          </PTabPanel>

          <!-- === Suscripción === -->
          <PTabPanel :value="2">
            <div v-if="subscription" class="settings-section">
              <div class="plans-grid">
                <div v-for="plan in PLANS" :key="plan.code"
                  :class="['plan-card', 'plan-card--' + plan.color, { 'plan-card--current': subscription?.planCode === plan.code, 'plan-card--pending': pendingPlanCode === plan.code }]">
                  <div class="plan-card-top">
                    <span :class="['plan-badge', 'plan-badge--' + plan.color]">{{ plan.name }}</span>
                    <span v-if="subscription?.planCode === plan.code" class="plan-current-label">{{ t('settings.currentPlan') }}</span>
                  </div>
                  <span class="plan-card-price">{{ plan.price }}</span>
                  <ul class="plan-features">
                    <li v-for="key in ['units','workers','ordersThisMonth','loyalUsers','companies']" :key="key">
                      <i :class="['pi', plan[key] === -1 ? 'pi-check-circle' : 'pi-circle']" />
                      <span>{{ t('settings.resource.' + key) }}</span>
                      <strong>{{ plan[key] === -1 ? '∞' : plan[key] }}</strong>
                    </li>
                  </ul>

                  <!-- Confirmación inline al cambiar de plan -->
                  <Transition name="plan-confirm">
                    <div v-if="pendingPlanCode === plan.code" class="plan-confirm">
                      <div v-if="downgradeRisks.length" class="plan-confirm-risks">
                        <p class="plan-confirm-risks-title">{{ t('settings.downgradeRisksTitle') }}</p>
                        <ul>
                          <li v-for="risk in downgradeRisks" :key="risk.key">
                            <i class="pi pi-exclamation-triangle" />
                            {{ t('settings.resource.' + risk.key) }}: {{ t('settings.downgradeRiskDelete', { count: risk.current - risk.max }) }}
                          </li>
                        </ul>
                      </div>
                      <PMessage v-if="planChangeError && pendingPlanCode === plan.code" severity="error" :closable="false" class="plan-confirm-error">{{ planChangeError }}</PMessage>
                      <div class="plan-confirm-actions">
                        <PButton :label="t('common.cancel')" severity="secondary" text size="small" @click="pendingPlanCode = null; planChangeError = ''" />
                        <PButton
                          :label="planChanging ? t('common.loading') : t('settings.confirmDowngrade')"
                          :severity="downgradeRisks.length ? 'danger' : (isDowngrade(plan.code) ? 'danger' : 'primary')"
                          size="small"
                          :loading="planChanging"
                          @click="selectPlan(plan.code, downgradeRisks.length > 0)"
                        />
                      </div>
                    </div>
                  </Transition>

                  <template v-if="pendingPlanCode !== plan.code">
                    <PButton
                      v-if="subscription?.planCode !== plan.code"
                      :label="isDowngrade(plan.code) ? t('settings.downgradePlan') : t('settings.selectPlan')"
                      :severity="isDowngrade(plan.code) ? 'secondary' : 'primary'"
                      size="small"
                      class="plan-select-btn"
                      @click="requestPlanChange(plan.code)"
                    />
                    <div v-else class="plan-active-indicator">
                      <i class="pi pi-check" /> {{ t('settings.currentPlan') }}
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </PTabPanel>

          <!-- === API Keys === -->
          <PTabPanel :value="3">
            <ApiKeysSection />
          </PTabPanel>
          <PTabPanel :value="4">
            <DeviceSecction/>
          </PTabPanel>
        </PTabPanels>
      </PTabs>
    </div>


  <!-- Modal crop logo -->
  <div v-if="logoCropPreview" class="avatar-modal-backdrop" @click.self="cancelLogoCrop">
    <div class="avatar-modal">
      <h3 class="avatar-modal-title">{{ t('profile.avatarPreviewTitle') }}</h3>
      <p class="avatar-modal-hint">{{ t('profile.avatarPreviewHint') }}</p>
      <div class="crop-stage crop-stage--square">
        <canvas
          ref="logoCropCanvas"
          :width="LOGO_CANVAS_SIZE" :height="LOGO_CANVAS_SIZE"
          class="crop-canvas" :class="{ dragging: logoIsDragging }"
          @wheel.prevent="onLogoCropWheel"
          @mousedown="onLogoCropMouseDown" @mousemove="onLogoCropMouseMove"
          @mouseup="onLogoCropMouseUp" @mouseleave="onLogoCropMouseUp"
          @touchstart.prevent="onLogoCropTouchStart" @touchmove.prevent="onLogoCropTouchMove"
          @touchend="onLogoCropTouchEnd"
        />
        <div class="crop-overlay crop-overlay--square" />
      </div>
      <p class="crop-hint">{{ t('profile.avatarCropHint') }}</p>
      <div class="avatar-modal-actions">
        <PButton :label="t('profile.avatarCancel')" severity="secondary" outlined size="small" @click="cancelLogoCrop" :disabled="logoCropSaving" />
        <PButton :label="t('profile.avatarConfirm')" icon="pi pi-check" size="small" :loading="logoCropSaving" @click="confirmLogoCrop" />
      </div>
    </div>
  </div>
  </div>
</template>

<style scoped src="./SettingsView.css"></style>
