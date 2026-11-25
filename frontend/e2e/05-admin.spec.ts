import type { Page } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import { API_BASE_URL, bestEffortApi, expect, sidebar, signIn, test } from './support'

// Admins are the MandarinSpark team: they answer support, review AI reports and make the classes learners join.
// The demo profile seeds two class drafts and the eight published catalogue classes.
const DRAFTS = ['Chinese Culture and Festivals', 'Study Group Mandarin']

async function signInAdmin(page: Page, redirect?: string): Promise<void> {
  await signIn(page, E2E_ACCOUNTS.admin, redirect)
  if (!redirect) await expect(page).toHaveURL(/\/admin\/dashboard$/)
}

test('the compact navigation supports keyboard travel and docks when the window widens', async ({ page }) => {
  await signInAdmin(page)
  await page.setViewportSize({ width: 720, height: 450 })
  const opener = page.getByRole('button', { name: 'Workspace navigation' })
  const drawer = page.getByRole('dialog', { name: 'Workspace navigation' })
  await opener.focus()
  await page.keyboard.press('Enter')
  await expect(drawer.getByRole('button', { name: 'Close' })).toBeFocused()
  await page.keyboard.press('Shift+Tab')
  await expect(drawer.getByRole('link').last()).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(opener).toBeFocused()
  await opener.click()
  await drawer.getByRole('link', { name: 'Audit log' }).click()
  await expect(page).toHaveURL(/\/admin\/audit-log$/)
  await expect(drawer).toHaveCount(0)
  await expect(page.getByRole('main')).toBeFocused()
  await opener.click()
  await page.setViewportSize({ width: 1280, height: 720 })
  await expect(drawer).toHaveCount(0)
  await expect(sidebar(page).getByRole('link', { name: 'Audit log' })).toBeFocused()
})

test('the unit confirmation traps keyboard focus and returns it through both dialogs', async ({ page }) => {
  await signInAdmin(page, '/admin/content/learning-path')
  const opener = page.getByTestId('open-unit-1')
  await opener.focus()
  await page.keyboard.press('Enter')
  const drawer = page.getByTestId('unit-detail')
  const unpublish = drawer.getByTestId('unit-unpublish')
  await unpublish.focus()
  await page.keyboard.press('Enter')

  const dialog = page.getByRole('alertdialog')
  const close = dialog.getByRole('button', { name: 'Close' })
  await expect(dialog.getByTestId('confirm-dialog-cancel')).toBeFocused()
  await expect(drawer).toHaveAttribute('inert', '')
  await close.focus()
  await page.keyboard.press('Shift+Tab')
  await expect(dialog.getByTestId('confirm-dialog-confirm')).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(close).toBeFocused()

  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(unpublish).toBeFocused()
  await expect(page.locator('#app')).toHaveAttribute('inert', '')
  await page.keyboard.press('Escape')
  await expect(drawer).toHaveCount(0)
  await expect(opener).toBeFocused()
  await expect(page.locator('#app')).not.toHaveAttribute('inert', '')
})

test('the dashboard lists what needs attention, and Analytics has the platform numbers', async ({ page }) => {
  await signInAdmin(page)
  const main = page.getByRole('main')
  await expect(main.getByRole('heading', { name: 'Needs attention' })).toBeVisible()
  await expect(main.getByRole('heading', { name: /^Class drafts/ })).toBeVisible()
  await expect(main.getByRole('heading', { name: /^AI reports/ })).toBeVisible()
  await expect(main.getByRole('heading', { name: /^Support/ })).toBeVisible()
  for (const title of DRAFTS) await expect(main.getByRole('link', { name: new RegExp(title) })).toBeVisible()
  // No teacher applications or review queue any more.
  await expect(main).not.toContainText('Teacher applications')
  await expect(main).not.toContainText('Review queue')

  await sidebar(page).getByRole('link', { name: 'Analytics' }).click()
  await expect(page).toHaveURL(/\/admin\/analytics$/)
  const registrations = page.getByRole('region', { name: 'Registrations' })
  await expect(registrations).toContainText(/[1-9]\d*\s+learners?/)
  await expect(registrations).toContainText(/[1-9]\d*\s+admins?/)
  await expect(registrations).not.toContainText(/teacher/i)
})

