<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useApi } from '@/composables/useApi'
import { useServices } from '@/composables/useServices'
import { useAppConfig } from '@/composables/useAppConfig'
import { WORKER_ROLES } from '@/constants/roles'
import { stopAuthRefresh } from '@/composables/useRefreshToken'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const api = useApi()
const authApi = useServices("auth-service")
const { load: loadConfig } = useAppConfig()

const collapsed = ref(false)
const profileOpen = ref(false)
const profileRef = ref(null)
const companySwitcherOpen = ref(false)
const companySwitcherRef = ref(null)
const switching = ref(false)

const themeClass = computed(() =>
  WORKER_ROLES.includes(auth.role) ? 'theme-company' : 'theme-personal'
)

const navItems = [
  { path: '/home', icon: 'pi-home', labelKey: 'nav.home', roles: WORKER_ROLES },
  { path: '/activity', icon: 'pi-chart-bar', labelKey: 'nav.activity', roles: ['COMPANY_ADMIN', 'ANALYST'] },
  { path: '/units', icon: 'pi-building', labelKey: 'nav.units', roles: WORKER_ROLES },
  { path: '/vehicles', icon: 'pi-truck', labelKey: 'nav.vehicles', roles: ['COMPANY_ADMIN', 'ANALYST', 'OPERATOR'] },
  { path: '/orders', icon: 'pi-send', labelKey: 'nav.orders', roles: WORKER_ROLES },
  { path: '/loyal-users', icon: 'pi-users', labelKey: 'nav.loyalUsers', roles: ['COMPANY_ADMIN', 'ANALYST'] },
  { path: '/workers', icon: 'pi-id-card', labelKey: 'nav.workers', roles: WORKER_ROLES },
  { path: '/my-orders', icon: 'pi-inbox', labelKey: 'nav.myOrders', noRole: true },
  { path: '/settings', icon: 'pi-cog', labelKey: 'nav.settings', roles: ['COMPANY_ADMIN'] },
  { path: '/admin', icon: 'pi-shield', labelKey: 'nav.admin', roles: ['GLOBAL_ADMIN'] },
]

const visibleItems = computed(() =>
  navItems.filter(item => {
    if (item.noRole) return !auth.role
    return !item.roles || item.roles.includes(auth.role)
  })
)

const displayName = computed(() => {
  if (!auth.user) return '...'
  const { firstName, lastName } = auth.user
  return [firstName, lastName].filter(Boolean).join(' ') || auth.user.email || '...'
})

const initials = computed(() => {
  if (!auth.user) return '?'
  const f = auth.user.firstName?.[0] || ''
  const l = auth.user.lastName?.[0] || ''
  return (f + l).toUpperCase() || auth.user.email?.[0]?.toUpperCase() || '?'
})

const otherCompanies = computed(() =>
  auth.companies.filter(c => c.id !== auth.companyId)
)

const currentCompanyLogo = computed(() =>
  auth.companies.find(c => c.id === auth.companyId)?.logoData || null
)

function applyUserLocale(email) {
  const saved = localStorage.getItem(`locale_${email}`)
  if (saved) locale.value = saved
}

function handleLogout() {
  try {
      authApi.get('/auth/logout')
    } catch(e) {console.log(e)}
  profileOpen.value = false
  locale.value = navigator.language?.startsWith('en') ? 'en' : 'es'
  stopAuthRefresh()
  auth.logout()
  router.push('/')
}

function onDocumentClick(e) {
  if (profileRef.value && !profileRef.value.contains(e.target)) {
    profileOpen.value = false
  }
  if (companySwitcherRef.value && !companySwitcherRef.value.contains(e.target)) {
    companySwitcherOpen.value = false
  }
}

function toggleProfile() {
  companySwitcherOpen.value = false
  profileOpen.value = !profileOpen.value
}

function toggleCompanySwitcher() {
  if (otherCompanies.value.length === 0) return
  profileOpen.value = false
  companySwitcherOpen.value = !companySwitcherOpen.value
}

async function switchCompany(companyId) {
  if (switching.value) return
  companySwitcherOpen.value = false
  switching.value = true
  try {
    const res = await authApi.post('/auth/switch-company', { companyId })
    if (res.ok) {
      const data = await res.json()
      auth.applyLoginData(data)
      router.push('/home')
    }
  } catch {
    // silencioso
  } finally {
    switching.value = false
  }
}

async function loadUserProfile() {
  try {
    const res = await api.get('/user/profile')
    if (res.ok) {
      const profile = await res.json()
      auth.setUser(profile)
      applyUserLocale(profile.email)
    }
  } catch { /* silencioso */ }
}

async function loadSubscriptionIfNeeded() {
  if (auth.planCode) return
  try {
    const subRes = await api.get('/settings/subscription')
    if (subRes.ok) { const sub = await subRes.json(); auth.setPlanCode(sub.planCode) }
  } catch { /* silencioso */ }
}

