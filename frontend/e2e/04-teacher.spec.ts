import { expect, test } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import { sidebar, signIn } from './support'

// Li Wei, the approved demo teacher: "Mandarin for Campus Life" (published; Amira Hassan, Chen Hao and Zhang Yi
// enrolled), "Chinese Through Film" (published), "Chinese Culture and Festivals" (in review) and "Study Group Mandarin"
// (draft).
const ENROLLED = ['Zhang Yi', 'Amira Hassan', 'Chen Hao']

test.beforeEach(async ({ page }) => {
  await signIn(page, E2E_ACCOUNTS.teacher)
  await expect(page).toHaveURL(/\/teacher\/dashboard$/)
})

test('the dashboard shows the To do list, the students needing support and the upcoming sessions', async ({ page }) => {
  await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible()

  const todo = page.getByRole('region', { name: 'To do' })
  await expect(todo).toContainText('1 needs you · 2 to keep an eye on')
  for (const [item, action] of [
    ['Changes requested: University Life', 'Edit lesson: University Life'],
    ['In review: Chinese Culture and Festivals (new class)', 'Open Chinese Culture and Festivals'],
    ['In review: Ordering Food at the Canteen', 'Open Ordering Food at the Canteen'],
  ] as const) {
    const row = todo.getByRole('listitem').filter({ hasText: item })
    await expect(row).toBeVisible()
    await expect(row.getByRole('link', { name: action })).toBeVisible()
  }

  const support = page.getByRole('region', { name: 'Students needing support' })
  await expect(support.getByRole('listitem')).toHaveCount(1)
  const student = support.getByRole('link', { name: 'Open Zhang Yi' })
  await expect(student).toContainText('20% class progress · quiz average 50%')
  await expect(student).toContainText('Needs support')

  const sessions = page.getByRole('region', { name: 'Upcoming live sessions' })
  for (const session of ['Meeting Classmates 认识同学', 'University Life', 'Ordering Food 点菜', 'Campus Q&A']) {
    await expect(sessions.getByRole('link', { name: `Open ${session}` })).toBeVisible()
  }

  // A row goes to the student it names.
  await student.click()
  await expect(page).toHaveURL(/\/teacher\/students\?student=\d+$/)
  await expect(page.getByRole('heading', { name: 'Zhang Yi', level: 2 })).toBeVisible()
})

test('Classes lists his classes with their status', async ({ page }) => {
  await sidebar(page).getByRole('link', { name: 'Classes' }).click()

  await expect(page).toHaveURL(/\/teacher\/classes$/)
  await expect(page.getByRole('heading', { name: 'Classes', level: 1 })).toBeVisible()
  for (const [title, status, facts] of [
    ['Mandarin for Campus Life', 'Published', '5 lessons · 1 in review · 1 with changes requested'],
    ['Chinese Through Film', 'Published', '6 lessons · 2 units · all published'],
    ['Chinese Culture and Festivals', 'In review', '2 lessons · 1 in review · 1 draft'],
    ['Study Group Mandarin', 'Draft', '1 lesson · 1 draft'],
  ]) {
    const card = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: title, exact: true }) })
    await expect(card).toContainText(status)
    await expect(card).toContainText(facts)
    await expect(card.getByRole('link', { name: `Open ${title}` })).toBeVisible()
  }
})

test('a new class is saved as a draft and listed in Classes', async ({ page }) => {
  const title = `E2E draft class ${Date.now()}`
  await page.goto('/teacher/classes')
  await page.getByRole('button', { name: 'New class' }).click()

  const drawer = page.getByRole('dialog', { name: 'New class' })
  await expect(drawer).toBeVisible()
  await expect(drawer.getByRole('button', { name: 'Create class' })).toBeDisabled()
  await drawer.getByRole('textbox', { name: 'Title' }).fill(title)
  await drawer.getByRole('textbox', { name: 'Description' }).fill('A class created by the end-to-end tests.')
  await drawer.getByRole('combobox', { name: 'Theme' }).selectOption('Daily life')
  await drawer.getByRole('combobox', { name: 'Best after (Learn path)' }).selectOption('Unit 2')
  await drawer.getByRole('button', { name: 'Create class' }).click()

  await expect(page).toHaveURL(/\/teacher\/classes\/\d+\?created=1$/)
  await expect(page.getByRole('heading', { name: title, level: 1 })).toBeVisible()

  await page.getByRole('navigation', { name: 'Breadcrumb' }).getByRole('link', { name: 'Classes' }).click()
  await expect(page).toHaveURL(/\/teacher\/classes$/)
  const card = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: title }) })
  await expect(card).toContainText('Draft')
  await expect(card).toContainText('Daily life')
  await expect(card).toContainText('Best after Unit 2')
})