test('Users finds a learner and her row opens her account', async ({ page }) => {
  await signInAdmin(page)
  await sidebar(page)
    .getByRole('link', { name: /^Users/ })
    .click()
  await expect(page).toHaveURL(/\/admin\/users(\?user=\d+)?$/)

  await page.getByRole('searchbox', { name: 'Search' }).fill('Amira')
  await expect(page).toHaveURL(/[?&]q=Amira(&|$)/)
  await expect(page.getByTestId('admin-users-row')).toHaveCount(1)
  await page.getByRole('button', { name: 'Open Amira Hassan' }).click()
  const main = page.getByRole('main')
  await expect(main.getByRole('heading', { name: 'Amira Hassan', level: 2 })).toBeVisible()
  await expect(main).toContainText('student@fyp.local')
  await expect(main).toContainText('Learner')
  await expect(main.getByRole('button', { name: 'Deactivate' })).toBeVisible()
})

test('Content › Classes lists classes and lessons with their status', async ({ page }) => {
  await signInAdmin(page)
  await sidebar(page)
    .getByRole('link', { name: /^Content/ })
    .click()
  await expect(page).toHaveURL(/\/admin\/content$/)
  await expect(page.getByRole('link', { name: /^Classes/ })).toBeVisible()
  const table = page.getByRole('table')
  for (const title of DRAFTS) {
    await expect(table.getByRole('row', { name: new RegExp(`^${title} Class .*Draft`) })).toBeVisible()
  }

  await page.getByLabel('Status').selectOption({ label: 'Published' })
  await expect(page).toHaveURL(/[?&]status=PUBLISHED(&|$)/)
  await expect(table.getByRole('row', { name: /Published/ }).first()).toBeVisible()
  await expect(table.getByRole('row', { name: /Draft/ })).toHaveCount(0)
})