onMounted(async () => {
  document.addEventListener('click', onDocumentClick)
  loadConfig()

  if (!auth.user && auth.isAuthenticated) {
    await loadUserProfile()
  } else if (auth.user?.email) {
    applyUserLocale(auth.user.email)
  }

  //if (auth.isWorker && auth.isCompanyAdmin) {
  if (auth.isWorker) {
    auth.loadCompanies()
    await loadSubscriptionIfNeeded()
  }
})

onUnmounted(() => document.removeEventListener('click', onDocumentClick))
</script>

<template>
  <div class="app-layout" :class="themeClass">
    <nav class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">

      <!-- Brand / Company switcher -->
      <div ref="companySwitcherRef" class="sidebar-brand-wrapper">
        <Transition name="menu-slide">
          <ul v-if="companySwitcherOpen && otherCompanies.length" class="company-dropdown">
            <li class="company-dropdown-label">{{ t('settings.myCompanies') }}</li>
            <li v-for="c in otherCompanies" :key="c.id">
              <button class="company-dropdown-item" type="button" @click="switchCompany(c.id)">
                <img v-if="c.logoData" :src="c.logoData" class="company-dropdown-logo" alt="" />
                <span v-else class="company-dropdown-avatar">{{ c.name[0].toUpperCase() }}</span>
                <span>{{ c.name }}</span>
              </button>
            </li>
          </ul>
        </Transition>

        <!-- Modo empresa: muestra nombre de empresa -->
        <button
          v-if="auth.isWorker"
          class="sidebar-brand sidebar-brand--company"
          :class="{ 'sidebar-brand--clickable': otherCompanies.length > 0 }"
          type="button"
          @click="toggleCompanySwitcher"
        >
          <img v-if="currentCompanyLogo" :src="currentCompanyLogo" class="sidebar-company-logo" alt="" />
          <span v-else class="sidebar-company-avatar">
            {{ auth.companyName?.[0]?.toUpperCase() ?? '?' }}
          </span>
          <div class="sidebar-brand-info">
            <span class="sidebar-brand-company">{{ auth.companyName ?? '...' }}</span>
            <span class="sidebar-brand-org">{{ auth.orgName ?? '' }}</span>
          </div>
          <i
            v-if="otherCompanies.length > 0"
            :class="['pi', companySwitcherOpen ? 'pi-angle-up' : 'pi-angle-down', 'sidebar-brand-chevron']"
          />
        </button>

        <!-- Modo personal: muestra "Delivera" -->
        <div v-else class="sidebar-brand">
          <i class="pi pi-truck sidebar-logo" />
          <span class="sidebar-brand-name">Delivera</span>
        </div>
      </div>

      <!-- Nav items -->
      <ul class="sidebar-nav">
        <li v-for="item in visibleItems" :key="item.path">
          <RouterLink
            :to="item.path"
            class="sidebar-item"
            :class="{ 'sidebar-item--active': route.path.startsWith(item.path) }"
            v-tooltip.right="collapsed ? t(item.labelKey) : null"
          >
            <i :class="['pi', item.icon, 'sidebar-item-icon']" />
            <span class="sidebar-item-label">{{ t(item.labelKey) }}</span>
          </RouterLink>
        </li>
      </ul>

      <!-- Toggle -->
      <button class="sidebar-toggle" type="button" @click="collapsed = !collapsed">
        <i :class="['pi', collapsed ? 'pi-angle-right' : 'pi-angle-left']" />
      </button>

      <!-- Profile -->
      <div ref="profileRef" class="sidebar-profile-wrapper">
        <Transition name="menu-slide">
          <ul v-if="profileOpen" class="profile-dropdown">
            <li>
              <RouterLink to="/profile" class="profile-dropdown-item" @click="profileOpen = false">
                <i class="pi pi-user" />
                <span>{{ t('nav.profile') }}</span>
              </RouterLink>
            </li>
            <li>
              <button class="profile-dropdown-item profile-dropdown-logout" type="button" @click="handleLogout">
                <i class="pi pi-sign-out" />
                <span>{{ t('auth.logout') }}</span>
              </button>
            </li>
          </ul>
        </Transition>

        <button class="sidebar-profile" type="button" @click="toggleProfile">
          <img v-if="auth.user?.avatarData" :src="auth.user.avatarData" class="sidebar-avatar-img" alt="" />
          <PAvatar v-else :label="initials" shape="circle" class="sidebar-avatar" />
          <div class="sidebar-profile-info">
            <span class="sidebar-profile-name">{{ displayName }}</span>
            <span v-if="auth.planCode" :class="['sidebar-plan-badge', 'plan-badge--' + auth.planCode.toLowerCase()]">{{ auth.planCode }}</span>
          </div>
          <i :class="['pi', profileOpen ? 'pi-angle-up' : 'pi-angle-down', 'sidebar-profile-chevron']" />
        </button>
      </div>
    </nav>

    <main class="app-main">
      <RouterView v-slot="{ Component, route }">
        <Transition name="fade" mode="out-in">
          <component :is="Component" :key="route.path" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>

<style scoped src="./AppLayout.css"></style>
