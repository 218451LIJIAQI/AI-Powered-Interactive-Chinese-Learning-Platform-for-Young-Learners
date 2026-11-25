import { E2E_ACCOUNTS } from './accounts'
import {
  API_BASE_URL,
  apiAuthHeaders,
  expect,
  expectHiddenPageTitle,
  expectSidebarItems,
  registerStudent,
  signIn,
  signOut,
  submitSignIn,
  switchLanguage,
  test,
} from './support'

// Home page and sidebar of each role (frontend/src/config/navigation.ts). There are two roles: learners and admins.
const ROLES = [
  {
    role: 'learner',
    account: E2E_ACCOUNTS.student,
    home: '/student/home',
    sidebar: ['Home', 'Learn', 'Practice', 'AI Helper', 'Classes', 'Progress'],
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
    await expect(page.getByRole('heading', { level: 1 })).toBeAttached()
    const accountLink = page.getByRole('banner').getByRole('link', { name: `Account: ${account.name}`, exact: true })
    await expect(accountLink).toBeVisible()
    await expect(accountLink).toContainText(account.name)
    await expectSidebarItems(page, sidebar)

    await signOut(page, account)
  })
}

test('saving a name in an open tab keeps the language chosen in another tab', async ({ page, context }) => {
  await registerStudent(page, 'Siti Hassan', 'profile')
  await expect(page).toHaveURL('/student/setup')
  await page.goto('/student/account')
  const name = page.locator('#account-name')
  await expect(name).toHaveValue('Siti Hassan')

  const otherTab = await context.newPage()
  await otherTab.goto('/student/account')
  await expect(otherTab.locator('#account-name')).toHaveValue('Siti Hassan')
  const profilePath = '/api/v1/users/me/profile'
  const languageSaved = otherTab.waitForResponse(
    (response) => response.url().endsWith(profilePath) && response.request().method() === 'PUT',
  )
  await switchLanguage(otherTab, 'BM')
  expect((await languageSaved).ok()).toBe(true)

  await name.fill('Siti Aminah')
  const nameSaved = page.waitForResponse(
    (response) => response.url().endsWith(profilePath) && response.request().method() === 'PUT',
  )
  await page.getByTestId('account-save').click()
  const response = await nameSaved
  expect(response.ok()).toBe(true)
  expect((await response.json()).data).toMatchObject({ fullName: 'Siti Aminah', preferredLanguage: 'ms' })
  const updatedAccountLink = page.getByRole('banner').getByRole('link', { name: 'Account: Siti Aminah', exact: true })
  await expect(updatedAccountLink).toBeVisible()
  await expect(updatedAccountLink).toContainText('Siti Aminah')
  await page.reload()
  await expect(page.locator('#account-name')).toHaveValue('Siti Aminah')
  await expect(page.locator('html')).toHaveAttribute('lang', 'ms')
})

test('a wrong password shows an error on the sign-in page', async ({ page }) => {
  await page.goto('/auth')
  await submitSignIn(page, { email: 'chen.hao@fyp.local', password: 'Not-The-Right-Password-1' })

  await expect(page.getByRole('alert')).toContainText('The email or password is incorrect.')
  await expect(page).toHaveURL(/\/auth$/)
})

test('a visitor opening a learner page signs in first and is sent back to it', async ({ page }) => {
  await page.goto('/student/home')
  await expect(page).toHaveURL(/\/auth\?redirect=\/student\/home$/)

  await submitSignIn(page, E2E_ACCOUNTS.student)
  await expect(page).toHaveURL(/\/student\/home$/)
})

for (const [role, account, path] of [
  ['admin', E2E_ACCOUNTS.admin, '/student/home'],
  ['learner', E2E_ACCOUNTS.student, '/admin/dashboard'],
] as const) {
  test(`a ${role} gets the No access page for the other role's pages`, async ({ page }) => {
    await signIn(page, account)

    await page.goto(path)
    // The refused page rides along, so "Use another account" can sign in to it.
    await expect(page).toHaveURL(`/unauthorized?redirect=${path}`)
    await expect(page.getByRole('heading', { name: 'You don’t have access to this page' })).toBeVisible()
    await expect(page.getByRole('main').getByRole('link', { name: 'Go to my home' })).toBeVisible()
  })
}

