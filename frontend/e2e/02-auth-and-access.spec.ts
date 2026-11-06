import { expect, test } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import {
  API_BASE_URL,
  NO_ACCESS_HEADING,
  apiToken,
  bearer,
  expectHiddenPageTitle,
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

test.describe('page titles', () => {
  // A section root starts with its tabs or content, so its one h1 only speaks to assistive technology.
  const SECTION_ROOTS = [
    {
      role: 'student',
      account: E2E_ACCOUNTS.student,
      pages: [
        ['/student/home', 'Welcome back, Amira'],
        ['/student/learn', 'Learn'],
        ['/student/practice/flashcards', 'Practice'],
        ['/student/ai/talk', 'AI Helper'],
        ['/student/classes', 'My classes'],
        ['/student/classes/find', 'Find classes'],
        ['/student/classes/live', 'Live sessions'],
        ['/student/progress', 'Progress'],
      ],
    },
    {
      role: 'teacher',
      account: E2E_ACCOUNTS.teacher,
      pages: [
        ['/teacher/dashboard', 'Dashboard'],
        ['/teacher/classes', 'Classes'],
        ['/teacher/live-sessions', 'Live sessions'],
        ['/teacher/students', 'Students'],
        ['/teacher/profile', 'Teaching profile'],
      ],
    },
    {
      role: 'admin',
      account: E2E_ACCOUNTS.admin,
      pages: [
        ['/admin/dashboard', 'Dashboard'],
        ['/admin/users', 'Users'],
        ['/admin/content', 'Review queue'],
        ['/admin/analytics', 'Analytics'],
        ['/admin/audit-log', 'Audit log'],
      ],
    },
  ] as const

  for (const { role, account, pages } of SECTION_ROOTS) {
    test(`the ${role}’s section roots keep one h1 for assistive technology`, async ({ page }) => {
      await signIn(page, account)
      for (const [path, title] of pages) {
        await page.goto(path)
        await expectHiddenPageTitle(page, title)
      }
    })
  }

  test('a nested page shows its h1 under the breadcrumb', async ({ page }) => {
    await signIn(page, E2E_ACCOUNTS.student)
    await page.goto('/student/progress/plan')

    const title = page.getByRole('heading', { level: 1 })
    await expect(title).toHaveText('My learning plan')
    await expect(page.getByRole('navigation', { name: 'Breadcrumb' })).toBeVisible()
    await expect(title).not.toHaveClass(/sr-only/)
    expect((await title.boundingBox())!.width).toBeGreaterThan(1)
  })
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