test('a class page shows its units and lessons', async ({ page }) => {
  await page.goto('/teacher/classes')
  await page.getByRole('link', { name: 'Open Mandarin for Campus Life' }).click()

  await expect(page).toHaveURL(/\/teacher\/classes\/\d+$/)
  await expect(page.getByRole('heading', { name: 'Mandarin for Campus Life', level: 1 })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Units and lessons' })).toBeVisible()
  await expect(page.getByRole('heading', { name: /First Conversations$/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: /Campus Life$/, level: 3 })).toBeVisible()
  // A lesson whose update is in review opens read-only ("Open"); the others are edited ("Edit").
  for (const lesson of ['Meeting Classmates', 'Room Numbers', 'University Life', 'Finding Places']) {
    await expect(page.getByRole('link', { name: `Edit ${lesson}` })).toBeVisible()
  }
  await expect(page.getByRole('link', { name: 'Open Ordering Food at the Canteen' })).toBeVisible()
})

test('Live sessions lists the upcoming sessions', async ({ page }) => {
  await sidebar(page).getByRole('link', { name: 'Live sessions' }).click()

  await expect(page).toHaveURL(/\/teacher\/live-sessions$/)
  await expect(page.getByRole('heading', { name: 'Live sessions', level: 1 })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Upcoming 4' })).toBeVisible()
  for (const session of ['Meeting Classmates 认识同学', 'University Life', 'Ordering Food 点菜', 'Campus Q&A']) {
    await expect(page.getByRole('button', { name: `Edit ${session}` })).toBeVisible()
  }
  // A cancelled session stays in the list but can no longer be changed.
  const cancelled = page.getByRole('listitem').filter({ hasText: 'Finding Places 问路' })
  await expect(cancelled).toContainText('Cancelled')
  await expect(cancelled.getByRole('button')).toHaveCount(0)
})

test('Students lists the enrolled students and shows one student’s progress and best quiz scores', async ({ page }) => {
  await sidebar(page).getByRole('link', { name: 'Students' }).click()

  await expect(page).toHaveURL(/\/teacher\/students/)
  await expect(page.getByRole('heading', { name: 'My students', exact: true })).toBeVisible()
  for (const name of ENROLLED) {
    await expect(page.getByRole('button', { name: new RegExp(`^${name} `) })).toBeVisible()
  }

  await page.getByRole('button', { name: /^Amira Hassan / }).click()
  await expect(page.getByRole('heading', { name: 'Amira Hassan', level: 2 })).toBeVisible()
  await expect(page.getByRole('main')).toContainText(/Class progress\s*40%/)
  await expect(page.getByRole('main')).toContainText(/Quiz average\s*75%/)
  const lessons = page.getByRole('region', { name: 'Progress by lesson' }).getByRole('listitem')
  await expect(lessons.filter({ hasText: 'Meeting Classmates' })).toContainText('Best quiz 80%')
  await expect(lessons.filter({ hasText: 'Room Numbers' })).toContainText('Best quiz 100%')
  await expect(lessons.filter({ hasText: 'Ordering Food at the Canteen' })).toContainText('Best quiz 60%')
})

test('feedback written to a student appears in Feedback you sent', async ({ page }) => {
  const message = `Nice work on your tones this week (e2e ${Date.now()}).`
  await page.goto('/teacher/students')
  await page.getByRole('button', { name: /^Amira Hassan / }).click()
  await expect(page.getByRole('heading', { name: 'Amira Hassan', level: 2 })).toBeVisible()
  const sent = page.getByRole('region', { name: 'Feedback you sent' }).getByRole('listitem')
  await expect(sent.filter({ hasText: message })).toHaveCount(0)
  await page.getByRole('button', { name: 'Write feedback' }).click()

  const drawer = page.getByRole('dialog', { name: 'Write feedback' })
  await expect(drawer).toContainText('To Amira Hassan')
  // It is about her latest quiz attempt unless he chooses otherwise.
  const about = drawer.getByRole('combobox', { name: 'About' })
  await expect(about.getByRole('option', { selected: true })).toContainText('Quiz attempt · Meeting Classmates')
  await expect(drawer.getByRole('button', { name: 'Send feedback' })).toBeDisabled()
  await drawer.getByRole('textbox', { name: 'Your feedback' }).fill(message)
  await drawer.getByRole('button', { name: 'Send feedback' }).click()
  await expect(drawer).toBeHidden()
  await expect(page.getByText('Feedback sent to Amira Hassan.')).toBeVisible()

  await expect(sent.filter({ hasText: message })).toBeVisible()
  await expect(sent.filter({ hasText: message })).toContainText('Lesson quiz · Meeting Classmates')
})
