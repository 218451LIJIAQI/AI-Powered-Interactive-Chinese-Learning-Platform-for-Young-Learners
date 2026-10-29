import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test'

import { E2E_BACKEND_PORT, NEW_STUDENT_PASSWORD, type E2eAccount } from './accounts'

/** The backend API, called directly (not through the Vite proxy) by the authorisation checks. */
export const API_BASE_URL = `http://127.0.0.1:${E2E_BACKEND_PORT}/api/v1`

/** Heading of the No access page (/unauthorized). */
export const NO_ACCESS_HEADING = 'You don’t have access to this page'

/** Fills in and submits the sign-in form of the page that is open (/auth, possibly with a redirect). */
export async function submitSignIn(page: Page, account: { email: string; password: string }): Promise<void> {
  // The labels end in a required mark ("Password*"); /^Password/ also keeps clear of the "Show password" button.
  await page.getByLabel(/^Email address/).fill(account.email)
  await page.getByLabel(/^Password/).fill(account.password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
}

/** Signs in through the sign-in page and waits until the app has left it. */
export async function signIn(page: Page, account: E2eAccount): Promise<void> {
  await page.goto('/auth')
  await submitSignIn(page, account)
  await expect(page).not.toHaveURL(/\/auth/)
}

/** Signs out from the Account page (opened from the user chip in the header) and checks the visitor landing page. */
export async function signOut(page: Page, account: E2eAccount): Promise<void> {
  await page.getByRole('link', { name: `Account: ${account.name}` }).click()
  await expect(page).toHaveURL(/\/(student|teacher|admin)\/account$/)
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

/** Focus mode (lesson player, review, setup, class activity, quiz): the task fills the screen, without the sidebar. */
export async function expectFocusMode(page: Page): Promise<void> {
  await expect(sidebar(page)).toHaveCount(0)
}

/**
 * The focus-mode "Leave …?" step (ConfirmDialog, tone leave): an alert dialog with the leave choice first and the
 * stay choice second, which has the focus so Enter never leaves by accident.
 */
export async function expectLeaveStep(page: Page, title: string, leave: string, stay: string): Promise<Locator> {
  const dialog = page.getByRole('alertdialog', { name: title })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('button')).toHaveText([leave, stay])
  await expect(dialog.getByRole('button', { name: stay })).toBeFocused()
  return dialog
}

/** A student who signs up during the run; the e-mail is new on every run. */
export interface NewStudent {
  name: string
  email: string
  password: string
}

export function newStudent(name: string, label: string): NewStudent {
  return { name, email: `e2e.${label}.${Date.now()}@example.test`, password: NEW_STUDENT_PASSWORD }
}

/** Creates a student account through the Create account form (/auth?tab=register) and submits it. */
export async function registerStudent(page: Page, student: NewStudent): Promise<void> {
  await page.goto('/auth?tab=register')
  await expect(page.getByRole('tab', { name: 'Create account' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('radio', { name: /^Student/ })).toBeChecked()
  await page.getByLabel(/^Full name/).fill(student.name)
  await page.getByLabel(/^Email address/).fill(student.email)
  await page.getByLabel(/^Password/).fill(student.password)
  await page.getByLabel(/^Confirm password/).fill(student.password)
  await page.getByRole('checkbox', { name: /^I am 18 or older and agree to the Terms of Service/ }).check()
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
}

/** Switches the interface language with the EN / BM / 简体中文 switch of the header. */
export async function switchLanguage(page: Page, label: 'EN' | 'BM' | '简体中文'): Promise<void> {
  // The visible label stays the same in every language; the rest of the accessible name is translated.
  await page.getByRole('button', { name: new RegExp(`^${label}, `) }).click()
}

/** The Authorization header of a direct API call. */
export function bearer(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` }
}

/** Signs in through the API and returns the access token (`data.token` of POST /auth/login). */
export async function apiToken(request: APIRequestContext, account: E2eAccount): Promise<string> {
  const response = await request.post(`${API_BASE_URL}/auth/login`, {
    data: { email: account.email, password: account.password },
  })
  expect(response.status()).toBe(200)
  const body = (await response.json()) as { data: { token: string } }
  return body.data.token
}
