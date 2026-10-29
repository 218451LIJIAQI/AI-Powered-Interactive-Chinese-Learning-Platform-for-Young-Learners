import { expect, test, type Page } from '@playwright/test'

import { expectFocusMode, expectLeaveStep, newStudent, registerStudent, sidebar, type NewStudent } from './support'

// New students sign up through the Create account form (a new e-mail on every run) and land in the first-run
// setup. The AI provider is off, so a plan is built from the student's own answers.

/** The e-mail banner of a new account on Home. */
async function expectConfirmEmailBanner(page: Page, student: NewStudent): Promise<void> {
  const banner = page.getByRole('status').filter({ hasText: 'Confirm your email.' })
  await expect(banner).toContainText(`We sent a link to ${student.email}. You can keep learning meanwhile.`)
  await expect(banner.getByRole('button', { name: 'Send the link again' })).toBeEnabled()
}

test('a new student signs up, builds her plan from her answers and starts her path', async ({ page }) => {
  const student = newStudent('Nadia Rahman', 'setup')
  await registerStudent(page, student)

  await expect(page).toHaveURL(/\/student\/setup$/)
  await expectFocusMode(page)
  await expect(page.getByRole('heading', { name: 'Let’s build your Mandarin plan', level: 2 })).toBeVisible()
  await page.getByRole('button', { name: 'Let’s start' }).click()
  const next = page.getByRole('button', { name: 'Continue', exact: true })

  // 1 Why
  await expect(page.getByText('Your plan · 1 of 5')).toBeVisible()
  await expect(next).toBeDisabled()
  await page
    .getByRole('group', { name: 'Main reason' })
    .getByRole('button', { name: /^Travel/ })
    .click()
  await expect(page.getByText('Travel it is! We’ll focus on food, directions and getting around.')).toBeVisible()
  await next.click()

  // 2 Goal
  await expect(page.getByRole('heading', { name: 'What do you want to be able to do?', level: 2 })).toBeVisible()
  const wants = page.getByRole('group', { name: 'What you want to do' })
  await wants.getByRole('button', { name: 'Order food' }).click()
  await wants.getByRole('button', { name: 'Ask for directions' }).click()
  await expect(wants.getByRole('button', { name: 'Order food' })).toHaveAttribute('aria-pressed', 'true')
  await next.click()

  // 3 Pace
  await expect(page.getByRole('heading', { name: 'When, and how much time?', level: 2 })).toBeVisible()
  await expect(next).toBeDisabled()
  await page
    .getByRole('group', { name: 'I want to reach my goal in' })
    .getByRole('button', { name: '2 months' })
    .click()
  await page
    .getByRole('group', { name: 'Time I can give' })
    .getByRole('button', { name: /^Regular/ })
    .click()
  await next.click()

  // 4 Her Mandarin, then the level check: she does not know the answers yet.
  await expect(page.getByRole('heading', { name: 'How much Mandarin do you know?', level: 2 })).toBeVisible()
  await page.getByRole('button', { name: /^New to Mandarin/ }).click()
  await page.getByRole('button', { name: 'Start the level check' }).click()
  await expect(page.getByText(/^Level check · 1 of \d+$/)).toBeVisible()
  const dontKnow = page.getByRole('button', { name: 'I don’t know yet' })
  const enough = page.getByRole('heading', { name: 'That’s all we need!', level: 2 })
  for (let asked = 0; !(await enough.isVisible()); asked += 1) {
    expect(asked, 'the level check stops after at most 6 questions').toBeLessThan(6)
    await dontKnow.click()
    await expect(page.getByRole('status').filter({ hasText: 'No problem!' })).toBeVisible()
    await next.click()
    await expect(dontKnow.or(enough)).toBeVisible()
  }
  await expect(page.getByRole('definition').first()).toHaveText('Nothing yet, and that’s fine')
  await next.click()

  // 5 Focus
  await expect(page.getByRole('heading', { name: 'What do you want to get better at?', level: 2 })).toBeVisible()
  await page
    .getByRole('group', { name: 'What do you want to get better at?' })
    .getByRole('button', { name: /^Speaking/ })
    .click()
  await page.getByRole('button', { name: 'Build my plan' }).click()

  // The plan, built from her answers, for her to confirm.
  await expect(page.getByRole('heading', { name: 'Your plan is ready!', level: 2 })).toBeVisible()
  const goal = page.getByRole('region', { name: 'Your goal' })
  await expect(goal).toContainText('Travel')
  await expect(goal).toContainText('in 2 months')
  await expect(goal.getByRole('listitem')).toHaveText(['Order food', 'Ask for directions'])
  await expect(page.getByRole('region', { name: 'Your Mandarin level' })).toContainText('New to Mandarin')
  await page.getByRole('button', { name: 'Yes, show my path' }).click()

  await expect(page.getByRole('heading', { name: 'Your path · Travel', level: 2 })).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: 'Built from your answers.' })).toContainText(
    'SparkBot isn’t available right now, so we used your choices and your level check',
  )
  const steps = page.getByRole('list', { name: 'The steps of your plan' })
  await expect(steps).toContainText('Learn · Unit 1 · Say hello · 你好')
  await expect(steps).toContainText('matches your goal: Order food.')
  await expect(steps).toContainText('matches your goal: Ask for directions.')
  await page.getByRole('button', { name: 'Start my path' }).click()

  // Home on her first day, with the plan.
  await expect(page).toHaveURL(/\/student\/home$/)
  await expect(sidebar(page)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Welcome to MandarinSpark, Nadia', level: 1 })).toBeVisible()
  const homeGoal = page.getByRole('region', { name: 'Your goal' })
  await expect(homeGoal).toContainText('Travel')
  await expect(homeGoal).toContainText(/week 1 of \d+/)
  await expect(homeGoal.getByRole('link', { name: 'View my plan' })).toBeVisible()
  const unit1 = page.getByRole('region', { name: 'Say hello · 你好' })
  await expect(unit1).toContainText('First in your plan · Learn · Unit 1')
  await expect(unit1.getByRole('link', { name: 'Start Unit 1' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'This week' })).toContainText('Start Say hello')
  await expect(page.getByRole('region', { name: 'Build your learning plan' })).toHaveCount(0)
  await expectConfirmEmailBanner(page, student)
})

