import { expect, test } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import { sidebar, signIn, signOut } from './support'

// This file changes shared data, so it runs after the visitor, applicant and teacher files that expect the seeded
// state: it approves Li Wei's in-review class and Mei Lin's application.
const IN_REVIEW_CLASS = 'Chinese Culture and Festivals'
const IN_REVIEW_REVISION = 'Ordering Food at the Canteen'

test.beforeEach(async ({ page }) => {
  await signIn(page, E2E_ACCOUNTS.admin)
  await expect(page).toHaveURL(/\/admin\/dashboard$/)
})

test('the dashboard shows what needs attention and the platform numbers', async ({ page }) => {
  await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Teacher applications (2)' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Review queue (2)' })).toBeVisible()

  const numbers = page.getByRole('region', { name: 'The platform in numbers' })
  await expect(numbers).toContainText(/Users\s*12\s*5 students · 6 teachers · 1 admin/)
  await expect(numbers.getByRole('term')).toHaveText(['Users', 'Published content', 'AI requests', 'Audit events'])
})

test('Users lists the accounts and search finds a student', async ({ page }) => {
  await sidebar(page)
    .getByRole('link', { name: /^Users/ })
    .click()

  await expect(page).toHaveURL(/\/admin\/users$/)
  await expect(page.getByRole('heading', { name: 'Users', level: 1 })).toBeVisible()
  await expect(page.getByText('Showing 1–10 of 12')).toBeVisible()
  await expect(page.getByRole('row', { name: /^Li Wei teacher@fyp\.local Teacher Active Approved/ })).toBeVisible()

  await page.getByRole('searchbox', { name: 'Search' }).fill('Amira')
  await expect(page.getByText('Showing 1–1 of 1')).toBeVisible()
  await expect(page.getByRole('row', { name: /^Amira Hassan student@fyp\.local Student Active/ })).toBeVisible()
  await expect(page.getByRole('row', { name: /^Li Wei / })).toHaveCount(0)
})

test.describe.serial('content review', () => {
  test('approving the in-review class takes it out of the queue and publishes it', async ({ page }) => {
    await sidebar(page)
      .getByRole('link', { name: /^Content/ })
      .click()

    await expect(page).toHaveURL(/\/admin\/content$/)
    await expect(page.getByRole('heading', { name: 'Review queue', level: 1 })).toBeVisible()
    const classItem = page.getByRole('button', { name: new RegExp(`^${IN_REVIEW_CLASS} Class New class`) })
    const revisionItem = page.getByRole('button', { name: new RegExp(`^${IN_REVIEW_REVISION} Lesson Revision`) })
    await expect(classItem).toBeVisible()
    await expect(revisionItem).toBeVisible()

    await classItem.click()
    await expect(page.getByRole('heading', { name: IN_REVIEW_CLASS, level: 2 })).toBeVisible()
    await page.getByRole('button', { name: 'Approve', exact: true }).click()
    // Confirm steps (ConfirmDialog) are alert dialogs; drawers and forms (AppOverlay) are plain dialogs.
    const dialog = page.getByRole('alertdialog', { name: `Approve "${IN_REVIEW_CLASS}"?` })
    await dialog.getByRole('button', { name: 'Approve' }).click()

    await expect(dialog).toBeHidden()
    await expect(page.getByText(`"${IN_REVIEW_CLASS}" was approved.`)).toBeVisible()
    await expect(classItem).toHaveCount(0)
    await expect(revisionItem).toBeVisible()
    await expect(page.getByRole('link', { name: 'Review queue 1' })).toBeVisible()

    // The public catalogue now lists it with the 8 classes published before.
    await page.goto('/classes')
    await expect(page.getByRole('heading', { name: '9 classes matched' })).toBeVisible()
    await page.getByRole('link', { name: IN_REVIEW_CLASS }).click()
    await expect(page.getByRole('heading', { name: IN_REVIEW_CLASS, level: 1 })).toBeVisible()
    await expect(page.getByRole('complementary')).toContainText('Li Wei')
  })

  test('the audit log lists the approval', async ({ page }) => {
    await sidebar(page).getByRole('link', { name: 'Audit log' }).click()

    await expect(page).toHaveURL(/\/admin\/audit-log$/)
    await expect(page.getByRole('heading', { name: 'Audit log', level: 1 })).toBeVisible()
    // Event (an approval, naming the class), category Content, and the admin who approved it.
    const entries = page.getByRole('region', { name: 'Audit entries' }).getByRole('table')
    await expect(
      entries.getByRole('row', {
        name: new RegExp(`approved ${IN_REVIEW_CLASS} Content Platform Administrator admin@fyp\\.local`, 'i'),
      }),
    ).toBeVisible()
  })
})

test('approving Mei Lin’s application unlocks her teacher tools after she signs in again', async ({ page }) => {
  await page.goto('/admin/users/applications')
  await expect(page.getByRole('heading', { name: 'Teacher applications', level: 1 })).toBeVisible()
  const waiting = page.getByRole('list', { name: 'Applications waiting for a decision' })
  const application = waiting.getByRole('button', { name: /^Mei Lin applicant@fyp\.local In review/ })
  await application.click()

  const details = page.getByRole('region', { name: 'Mei Lin' })
  await expect(details.getByRole('heading', { name: 'Documents' })).toBeVisible()
  await details.getByRole('button', { name: 'Approve' }).click()
  const dialog = page.getByRole('dialog', { name: 'Approve Mei Lin?' })
  await expect(dialog).toContainText('Mei Lin can use all teaching tools straight away.')
  await dialog.getByRole('button', { name: 'Approve' }).click()

  await expect(dialog).toBeHidden()
  await expect(page.getByText('Mei Lin is approved and can use all teaching tools now.')).toBeVisible()
  await expect(application).toHaveCount(0)

  await signOut(page, E2E_ACCOUNTS.admin)
  await signIn(page, E2E_ACCOUNTS.applicant)
  await expect(sidebar(page).getByRole('link', { name: 'Classes', exact: true })).toBeVisible()
  await page.goto('/teacher/classes')
  await expect(page).toHaveURL(/\/teacher\/classes$/)
  await expect(page.getByRole('heading', { name: 'Classes', level: 1 })).toBeVisible()
  await expect(page.getByRole('button', { name: 'New class' }).first()).toBeVisible()
})