test('an admin creates, edits and deletes a draft class with an audit trail', async ({ page, request }) => {
  await signInAdmin(page, '/admin/content')
  const classTitle = `E2E Hawker Mandarin ${Date.now()}`
  let createdClassId: number | null = null

  try {
    await test.step('create a draft that cannot be published yet', async () => {
      await page.getByRole('button', { name: 'New class' }).first().click()
      const dialog = page.getByRole('dialog', { name: 'New class' })
      const title = dialog.getByLabel(/^Title/)
      await title.focus()
      await title.blur()
      await expect(dialog.getByText('Add a class title.')).toBeVisible()
      await expect(dialog.getByRole('button', { name: 'Create class' })).toBeDisabled()

      await title.fill(classTitle)
      await dialog.getByRole('button', { name: 'Create class' }).click()
      await expect(page).toHaveURL(/\/admin\/content\/classes\/\d+$/)
      createdClassId = Number(/\/admin\/content\/classes\/(\d+)$/.exec(page.url())![1])
      await expect(page.getByRole('heading', { name: classTitle, level: 1 })).toBeVisible()
      await expect(page.getByTestId('class-notice')).toContainText('Learners can’t see this class yet.')
      await expect(page.getByRole('button', { name: 'Publish class' })).toBeDisabled()
    })

    await test.step('add a unit and open a new lesson in its editor', async () => {
      await page.goto('/admin/content')
      await page.getByRole('searchbox').fill(classTitle)
      await expect(page).toHaveURL(/[?&]q=E2E/)
      await page.getByRole('link', { name: `Open ${classTitle}` }).click()

      await page.getByTestId('add-first-unit').click()
      const unitDialog = page.getByRole('dialog', { name: 'Add a unit' })
      await unitDialog.getByLabel(/^Unit title/).fill('Unit 1: At the stall')
      await unitDialog.getByTestId('unit-dialog-confirm').click()
      await expect(page.getByTestId('toast')).toContainText('Unit added.')
      await expect(page.getByTestId('class-unit')).toHaveCount(1)

      await page.getByRole('button', { name: 'Add lesson to Unit 1: At the stall' }).click()
      const lessonDialog = page.getByRole('dialog', { name: 'Add a lesson' })
      await lessonDialog.getByLabel(/^Lesson title/).fill('Ordering chicken rice')
      await lessonDialog.getByTestId('unit-dialog-confirm').click()

      await expect(page).toHaveURL(/\/admin\/content\/classes\/\d+\/lessons\/\d+$/)
      await expect(page.getByRole('heading', { name: 'Ordering chicken rice', level: 1 })).toBeVisible()
      // A new lesson is a draft and can't be published until it is complete.
      await expect(page.getByRole('button', { name: 'Publish', exact: true })).toBeDisabled()
      await expect(page.getByRole('main')).toContainText('To publish, add')
    })

    await test.step('delete the draft and find its creation and deletion records', async () => {
      await page.goto('/admin/content')
      await page.getByRole('searchbox').fill(classTitle)
      await expect(page).toHaveURL(/[?&]q=E2E/)
      await page.getByRole('link', { name: `Open ${classTitle}` }).click()

      await page.getByTestId('delete-class').click()
      const confirm = page.getByRole('alertdialog', { name: `Delete “${classTitle}”?` })
      await confirm.getByRole('button', { name: 'Delete class' }).click()
      await expect(page).toHaveURL(/\/admin\/content(\?.*)?$/)
      createdClassId = null
      await expect(page.getByTestId('toast')).toContainText(`“${classTitle}” deleted.`)
      await page.getByRole('searchbox').fill(classTitle)
      await expect(page).toHaveURL(/[?&]q=E2E/)
      await expect(page.getByRole('heading', { name: `No classes or lessons match “${classTitle}”` })).toBeVisible()
      await expect(page.getByRole('row', { name: new RegExp(classTitle) })).toHaveCount(0)

      await sidebar(page).getByRole('link', { name: 'Audit log' }).click()
      await expect(page).toHaveURL(/\/admin\/audit-log$/)
      await page.getByRole('searchbox', { name: 'Search', exact: true }).fill(classTitle)
      await expect(page).toHaveURL(/[?&]q=E2E/)
      const entries = page.getByRole('row').filter({ hasText: classTitle })
      await expect(entries.filter({ hasText: 'Class created' })).toHaveCount(1)
      await expect(entries.filter({ hasText: 'Draft class deleted' })).toHaveCount(1)
    })
  } finally {
    if (createdClassId !== null) {
      await bestEffortApi(request, E2E_ACCOUNTS.admin, (headers) =>
        request.delete(`${API_BASE_URL}/admin/classes/${createdClassId}`, { headers }),
      )
    }
  }
})

