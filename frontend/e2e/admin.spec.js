import { test, expect } from '@playwright/test'
import { setupAuth, mockApiDefaults } from './helpers/auth.js'

test.beforeEach(async ({ page }) => {
  await mockApiDefaults(page)
})

// ── Access control ───────────────────────────────────────────────────────────

test('GLOBAL_ADMIN accessing / redirects to /admin', { tag: '@admin' }, async ({ page }) => {
  await setupAuth(page, { role: 'GLOBAL_ADMIN' })
  await page.goto('/')
  await expect(page).toHaveURL('/admin')
})

test('COMPANY_ADMIN cannot access /admin', { tag: '@admin' }, async ({ page }) => {
  await setupAuth(page, { role: 'COMPANY_ADMIN' })
  await page.goto('/admin')
  await expect(page).toHaveURL('/profile')
})

test('OPERATOR cannot access /admin', { tag: '@admin' }, async ({ page }) => {
  await setupAuth(page, { role: 'OPERATOR' })
  await page.goto('/admin')
  await expect(page).toHaveURL('/profile')
})

test('unauthenticated /admin redirects to /', { tag: '@admin' }, async ({ page }) => {
  await page.goto('/admin')
  await expect(page).toHaveURL('/')
})

// ── Dashboard rendering ──────────────────────────────────────────────────────

test('admin dashboard shows metrics and organizations', { tag: '@admin' }, async ({ page }) => {
  await setupAuth(page, { role: 'GLOBAL_ADMIN' })

  await page.route('**/api/v2/admin/metrics', route =>
    route.fulfill({ json: { totalOrganizations: 3, totalCompanies: 7, totalOrdersThisMonth: 42, totalActiveUsers: 15 } })
  )
  await page.route('**/api/v2/admin/organizations', route =>
    route.fulfill({
      json: [
        { id: '1', name: 'Org Alpha', handle: 'org-alpha', createdAt: '2026-01-15T10:00:00Z', companyCount: 2, workerCount: 5, orderCount: 20 },
        { id: '2', name: 'Org Beta', handle: 'org-beta', createdAt: '2026-03-01T08:00:00Z', companyCount: 1, workerCount: 3, orderCount: 10 },
      ],
    })
  )
  await page.route('**/api/v2/admin/units', route => route.fulfill({ json: [] }))
  await page.route('**/api/v2/admin/routes', route => route.fulfill({ json: [] }))
  await page.route('**/api/v2/admin/activity/orders-by-day**', route => route.fulfill({ json: [] }))

  await page.goto('/admin')

  // Metrics chips
  const statsGrid = page.locator('.stats-grid')
  await expect(statsGrid.getByText('3')).toBeVisible()
  await expect(statsGrid.getByText('7')).toBeVisible()
  await expect(statsGrid.getByText('42')).toBeVisible()
  await expect(statsGrid.getByText('15')).toBeVisible()

  // Organizations table (in the Administration tab)
  await page.getByRole('button', { name: 'Administración' }).click()
  await expect(page.getByText('Org Alpha')).toBeVisible()
  await expect(page.getByText('Org Beta')).toBeVisible()
  await expect(page.getByText('org-alpha')).toBeVisible()
})

test('admin dashboard shows empty state', { tag: '@admin' }, async ({ page }) => {
  await setupAuth(page, { role: 'GLOBAL_ADMIN' })

  await page.route('**/api/v2/admin/metrics', route =>
    route.fulfill({ json: { totalOrganizations: 0, totalCompanies: 0, totalOrdersThisMonth: 0, totalActiveUsers: 0 } })
  )
  await page.route('**/api/v2/admin/organizations', route =>
    route.fulfill({ json: [] })
  )

  await page.goto('/admin')

  await expect(page.getByText('0').first()).toBeVisible()
})

// ── Sidebar ──────────────────────────────────────────────────────────────────

test('sidebar shows admin link for GLOBAL_ADMIN', { tag: '@admin' }, async ({ page }) => {
  await setupAuth(page, { role: 'GLOBAL_ADMIN' })

  await page.route('**/api/v2/admin/metrics', route =>
    route.fulfill({ json: { totalOrganizations: 0, totalCompanies: 0, totalOrdersThisMonth: 0, totalActiveUsers: 0 } })
  )
  await page.route('**/api/v2/admin/organizations', route =>
    route.fulfill({ json: [] })
  )

  await page.goto('/admin')

  const sidebar = page.locator('.sidebar')
  await expect(sidebar.getByText('Administración')).toBeVisible()
})

test('sidebar does not show admin link for COMPANY_ADMIN', { tag: '@admin' }, async ({ page }) => {
  await setupAuth(page)
  await page.goto('/home')

  const sidebar = page.locator('.sidebar')
  await expect(sidebar.getByText('Administración')).not.toBeVisible()
})
