import { expect, test } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import { sidebar, signIn } from './support'

// Li Wei, the approved demo teacher: "Mandarin for Beginners" (published; Amira Hassan, Chen Hao and Zhang Yi
// enrolled), "HSK 1 Prep" (published), "Chinese Culture and Festivals" (in review) and "Campus Mandarin" (draft).
const ENROLLED = ['Zhang Yi', 'Amira Hassan', 'Chen Hao']

test.beforeEach(async ({ page }) => {
  await signIn(page, E2E_ACCOUNTS.teacher)
  await expect(page).toHaveURL(/\/teacher\/dashboard$/)
})

test('the dashboard shows the To do list and the teaching numbers', async ({ page }) => {
  await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'To do' })).toBeVisible()
  for (const item of [
    'Changes requested: University Life (revision)',
    'In review: Chinese Culture and Festivals (new class)',
    'Revision in review: Ordering Food at the Canteen',
  ]) {
    await expect(page.getByText(item, { exact: true })).toBeVisible()
  }

  const numbers = page.getByRole('region', { name: 'Your numbers' })
  await expect(numbers).toContainText(/Students\s*3\s*1 needs support/)
  await expect(numbers).toContainText(/Published content\s*13\s*2 classes and 11 lessons/)
  await expect(numbers).toContainText(/Upcoming live sessions\s*4\s*8 bookings/)
  await expect(numbers).toContainText(/Quiz average\s*66%/)
})

test('Classes lists his classes with their status', async ({ page }) => {
  await sidebar(page).getByRole('link', { name: 'Classes' }).click()

  await expect(page).toHaveURL(/\/teacher\/classes$/)
  await expect(page.getByRole('heading', { name: 'Classes', level: 1 })).toBeVisible()
  for (const [title, status] of [
    ['Mandarin for Beginners', 'Published'],
    ['HSK 1 Prep', 'Published'],
    ['Chinese Culture and Festivals', 'In review'],
    ['Campus Mandarin', 'Draft'],
  ]) {
    const card = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: title, exact: true }) })
    await expect(card).toContainText(status)
  }
})

test('a new class is saved as a draft and listed in Classes', async ({ page }) => {
  const title = `E2E draft class ${Date.now()}`
  await page.goto('/teacher/classes')
  await page.getByRole('button', { name: 'New class' }).first().click()

  const drawer = page.getByRole('dialog', { name: 'New class' })
  await expect(drawer).toBeVisible()
  await expect(drawer.getByRole('button', { name: 'Create class' })).toBeDisabled()
  await drawer.getByRole('textbox', { name: 'Title' }).fill(title)
  await drawer.getByRole('textbox', { name: 'Description' }).fill('A class created by the end-to-end tests.')
  await drawer.getByRole('combobox', { name: 'Difficulty' }).selectOption('Beginner 1')
  await drawer.getByRole('combobox', { name: 'Topic' }).selectOption('Pinyin')
  await drawer.getByRole('button', { name: 'Recorded' }).click()
  await drawer.getByRole('button', { name: 'Create class' }).click()

  await expect(page).toHaveURL(/\/teacher\/classes\/\d+\?created=1$/)
  await expect(page.getByRole('heading', { name: title, level: 1 })).toBeVisible()

  await page.getByRole('navigation', { name: 'Breadcrumb' }).getByRole('link', { name: 'Classes' }).click()
  await expect(page).toHaveURL(/\/teacher\/classes$/)
  const card = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: title }) })
  await expect(card).toContainText('Draft')
  await expect(card).toContainText('Beginner 1 · Pinyin · Recorded')
})

test('a class page shows its units and lessons', async ({ page }) => {
  await page.goto('/teacher/classes')
  await page.getByRole('link', { name: 'Open Mandarin for Beginners' }).click()

  await expect(page).toHaveURL(/\/teacher\/classes\/\d+$/)
  await expect(page.getByRole('heading', { name: 'Mandarin for Beginners', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Units and lessons' })).toBeVisible()
  await expect(page.getByRole('heading', { name: /First Conversations$/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: /Campus Life$/ })).toBeVisible()
  // A lesson whose revision is in review opens read-only ("Open"); the others are edited ("Edit").
  for (const lesson of [
    'Greetings',
    'Numbers 1–10',
    'University Life',
    'Ordering Food at the Canteen',
    'Finding Places',
  ]) {
    await expect(page.getByRole('link', { name: new RegExp(`^(Edit|Open) ${lesson}$`) })).toBeVisible()
  }
})

test('Live sessions lists the upcoming sessions', async ({ page }) => {
  await sidebar(page).getByRole('link', { name: 'Live sessions' }).click()

  await expect(page).toHaveURL(/\/teacher\/live-sessions$/)
  await expect(page.getByRole('heading', { name: 'Live sessions', level: 1 })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Upcoming 4' })).toBeVisible()
  for (const session of ['Greetings 问候', 'University Life', 'Ordering Food 点菜', 'Tones Clinic']) {
    await expect(page.getByRole('button', { name: `Open ${session}` })).toBeVisible()
  }
})

test('Students lists the enrolled students and shows one student’s progress and quiz results', async ({ page }) => {
  await sidebar(page).getByRole('link', { name: 'Students' }).click()

  await expect(page).toHaveURL(/\/teacher\/students/)
  await expect(page.getByRole('heading', { name: 'My students', exact: true })).toBeVisible()
  for (const name of ENROLLED) {
    await expect(page.getByRole('button', { name: new RegExp(`^${name} `) })).toBeVisible()
  }

  await page.getByRole('button', { name: /^Amira Hassan / }).click()
  await expect(page.getByRole('heading', { name: 'Amira Hassan', level: 2 })).toBeVisible()
  await expect(page.getByRole('main')).toContainText(/Class progress\s*40%/)
  await expect(page.getByRole('heading', { name: 'Progress by lesson' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Quiz results' })).toBeVisible()
  const results = page.getByRole('region', { name: 'Quiz results of Amira Hassan' })
  await expect(results.getByRole('row', { name: /^Greetings .* 80%$/ })).toBeVisible()
  await expect(results.getByRole('row', { name: /^Numbers 1–10 .* 100%$/ })).toBeVisible()
})

test('feedback written to a student appears in Feedback history', async ({ page }) => {
  const message = `Nice work on your tones this week (e2e ${Date.now()}).`
  await page.goto('/teacher/students')
  await page.getByRole('button', { name: /^Amira Hassan / }).click()
  await expect(page.getByRole('heading', { name: 'Amira Hassan', level: 2 })).toBeVisible()
  await page.getByRole('button', { name: 'Write feedback' }).click()

  const drawer = page.getByRole('dialog', { name: 'Write feedback' })
  await expect(drawer).toContainText('To Amira Hassan · Mandarin for Beginners')
  await drawer.getByRole('button', { name: 'Encouraging' }).click()
  await drawer.getByRole('textbox', { name: 'Message' }).fill(message)
  await drawer.getByRole('button', { name: 'Send feedback' }).click()
  await expect(drawer).toBeHidden()
  await expect(page.getByText('Your feedback was sent to Amira Hassan.')).toBeVisible()

  await page.getByRole('link', { name: /^Feedback history/ }).click()
  await expect(page).toHaveURL(/\/teacher\/students\/feedback/)
  const sent = page.getByRole('listitem').filter({ hasText: message })
  await expect(sent).toBeVisible()
  await expect(sent).toContainText('Encouraging')
  await expect(sent).toContainText('To Amira Hassan · Mandarin for Beginners')
})