test('the teacher, live-session and learner support pages are gone', async ({ page }) => {
  await signIn(page, E2E_ACCOUNTS.student)
  for (const path of ['/teacher/dashboard', '/student/classes/live', '/student/support']) {
    await page.goto(path)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('This page could not be found')
  }
})

test.describe('page titles', () => {
  // A section root starts with its tabs or content, so its one h1 only speaks to assistive technology.
  const SECTION_ROOTS = [
    {
      role: 'learner',
      account: E2E_ACCOUNTS.student,
      pages: [
        ['/student/home', 'Welcome back, Amira'],
        ['/student/learn', 'Learn'],
        ['/student/practice/flashcards', 'Practice'],
        ['/student/ai/talk', 'AI Helper'],
        ['/student/classes', 'My classes'],
        ['/student/classes/find', 'Find classes'],
        ['/student/progress', 'Progress'],
      ],
    },
    {
      role: 'admin',
      account: E2E_ACCOUNTS.admin,
      pages: [
        ['/admin/dashboard', 'Dashboard'],
        ['/admin/users', 'Users'],
        ['/admin/content', 'Classes'],
        ['/admin/ai', 'AI reports'],
        ['/admin/support', 'Support'],
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
    const box = await title.boundingBox()
    expect(box).not.toBeNull()
    expect(box!.width).toBeGreaterThan(1)
  })
})

test.describe('backend authorisation', () => {
  const ADMIN_API = `${API_BASE_URL}/admin/users`
  const AUTHORING_API = `${API_BASE_URL}/admin/classes`
  const LEARNER_API = `${API_BASE_URL}/students/classes`
  const AI_STATUS_API = `${API_BASE_URL}/ai/status`

  test('a learner token is refused by the admin and class-authoring APIs', async ({ request }) => {
    const headers = await apiAuthHeaders(request, E2E_ACCOUNTS.student)

    expect((await request.get(ADMIN_API, { headers })).status()).toBe(403)
    expect((await request.get(AUTHORING_API, { headers })).status()).toBe(403)
    expect((await request.get(LEARNER_API, { headers })).status()).toBe(200)
  })

  test('an admin token is accepted by the admin and class-authoring APIs and refused by learner APIs', async ({
    request,
  }) => {
    const headers = await apiAuthHeaders(request, E2E_ACCOUNTS.admin)

    expect((await request.get(ADMIN_API, { headers })).status()).toBe(200)
    expect((await request.get(AUTHORING_API, { headers })).status()).toBe(200)
    expect((await request.get(LEARNER_API, { headers })).status()).toBe(403)
    expect((await request.get(AI_STATUS_API, { headers })).status()).toBe(403)
  })

  test('the teacher API and teacher sign-up no longer exist', async ({ request }) => {
    const headers = await apiAuthHeaders(request, E2E_ACCOUNTS.admin)
    expect((await request.get(`${API_BASE_URL}/teachers/classes`, { headers })).status()).toBe(404)
    expect((await request.post(`${API_BASE_URL}/auth/register/teacher`, { headers, data: {} })).status()).toBe(404)
    // Unauthenticated callers are rejected before routing (not whitelisted as a public registration endpoint).
    expect((await request.post(`${API_BASE_URL}/auth/register/teacher`, { data: {} })).status()).toBe(401)
  })

  test('a request without a token is unauthorised', async ({ request }) => {
    expect((await request.get(ADMIN_API)).status()).toBe(401)
    expect((await request.get(AUTHORING_API)).status()).toBe(401)
    expect((await request.get(LEARNER_API)).status()).toBe(401)
    expect((await request.get(AI_STATUS_API)).status()).toBe(401)
  })
})