test('Support opens a request, marks it solved with Undo, and switches queue tabs', async ({ page, request }) => {
  await signInAdmin(page, '/admin/support')
  const openTab = page.getByTestId('admin-support-tab-open')
  await expect(openTab).toHaveAttribute('aria-pressed', 'true')

  const list = page.getByRole('list', { name: 'Requests' })
  const videoItem = list.getByRole('button', { name: /#1042.*Lesson video won’t play/ })
  await expect(videoItem).toBeVisible()
  await expect(list.getByRole('button', { name: /#1041.*Can't sign in after resetting my password/ })).toBeVisible()
  await videoItem.click()

  const detail = page.getByTestId('admin-support-detail')
  await expect(detail.getByRole('heading', { name: '#1042 · Lesson video won’t play', level: 2 })).toBeVisible()
  await expect(page).toHaveURL(/[?&]request=\d+/)
  const requestId = Number(/[?&]request=(\d+)/.exec(page.url())![1])
  await expect(detail).toContainText('Amira Hassan')
  await expect(detail).toContainText('The video “Meeting a classmate”')
  await expect(detail.getByRole('link', { name: 'View the account of Amira Hassan' })).toHaveAttribute(
    'href',
    /\/admin\/users\?user=\d+/,
  )
  await expect(detail.getByTestId('admin-support-send')).toBeDisabled()

  await page.clock.install()
  let needsReopen = false
  try {
    await detail.getByTestId('admin-support-solve').click()
    needsReopen = true
    const solvedToast = page.getByRole('status').filter({ hasText: '#1042 marked as solved.' })
    await expect(solvedToast).toBeVisible()
    const undo = solvedToast.getByRole('button', { name: 'Undo' })
    await undo.hover()
    await expect(detail).toContainText('Solved')
    await expect(detail.getByTestId('admin-support-reply')).toHaveCount(0)

    await undo.click()
    await expect(detail).toContainText('Open')
    needsReopen = false
    await expect(detail.getByTestId('admin-support-reply')).toBeVisible()
    await expect(videoItem).toBeVisible()
  } finally {
    if (needsReopen) {
      await bestEffortApi(request, E2E_ACCOUNTS.admin, (headers) =>
        request.post(`${API_BASE_URL}/admin/support/${requestId}/reopen`, { headers }),
      )
    }
  }

  await page.getByTestId('admin-support-tab-answered').click()
  await expect(list.getByRole('button', { name: /#1039.*How do I change my email address\?/ })).toBeVisible()
  await page.getByTestId('admin-support-tab-solved').click()
  const solvedItem = list.getByRole('button', { name: /#1021.*Change my display name/ })
  await expect(solvedItem).toBeVisible()
  await solvedItem.click()
  await expect(detail.getByRole('heading', { name: '#1021 · Change my display name', level: 2 })).toBeVisible()
  await expect(detail).toContainText('Marked as solved by support')
  await expect(detail.getByTestId('admin-support-reply')).toHaveCount(0)
})

test('AI shows open and resolved reports, validates the review note, and opens Usage and Settings', async ({
  page,
}) => {
  await signInAdmin(page, '/admin/ai')
  const tabs = page.getByRole('navigation', { name: 'AI sections' })
  await expect(tabs.getByRole('link')).toHaveText([/^Reports\s*\d*$/, 'Usage', 'Settings'])

  const detail = page.getByTestId('admin-ai-report-detail')
  await expect(detail.getByRole('heading', { name: 'Incorrect content', level: 2 })).toBeVisible()
  await expect(detail).toContainText('Amira Hassan · student@fyp.local')
  await expect(detail).toContainText('How do I introduce myself in Mandarin?')
  await expect(detail).toContainText('The answer mixes 我是 and 我叫.')

  // Both Resolve and Dismiss require a note to the learner before submitting.
  await detail.getByRole('button', { name: 'Resolve', exact: true }).click()
  await expect(detail.getByRole('alert')).toContainText('Write a note to the learner before you resolve or dismiss.')
  await expect(detail.getByRole('button', { name: 'Resolve', exact: true })).toBeDisabled()
  await expect(detail.getByRole('button', { name: 'Dismiss', exact: true })).toBeDisabled()

  // Selecting a resolved report displays the stored admin note without decision controls.
  await page.getByRole('button', { name: /Unclear explanation.*Resolved.*Amira Hassan/ }).click()
  await expect(detail).toContainText('What is the difference between 再见 and 拜拜?')
  await expect(detail).toContainText('Thanks — the answer was too short. SparkBot now explains both.')
  await expect(detail.getByRole('button', { name: 'Resolve', exact: true })).toHaveCount(0)

  await tabs.getByRole('link', { name: 'Usage', exact: true }).click()
  await expect(page).toHaveURL(/\/admin\/ai\/usage$/)
  await expect(page.getByRole('heading', { name: 'Request log', level: 2 })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Export CSV' })).toBeVisible()

  await tabs.getByRole('link', { name: 'Settings', exact: true }).click()
  await expect(page).toHaveURL(/\/admin\/ai\/settings$/)
  await expect(page.getByRole('heading', { name: 'AI settings', level: 2 })).toBeVisible()
  await expect(page.getByLabel(/^AI notice text/)).toBeVisible()
  await expect(page.getByRole('switch', { name: /Sensitive-word filter/ })).toBeVisible()
  await expect(page.getByRole('switch', { name: /AI safety check/ })).toBeVisible()
})
