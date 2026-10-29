import { expect, test } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import { sidebar, signIn } from './support'

// Mei Lin applied to teach and is in review; 05-admin approves her later in the run.
const LOCKED_TOOLS = ['Classes', 'Live sessions', 'Students']

test.beforeEach(async ({ page }) => {
  await signIn(page, E2E_ACCOUNTS.applicant)
  await expect(page).toHaveURL(/\/teacher\/dashboard$/)
})

test('the applicant dashboard shows the verification status and the tools that open after approval', async ({
  page,
}) => {
  await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Verification: In review' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Your application' })).toContainText('3. Admin review')

  const opensLater = page.getByRole('region', { name: 'Opens after approval' })
  for (const tool of LOCKED_TOOLS) {
    await expect(opensLater.getByRole('link', { name: new RegExp(`^Locked ${tool}\\b`) })).toBeVisible()
  }
})

test('the teacher tools are locked in the sidebar and their pages route to the Locked page', async ({ page }) => {
  for (const tool of LOCKED_TOOLS) {
    await expect(
      sidebar(page).getByRole('link', { name: `${tool} (locked until an admin approves you)` }),
    ).toBeVisible()
  }
  await expect(sidebar(page).getByRole('link', { name: 'Dashboard', exact: true })).toBeVisible()

  await sidebar(page).getByRole('link', { name: 'Classes (locked until an admin approves you)' }).click()
  await expect(page).toHaveURL(/\/teacher\/locked$/)
  await expect(page.getByRole('heading', { name: 'Teaching tools', level: 1 })).toBeVisible()

  for (const path of ['/teacher/classes', '/teacher/live-sessions', '/teacher/students']) {
    await page.goto(path)
    await expect(page).toHaveURL(/\/teacher\/locked$/)
    await expect(page.getByRole('heading', { name: 'You can use the teaching tools after approval' })).toBeVisible()
  }
})

test('the applicant can open her teaching profile', async ({ page }) => {
  await sidebar(page).getByRole('link', { name: 'Teaching profile' }).click()

  await expect(page).toHaveURL(/\/teacher\/profile$/)
  await expect(page.getByRole('heading', { name: 'Teaching profile', level: 1 })).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'Full name' })).toHaveValue(E2E_ACCOUNTS.applicant.name)
  await expect(page.getByRole('region', { name: 'Verification' })).toContainText('In review')
  await expect(page.getByRole('list', { name: 'Verification documents' }).getByRole('listitem')).toHaveCount(3)
})
