import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { WORKER_ROLES } from '@/constants/roles'

// Rutas con import() dinámico para generar un chunk por vista
// y evitar que Leaflet/Chart.js entren en el bundle inicial.
const LoginView = () => import('@/views/auth/LoginView.vue')
const OrgSelectView = () => import('@/views/auth/OrgSelectView.vue')
const RegisterView = () => import('@/views/auth/RegisterView.vue')
const CompanyRegisterView = () => import('@/views/auth/CompanyRegisterView.vue')
const SettingsView = () => import('@/views/profile/SettingsView.vue')
const ProfileView = () => import('@/views/profile/ProfileView.vue')
const UnitsView = () => import('@/views/units/UnitsView.vue')
const UnitFormView = () => import('@/views/units/UnitFormView.vue')
const UnitDetailView = () => import('@/views/units/UnitDetailView.vue')
const VehiclesView = () => import('@/views/vehicles/VehiclesView.vue')
const VehicleFormView = () => import('@/views/vehicles/VehicleFormView.vue')
const VehicleDetailView = () => import('@/views/vehicles/VehicleDetailView.vue')
const OrderFormView = () => import('@/views/orders/OrderFormView.vue')
const OrdersView = () => import('@/views/orders/OrdersView.vue')
const OrderDetailView = () => import('@/views/orders/OrderDetailView.vue')
const LoyalUsersView = () => import('@/views/loyal-users/LoyalUsersView.vue')
const LoyalUserDetailView = () => import('@/views/loyal-users/LoyalUserDetailView.vue')
const MyOrdersView = () => import('@/views/orders/MyOrdersView.vue')
const MyOrderDetailView = () => import('@/views/orders/MyOrderDetailView.vue')
const TrackingView = () => import('@/views/public/TrackingView.vue')
const NotFoundView = () => import('@/views/NotFoundView.vue')
const ActivityView = () => import('@/views/ActivityView.vue')
const WorkersView = () => import('@/views/workers/WorkersView.vue')
const InviteWorkerView = () => import('@/views/workers/InviteWorkerView.vue')
const HomeView = () => import('@/views/home/HomeView.vue')
const AdminDashboardView = () => import('@/views/admin/AdminDashboardView.vue')
const UnitAssignWorkersView = () => import('@/views/units/UnitAssignWorkersView.vue')
const UnitsMapView = () => import('@/views/units/UnitsMapView.vue')
const OnboardingView = () => import('@/views/auth/OnboardingView.vue')

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', component: LoginView, meta: { guest: true } },
    { path: '/login/org-select', component: OrgSelectView, meta: { guest: true } },
    { path: '/register', component: RegisterView, meta: { guest: true } },
    { path: '/register/company', component: CompanyRegisterView, meta: { guest: true } },
    { path: '/onboarding', component: OnboardingView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN'] } },
    { path: '/track', component: TrackingView },
    { path: '/track/:token', component: TrackingView },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: 'home', component: HomeView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST', 'OPERATOR'] } },
        { path: 'profile', component: ProfileView, meta: { requiresAuth: true } },
        { path: 'my-orders', component: MyOrdersView, meta: { requiresAuth: true } },
        { path: 'my-orders/detail', component: MyOrderDetailView, meta: { requiresAuth: true } },
        { path: 'units', component: UnitsView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST', 'OPERATOR'] } },
        { path: 'units/map', component: UnitsMapView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST', 'OPERATOR'] } },
        { path: 'units/new', component: UnitFormView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN'] } },
        { path: 'units/:id', component: UnitDetailView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST', 'OPERATOR'] } },
        { path: 'units/:id/edit', component: UnitFormView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN'] } },
        { path: 'units/:id/assign-workers', component: UnitAssignWorkersView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN'] } },
        { path: 'vehicles', component: VehiclesView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST', 'OPERATOR'] } },
        { path: 'vehicles/new', component: VehicleFormView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN'] } },
        { path: 'vehicles/:id', component: VehicleDetailView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST', 'OPERATOR'] } },
        { path: 'vehicles/:id/edit', component: VehicleFormView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN'] } },
        { path: 'orders', component: OrdersView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST', 'OPERATOR'] } },
        { path: 'orders/new', component: OrderFormView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST'] } },
        { path: 'orders/:id', component: OrderDetailView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST', 'OPERATOR'] } },
        { path: 'loyal-users', component: LoyalUsersView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST'] } },
        { path: 'loyal-users/:id', component: LoyalUserDetailView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST'] } },
        { path: 'activity', component: ActivityView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST'] } },
        { path: 'workers', component: WorkersView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN', 'ANALYST', 'OPERATOR'] } },
        { path: 'workers/invite', component: InviteWorkerView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN'] } },
        { path: 'settings', component: SettingsView, meta: { requiresAuth: true, roles: ['COMPANY_ADMIN'] } },
        { path: 'admin', component: AdminDashboardView, meta: { requiresAuth: true, roles: ['GLOBAL_ADMIN'] } },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFoundView },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return '/'
  }

  if (to.meta.guest && auth.isAuthenticated && to.path !== '/login/org-select') {
    if (auth.role === 'GLOBAL_ADMIN') return '/admin'
    return WORKER_ROLES.includes(auth.role) ? '/home' : '/my-orders'
  }

  if (to.meta.roles) {
    if (!auth.isAuthenticated) return '/'
    if (!to.meta.roles.includes(auth.role)) return '/profile'
  }
})

export default router
