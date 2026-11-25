import {
  test as base,
  expect,
  type APIRequestContext,
  type APIResponse,
  type Locator,
  type Page,
} from '@playwright/test'

import { E2E_BACKEND_PORT, NEW_STUDENT_PASSWORD, type E2eAccount } from './accounts'

export { expect }

/**
 * Expected Chromium console network noise for intentional negative-path requests (e.g. unauthenticated refresh check,
 * wrong-password 401, locked-lesson 409). Any other console error or uncaught pageerror fails the test.
 */
const EXPECTED_CONSOLE_ERROR = /^Failed to load resource: the server responded with a status of (?:401|409)\b/

/** Extended Playwright test fixture that guards every page in the context against uncaught pageerrors and console errors. */
export const test = base.extend({
  context: async ({ context }, use) => {
    const failures: string[] = []
    const attach = (page: Page): void => {
      page.on('pageerror', (error) => {
        failures.push(`pageerror: ${error.stack ?? error.message}`)
      })
      page.on('console', (message) => {
        if (message.type() !== 'error') return
        const text = message.text()
        if (EXPECTED_CONSOLE_ERROR.test(text)) return
        failures.push(`console.error: ${text}`)
      })
    }
    for (const existing of context.pages()) attach(existing)
    context.on('page', attach)
    await use(context)
    expect(failures, 'Unexpected browser runtime or console errors').toEqual([])
  },
})

/** The backend API, called directly (not through the Vite proxy) by the authorisation checks. */
export const API_BASE_URL = `http://127.0.0.1:${E2E_BACKEND_PORT}/api/v1`

/** Fills in and submits the sign-in form of the page that is open (/auth, possibly with a redirect). */
export async function submitSignIn(page: Page, account: { email: string; password: string }): Promise<void> {
  // The labels end in a required mark ("Password*"); /^Password/ also keeps clear of the "Show password" button.
  await page.getByLabel(/^Email address/).fill(account.email)
  await page.getByLabel(/^Password/).fill(account.password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
}

/** Signs in through the sign-in page and waits until the app leaves it (landing on `redirect` when provided). */
export async function signIn(page: Page, account: E2eAccount, redirect?: string): Promise<void> {
  await page.goto(redirect ? `/auth?redirect=${encodeURIComponent(redirect)}` : '/auth')
  await submitSignIn(page, account)
  await expect(page).not.toHaveURL(/\/auth/)
  if (redirect) {
    const expectedPath = redirect.split(/[?#]/, 1)[0]
    await expect(page).toHaveURL((url) => url.pathname === expectedPath)
  }
}

/** Signs out from the Account page (opened from the user chip in the header) and checks the visitor landing page. */
export async function signOut(page: Page, account: E2eAccount): Promise<void> {
  await page
    .getByRole('banner')
    .getByRole('link', { name: `Account: ${account.name}`, exact: true })
    .click()
  await expect(page).toHaveURL(/\/(student|admin)\/account$/)
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('banner').getByRole('link', { name: 'Sign in' })).toBeVisible()
}

/** The signed-in shell's sidebar navigation. */
export function sidebar(page: Page): Locator {
  return page.getByRole('navigation', { name: 'Main' })
}

/** Checks the sidebar links, in order; a link may carry a count badge ("Practice 6"). */
export async function expectSidebarItems(page: Page, labels: readonly string[]): Promise<void> {
  const pattern = (label: string) => new RegExp(`^${label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*\\d*$`)
  await expect(sidebar(page).getByRole('link')).toHaveText(labels.map(pattern))
}

/** A section root has exactly one h1, named after the page but only there for assistive technology (sr-only). */
export async function expectHiddenPageTitle(page: Page, title: string): Promise<void> {
  const heading = page.getByRole('heading', { level: 1 })
  await expect(heading).toHaveText(title)
  await expect(heading).toHaveCount(1)
  await expect(heading).toHaveClass(/sr-only/)
  const box = await heading.boundingBox()
  expect(box, `the h1 "${title}" is laid out`).not.toBeNull()
  expect(box!.width, `the h1 "${title}" takes no room`).toBeLessThanOrEqual(1)
}

/** Focus mode (lesson player, review, setup, class activity, quiz): the task fills the screen, without the sidebar. */
export async function expectFocusMode(page: Page): Promise<void> {
  await expect(sidebar(page)).toHaveCount(0)
}

/**
 * The focus-mode or confirm "Leave …?" step (ConfirmDialog): an alert dialog with `stay` (default "Keep going") on the
 * left, which has the focus so Enter never leaves by accident, and `leave` on the right.
 */
export async function expectLeaveStep(page: Page, title: string, leave: string, stay = 'Keep going'): Promise<Locator> {
  const dialog = page.getByRole('alertdialog', { name: title })
  await expect(dialog).toBeVisible()
  // The header's × backs out too; the footer holds the two choices.
  await expect(dialog.locator('.overlay-footer').getByRole('button')).toHaveText([stay, leave])
  await expect(dialog.getByRole('button', { name: stay })).toBeFocused()
  return dialog
}

/** Registers the named learner through the form and returns their new email address. */
export async function registerStudent(page: Page, name: string, label: string): Promise<string> {
  const email = `e2e.${label}.${Date.now()}@example.test`
  await page.goto('/auth?tab=register')
  await expect(page.getByRole('tab', { name: 'Create account' })).toHaveAttribute('aria-selected', 'true')
  await page.getByLabel(/^Full name/).fill(name)
  await page.getByLabel(/^Email address/).fill(email)
  await page.getByLabel(/^Password/).fill(NEW_STUDENT_PASSWORD)
  await page.getByLabel(/^Confirm password/).fill(NEW_STUDENT_PASSWORD)
  await page.getByRole('checkbox', { name: /^I am 18 or older and agree to the Terms of Service/ }).check()
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
  return email
}

/** Switches the interface language with the EN / BM / 中文 switch of the header. */
export async function switchLanguage(page: Page, label: 'EN' | 'BM' | '中文'): Promise<void> {
  // The visible label stays the same in every language; the rest of the accessible name is translated.
  await page.getByRole('button', { name: new RegExp(`^${label}, `) }).click()
}

/** Signs in through the API and returns the Authorization header (`data.token` of POST /auth/login) for direct calls. */
export async function apiAuthHeaders(request: APIRequestContext, account: E2eAccount): Promise<Record<string, string>> {
  const response = await request.post(`${API_BASE_URL}/auth/login`, {
    data: { email: account.email, password: account.password },
  })
  expect(response.status()).toBe(200)
  const body = (await response.json()) as { data: { token: string } }
  return { Authorization: `Bearer ${body.data.token}` }
}

/** Runs an authenticated API teardown call on a best-effort basis without masking a test's primary failure. */
export async function bestEffortApi(
  request: APIRequestContext,
  account: E2eAccount,
  run: (headers: Record<string, string>) => Promise<APIResponse>,
): Promise<void> {
  try {
    const response = await run(await apiAuthHeaders(request, account))
    if (!response.ok()) {
      console.warn(`bestEffortApi teardown returned non-OK status: ${response.status()}`)
    }
  } catch (error) {
    // Best-effort teardown: log visibly without masking the primary test failure.
    console.warn('bestEffortApi teardown failed:', error)
  }
}
