import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'



describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('applyLoginData distributes all fields to refs and localStorage', () => {
    const auth = useAuthStore()
    auth.applyLoginData({
      token: 'tok-123',
      role: 'COMPANY_ADMIN',
      companyId: 'comp-1',
      companyName: 'Acme',
      orgHandle: 'acme-org',
      orgName: 'Acme Org',
    })
    expect(auth.token).toBe('tok-123')
    expect(auth.role).toBe('COMPANY_ADMIN')
    expect(auth.companyId).toBe('comp-1')
    expect(auth.companyName).toBe('Acme')
    expect(auth.organizationHandle).toBe('acme-org')
    expect(auth.orgName).toBe('Acme Org')
    expect(localStorage.getItem('token')).toBe('tok-123')
  })

  it('logout clears all refs and removes every localStorage key', () => {
    const auth = useAuthStore()
    auth.applyLoginData({ token: 'tok', role: 'ANALYST', companyId: 'c1', companyName: 'Co', orgHandle: 'org', orgName: 'Org' })
    auth.logout()
    expect(auth.token).toBe('')
    expect(auth.role).toBeNull()
    expect(auth.companyId).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('role')).toBeNull()
  })

  it('isAuthenticated is true when token is not empty, false when empty', () => {
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(false)
    auth.setToken('some-token')
    expect(auth.isAuthenticated).toBe(true)
  })

  it('isWorker is true for worker roles and false for null', () => {
    const auth = useAuthStore()
    for (const r of ['COMPANY_ADMIN', 'ANALYST', 'OPERATOR']) {
      auth.setRole(r)
      expect(auth.isWorker).toBe(true)
    }
    auth.setRole(null)
    expect(auth.isWorker).toBe(false)
  })

  it('canCreateOrders is true for COMPANY_ADMIN and ANALYST, false for OPERATOR', () => {
    const auth = useAuthStore()
    auth.setRole('COMPANY_ADMIN')
    expect(auth.canCreateOrders).toBe(true)
    auth.setRole('ANALYST')
    expect(auth.canCreateOrders).toBe(true)
    auth.setRole('OPERATOR')
    expect(auth.canCreateOrders).toBe(false)
  })

  it('setUser stores the profile in user ref', () => {
    const auth = useAuthStore()
    const profile = { email: 'a@b.com', firstName: 'Ana' }
    auth.setUser(profile)
    expect(auth.user).toEqual(profile)
  })

  it('null-path setters remove keys from localStorage', () => {
    const auth = useAuthStore()
    auth.setOrganization('org-handle')
    auth.setOrgName('Org Name')
    auth.setCompanyName('Co Name')
    auth.setPlanCode('BASIC')
    auth.setOrganization(null)
    auth.setOrgName(null)
    auth.setCompanyName(null)
    auth.setPlanCode(null)
    expect(localStorage.getItem('organizationHandle')).toBeNull()
    expect(localStorage.getItem('orgName')).toBeNull()
    expect(localStorage.getItem('companyName')).toBeNull()
    expect(localStorage.getItem('planCode')).toBeNull()
  })

  it('loadCompanies fetches and stores companies when role is COMPANY_ADMIN', async () => {
    const auth = useAuthStore()
    auth.setToken('tok')
    auth.setRole('COMPANY_ADMIN')
    const mockCompanies = [{ id: 'c1', name: 'Acme' }]
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockCompanies) }))
    await auth.loadCompanies()
    expect(auth.companies).toEqual(mockCompanies)
    vi.unstubAllGlobals()
  })

  it('loadCompanies does nothing when not COMPANY_ADMIN', async () => {
    const auth = useAuthStore()
    auth.setToken('tok')
    auth.setRole('ANALYST')
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
    await auth.loadCompanies()
    expect(fetchSpy).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})