test('a new student can set the plan up later and gets Home without a plan', async ({ page }) => {
  const student = newStudent('Farid Ismail', 'later')
  await registerStudent(page, student)

  await expect(page).toHaveURL(/\/student\/setup$/)
  await page.getByRole('button', { name: 'Set up later' }).click()
  let dialog = await expectLeaveStep(page, 'Set up your plan later?', 'Set up later', 'Keep going')
  await expect(dialog).toContainText('You can build it any time from Home.')
  await dialog.getByRole('button', { name: 'Keep going' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('heading', { name: 'Let’s build your Mandarin plan', level: 2 })).toBeVisible()

  await page.getByRole('button', { name: 'Set up later' }).click()
  dialog = await expectLeaveStep(page, 'Set up your plan later?', 'Set up later', 'Keep going')
  await dialog.getByRole('button', { name: 'Set up later' }).click()

  await expect(page).toHaveURL(/\/student\/home$/)
  await expect(page.getByRole('heading', { name: 'Welcome to MandarinSpark, Farid', level: 1 })).toBeVisible()
  await expect(
    page.getByRole('region', { name: 'Build your learning plan' }).getByRole('link', { name: 'Build my plan' }),
  ).toBeVisible()
  await expect(page.getByRole('region', { name: 'Your goal' })).toHaveCount(0)
  await expect(page.getByRole('region', { name: 'Say hello · 你好' })).toContainText('Start here · Learn · Unit 1')
  const firstWeek = page.getByRole('region', { name: 'Your first week' })
  await expect(firstWeek.getByRole('group', { name: 'Daily goal' }).getByRole('button')).toHaveText([
    '10 XP',
    '20 XP',
    '30 XP',
  ])
  await expect(firstWeek.getByRole('button', { name: '20 XP' })).toHaveAttribute('aria-pressed', 'true')
  await expectConfirmEmailBanner(page, student)

  // Home no longer sends her to the setup.
  await page.reload()
  await expect(page).toHaveURL(/\/student\/home$/)
  await expect(page.getByRole('region', { name: 'Build your learning plan' })).toBeVisible()
})
