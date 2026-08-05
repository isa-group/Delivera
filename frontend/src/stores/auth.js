import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { WORKER_ROLES, ORDER_CREATOR_ROLES } from '@/constants/roles'
import { useServices } from '@/composables/useServices'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(null)
  const organizationHandle = ref(localStorage.getItem('organizationHandle') || null)
  const orgName = ref(localStorage.getItem('orgName') || null)
  const companyName = ref(localStorage.getItem('companyName') || null)
  const role = ref(localStorage.getItem('role') || null)
  const companyId = ref(localStorage.getItem('companyId') || null)
  const orgId = ref(localStorage.getItem('orgId') || null)
  const planCode = ref(localStorage.getItem('planCode') || null)
  // Empresas del usuario en la misma org (en memoria, se recarga cuando es necesario)
  const companies = ref([])

  const isAuthenticated = computed(() => !!token.value)
  const isCompanyAdmin = computed(() => role.value === 'COMPANY_ADMIN')
  const isWorker = computed(() => WORKER_ROLES.includes(role.value))
  const canCreateOrders = computed(() => ORDER_CREATOR_ROLES.includes(role.value))

  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setOrganization(handle = null) {
    organizationHandle.value = handle
    if (handle) localStorage.setItem('organizationHandle', handle)
    else localStorage.removeItem('organizationHandle')
  }

  function setOrgName(name = null) {
    orgName.value = name
    if (name) localStorage.setItem('orgName', name)
    else localStorage.removeItem('orgName')
  }

  function setCompanyName(name = null) {
    companyName.value = name
    if (name) localStorage.setItem('companyName', name)
    else localStorage.removeItem('companyName')
  }

  function setRole(newRole = null) {
    role.value = newRole
    if (newRole) localStorage.setItem('role', newRole)
    else localStorage.removeItem('role')
  }

  function setCompanyId(id = null) {
    companyId.value = id
    if (id) localStorage.setItem('companyId', id)
    else localStorage.removeItem('companyId')
  }

  function setPlanCode(code = null) {
    planCode.value = code
    if (code) localStorage.setItem('planCode', code)
    else localStorage.removeItem('planCode')
  }

  function setOrgId(id = null) {
    orgId.value = id
    if (id) {
      localStorage.setItem('orgId', id)
    } else {
      localStorage.removeItem('orgId')
    }
  }

  function parseJwt(token) {
    try {
      return JSON.parse(atob(token.split('.')[1]))
    } catch {
      return null
    }
  }

  function obtainOrgIdFromJwt(token) {
    const data = parseJwt(token)
    if (data == null) return;
    return data.orgId
  }


  function applyLoginData(data) {
    setToken(data.token)
    setRole(data.role ?? null)
    setCompanyId(data.companyId ?? null)
    setOrgId(obtainOrgIdFromJwt(data.token))
    setCompanyName(data.companyName ?? null)
    setOrganization(data.orgHandle ?? null)
    setOrgName(data.orgName ?? null)
    setPlanCode(null)
  }

  function logout() {
    token.value = ''
    user.value = null
    companies.value = []
    organizationHandle.value = null
    orgName.value = null
    companyName.value = null
    role.value = null
    companyId.value = null
    orgId.value = null
    localStorage.removeItem('orgId')
    localStorage.removeItem('token')
    localStorage.removeItem('organizationHandle')
    localStorage.removeItem('orgName')
    localStorage.removeItem('companyName')
    localStorage.removeItem('role')
    localStorage.removeItem('companyId')
    localStorage.removeItem('planCode')
    planCode.value = null
  }

  function setUser(profile) {
    user.value = profile
  }

  // Previene llamadas concurrentes (si ya hay una en vuelo, se reutiliza la promesa).
  let companiesInFlight = null
  async function loadCompanies() {
    if (!isCompanyAdmin.value || !token.value) return
    if (companiesInFlight) return companiesInFlight
    companiesInFlight = (async () => {
      try {
        const res = await fetch(`${import.meta.env.VITE_API_URL}/api/v2/settings/companies`, {
          headers: { Authorization: `Bearer ${token.value}` },
        })
        if (res.ok) companies.value = await res.json()
      // eslint-disable-next-line no-empty
      } catch {} finally {
        companiesInFlight = null
      }
    })()
    return companiesInFlight
  }

  return {
    token, user, organizationHandle, orgName, companyName, role, companyId, planCode,
    companies,
    isAuthenticated, isCompanyAdmin, isWorker, canCreateOrders,orgId,
    setToken, setUser, setOrganization, setOrgName, setCompanyName,
    setRole, setCompanyId, setPlanCode, applyLoginData,
    logout, loadCompanies,
  }
})
