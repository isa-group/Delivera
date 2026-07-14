import { test, expect } from '@playwright/test'

const activityTypes = [
  { code: 'TRANSPORT', labelEs: 'Transporte', labelEn: 'Transport' },
  { code: 'RETAIL', labelEs: 'Retail', labelEn: 'Retail' },
]

const registerResponse = {
  token: 'mock-jwt-token',
  role: 'COMPANY_ADMIN',
  companyId: 1,
  companyName: 'Acme Logística',
  orgHandle: 'grupo-acme',
  orgName: 'Grupo Acme',
}

// ── Individual registration ───────────────────────────────────────────────────

test('register shows account type selector', { tag: '@register' }, async ({ page }) => {
  await page.goto('/register')
  await expect(page.getByText('Acceso personal')).toBeVisible()
  await expect(page.getByText('Registrar empresa')).toBeVisible()
})

test('register individual shows personal form', { tag: '@register' }, async ({ page }) => {
  await page.goto('/register')
  await page.getByText('Acceso personal').click()
  await expect(page.locator('#register-email')).toBeVisible()
  await expect(page.locator('#register-username')).toBeVisible()
  await expect(page.locator('#register-password')).toBeVisible()
})

test('register individual empty submit stays on form', { tag: '@register' }, async ({ page }) => {
  await page.goto('/register')
  await page.getByText('Acceso personal').click()
  await page.getByRole('button', { name: 'Registrarse' }).click()
  // Validation rejects empty form — no navigation
  await expect(page.locator('#register-email')).toBeVisible()
})

test('register individual successful redirects to /profile', { tag: '@register' }, async ({ page }) => {
  await page.route('**/api/v2/**', async route => {
    const url = route.request().url()
    if (url.includes('/auth/check-username')) {
      await route.fulfill({ json: { available: true } })
    } else if (url.includes('/auth/register') && !url.includes('/company')) {
      await route.fulfill({ json: { token: 'tok', role: null } })
    } else {
      await route.fulfill({ json: [] })
    }
  })
  await page.goto('/register')
  await page.getByText('Acceso personal').click()
  await page.locator('#register-email').fill('user@test.com')
  await page.locator('#register-username').fill('juantest')
  await page.locator('#register-first-name').fill('Juan')
  await page.locator('#register-last-name').fill('García')
  await page.locator('#register-address').fill('Calle Test 1')
  await page.locator('#register-password input').fill('Password1')
  await page.getByRole('button', { name: 'Registrarse' }).click()
  await expect(page).toHaveURL('/profile')
})

// ── Company registration ──────────────────────────────────────────────────────

test('register company shows step 1 org form', { tag: '@register' }, async ({ page }) => {
  await page.route('**/api/v2/**', route => route.fulfill({ json: [] }))
  await page.goto('/register/company')
  await expect(page.locator('#org-name')).toBeVisible()
  await expect(page.locator('#org-handle')).toBeVisible()
  await expect(page.getByText('Siguiente')).toBeVisible()
})

test('register company step 1 empty submit stays on step 1', { tag: '@register' }, async ({ page }) => {
  await page.route('**/api/v2/**', route => route.fulfill({ json: [] }))
  await page.goto('/register/company')
  await page.getByRole('button', { name: 'Siguiente' }).click()
  // Validation rejects — step stays at 1
  await expect(page.locator('#org-name')).toBeVisible()
})

test('register company step 1 advances to step 2', { tag: '@register' }, async ({ page }) => {
  await page.route('**/api/v2/**', route => route.fulfill({ json: [] }))
  await page.route('**/api/v2/activity-types', route =>
    route.fulfill({ json: activityTypes })
  )
  await page.route('**/api/v2/organizations/check-handle**', route =>
    route.fulfill({ json: { available: true } })
  )
  await page.goto('/register/company')
  await page.locator('#org-name').fill('Grupo Acme')
  await page.locator('#org-handle').fill('grupo-acme')
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await expect(page.locator('#company-name')).toBeVisible()
  await expect(page.locator('.activity-option').first()).toBeVisible()
})

test('register company step 2 advances to step 3', { tag: '@register' }, async ({ page }) => {
  await page.route('**/api/v2/**', route => route.fulfill({ json: [] }))
  await page.route('**/api/v2/activity-types', route =>
    route.fulfill({ json: activityTypes })
  )
  await page.route('**/api/v2/organizations/check-handle**', route =>
    route.fulfill({ json: { available: true } })
  )
  await page.goto('/register/company')
  // Step 1
  await page.locator('#org-name').fill('Grupo Acme')
  await page.locator('#org-handle').fill('grupo-acme')
  await page.getByRole('button', { name: 'Siguiente' }).click()
  // Step 2
  await expect(page.locator('.activity-option').first()).toBeVisible()
  await page.locator('#company-name').fill('Acme Logística')
  await page.locator('.activity-option').first().click()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  await expect(page.locator('#company-email')).toBeVisible()
})

test('register company full flow submits and redirects', { tag: '@register' }, async ({ page }) => {
  await page.route('**/api/v2/**', route => route.fulfill({ json: [] }))
  await page.route('**/api/v2/auth/register/company', route =>
    route.fulfill({ json: registerResponse })
  )
  await page.route('**/api/v2/activity-types', route =>
    route.fulfill({ json: activityTypes })
  )
  await page.route('**/api/v2/auth/check-username**', route =>
    route.fulfill({ json: { available: true } })
  )
  await page.route('**/api/v2/organizations/check-handle**', route =>
    route.fulfill({ json: { available: true } })
  )
  await page.goto('/register/company')
  // Step 1
  await page.locator('#org-name').fill('Grupo Acme')
  await page.locator('#org-handle').fill('grupo-acme')
  await page.getByRole('button', { name: 'Siguiente' }).click()
  // Step 2
  await expect(page.locator('.activity-option').first()).toBeVisible()
  await page.locator('#company-name').fill('Acme Logística')
  await page.locator('.activity-option').first().click()
  await page.getByRole('button', { name: 'Siguiente' }).click()
  // Step 3
  await page.locator('#company-email').fill('admin@empresa.com')
  await page.locator('#company-username').fill('adminacme')
  await page.locator('#company-first-name').fill('Admin')
  await page.locator('#company-last-name').fill('User')
  await page.locator('#company-password input').fill('Password1')
  await page.getByRole('button', { name: 'Crear empresa' }).click()
  await expect(page).toHaveURL('/onboarding')
})
