import { expect, test } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import {
  API_BASE_URL,
  NO_ACCESS_HEADING,
  apiToken,
  bearer,
  expectSidebarItems,
  signIn,
  signOut,
  submitSignIn,
} from './support'

// Home page and sidebar of each role (frontend/src/config/navigation.ts).
const ROLES = [
  {
    role: 'student',
    account: E2E_ACCOUNTS.student,
    home: '/student/home',
    sidebar: ['Home', 'Learn', 'Practice', 'AI Helper', 'Classes', 'Progress'],
  },
  {
    role: 'teacher',
    account: E2E_ACCOUNTS.teacher,
    home: '/teacher/dashboard',
    sidebar: ['Dashboard', 'Classes', 'Live sessions', 'Students', 'Teaching profile'],
  },
  {
    role: 'admin',
    account: E2E_ACCOUNTS.admin,
    home: '/admin/dashboard',
    sidebar: ['Dashboard', 'Users', 'Content', 'AI', 'Support', 'Analytics', 'Audit log'],
  },
] as const

for (const { role, account, home, sidebar } of ROLES) {
  test(`the ${role} signs in, lands on their home with their sidebar, and signs out`, async ({ page }) => {
    await signIn(page, account)

    await expect(page).toHaveURL(new RegExp(`${home}$`))
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(page.getByRole('link', { name: `Account: ${account.name}` })).toBeVisible()
    await expectSidebarItems(page, sidebar)

    await signOut(page, account)
  })
}

test('a wrong password shows an error on the sign-in page', async ({ page }) => {
  await page.goto('/auth')
  await submitSignIn(page, { email: E2E_ACCOUNTS.classmate.email, password: 'Not-The-Right-Password-1' })

  await expect(page.getByRole('alert')).toContainText('The email or password is incorrect.')
  await expect(page).toHaveURL(/\/auth$/)
})

test('a visitor opening a student page signs in first and is sent back to it', async ({ page }) => {
  await page.goto('/student/home')
  await expect(page).toHaveURL(/\/auth\?redirect=\/student\/home$/)

  await submitSignIn(page, E2E_ACCOUNTS.student)
  await expect(page).toHaveURL(/\/student\/home$/)
})

test('a teacher gets the No access page for admin and student pages', async ({ page }) => {
  await signIn(page, E2E_ACCOUNTS.teacher)

  for (const path of ['/admin/users', '/student/home']) {
    await page.goto(path)
    await expect(page).toHaveURL(/\/unauthorized$/)
    await expect(page.getByRole('heading', { name: NO_ACCESS_HEADING })).toBeVisible()
  }
})

test('a student gets the No access page for teacher and admin pages', async ({ page }) => {
  await signIn(page, E2E_ACCOUNTS.student)

  for (const path of ['/teacher/dashboard', '/admin/dashboard']) {
    await page.goto(path)
    await expect(page).toHaveURL(/\/unauthorized$/)
    await expect(page.getByRole('heading', { name: NO_ACCESS_HEADING })).toBeVisible()
  }
})

test.describe('backend authorisation', () => {
  const ADMIN_API = `${API_BASE_URL}/admin/users`
  const TEACHER_API = `${API_BASE_URL}/teachers/classes`

  test('a student token is refused by admin and teacher APIs', async ({ request }) => {
    const token = await apiToken(request, E2E_ACCOUNTS.student)

    expect((await request.get(ADMIN_API, { headers: bearer(token) })).status()).toBe(403)
    expect((await request.get(TEACHER_API, { headers: bearer(token) })).status()).toBe(403)
  })

  test('a teacher token is refused by an admin API but accepted by its own', async ({ request }) => {
    const token = await apiToken(request, E2E_ACCOUNTS.teacher)

    expect((await request.get(ADMIN_API, { headers: bearer(token) })).status()).toBe(403)
    expect((await request.get(TEACHER_API, { headers: bearer(token) })).status()).toBe(200)
  })

  test('an admin token is accepted by the admin API', async ({ request }) => {
    const token = await apiToken(request, E2E_ACCOUNTS.admin)

    expect((await request.get(ADMIN_API, { headers: bearer(token) })).status()).toBe(200)
  })

  test('a request without a token is unauthorised', async ({ request }) => {
    expect((await request.get(ADMIN_API)).status()).toBe(401)
    expect((await request.get(TEACHER_API)).status()).toBe(401)
  })
})
