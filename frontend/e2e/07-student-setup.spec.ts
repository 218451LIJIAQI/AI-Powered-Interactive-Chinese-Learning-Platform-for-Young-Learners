import type { Locator, Page } from '@playwright/test'

import { expect, expectFocusMode, expectLeaveStep, registerStudent, sidebar, test } from './support'

// New learners sign up through the Create account form (a new e-mail on every run) and land in the first-run setup.
// The AI provider is off, so a plan is built from the learner's own answers.

/** The e-mail banner of a new account on Home. */
async function expectConfirmEmailBanner(page: Page, email: string): Promise<void> {
  const banner = page.getByTestId('home-email')
  await expect(banner).toContainText('Confirm your email')
  await expect(banner).toContainText(`we sent a link to ${email}.`)
  await expect(banner.getByRole('button', { name: 'Send the link again' })).toBeEnabled()
}

/** Asserts the setup progress bar is visible with the expected percentage (the level check carries it on from 80%). */
async function expectSetupProgress(page: Page, percent: number | RegExp): Promise<void> {
  const bar = page.getByRole('progressbar', { name: 'Setup progress' })
  await expect(bar).toBeVisible()
  await expect(bar).toHaveAttribute('aria-valuenow', typeof percent === 'number' ? String(percent) : percent)
}

/** The footer's Continue of the setup's steps. */
function continueButton(page: Page): Locator {
  return page.getByRole('button', { name: 'Continue', exact: true })
}

/** Steps 1 and 2: the main reason and what she wants to be able to do. */
async function answerWhyAndGoal(page: Page, reason: RegExp, goals: readonly string[]): Promise<void> {
  const next = continueButton(page)
  await expect(page.getByRole('heading', { name: 'Why are you learning Mandarin?', level: 2 })).toBeVisible()
  await expectSetupProgress(page, 20)
  await expect(next).toBeDisabled()
  const picked = page.getByRole('group', { name: 'Main reason' }).getByRole('button', { name: reason })
  await picked.click()
  await expect(picked).toHaveAttribute('aria-pressed', 'true')
  await next.click()

  await expect(page.getByRole('heading', { name: 'What do you want to be able to do?', level: 2 })).toBeVisible()
  await expectSetupProgress(page, 40)
  const wants = page.getByRole('group', { name: 'What you want to do' })
  for (const goal of goals) {
    await wants.getByRole('button', { name: goal, exact: true }).click()
    await expect(wants.getByRole('button', { name: goal, exact: true })).toHaveAttribute('aria-pressed', 'true')
  }
  await next.click()
}

/** Starts the path from the plan's road map: "Start my path" makes the plan she built hers (POST /students/plan/confirm). */
async function startMyPath(page: Page): Promise<void> {
  const confirmed = page.waitForResponse(
    (response) => response.url().endsWith('/api/v1/students/plan/confirm') && response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: 'Start my path' }).click()
  expect((await confirmed).status()).toBe(200)
  await expect(page).toHaveURL(/\/student\/home$/)
  await expect(sidebar(page)).toBeVisible()
}

test('a new learner signs up, builds her plan towards a goal date and starts her path', async ({ page }) => {
  const email = await registerStudent(page, 'Nadia Rahman', 'setup')

  await expect(page).toHaveURL(/\/student\/setup$/)
  await expectFocusMode(page)
  await expect(page.getByRole('heading', { name: 'Let’s build your Mandarin plan', level: 2 })).toBeVisible()
  await page.getByRole('button', { name: 'Let’s start' }).click()
  const next = continueButton(page)

  await answerWhyAndGoal(page, /^Travel/, ['Order food', 'Ask for directions'])

  // 3 When, and how much time: a day picked on the calendar, and the time she can give each day.
  await expect(page.getByRole('heading', { name: 'When, and how much time?', level: 2 })).toBeVisible()
  await expectSetupProgress(page, 60)
  const time = page.getByRole('group', { name: 'Time I can give each day' })
  // The sourced bands (Shen & Wu 2026), 1–2 hours until she picks another.
  await expect(time.getByRole('button')).toHaveText(['Under 30 min', '30–60 min', '1–2 hours', '2+ hours'])
  await expect(time.getByRole('button', { name: '1–2 hours' })).toHaveAttribute('aria-pressed', 'true')
  // A date, or No date, comes first.
  await expect(next).toBeDisabled()
  const targetDate = page.getByRole('group', { name: 'Target date' })
  await targetDate.getByRole('button', { name: 'Pick a day on the calendar' }).click()

  const calendar = page.getByRole('dialog', { name: 'Pick your target date' })
  const useDate = calendar.getByRole('button', { name: 'Use this date' })
  await expect(useDate).toBeDisabled()
  // Three months from this one; the 15th of the last of them is about two months away.
  const months = calendar.getByRole('region')
  await expect(months).toHaveCount(3)
  const day = months.last().getByRole('button', { name: /^\w+ 15 \w+ \d{4}$/ })
  await day.click()
  await expect(day).toHaveAttribute('aria-pressed', 'true')
  await expect(calendar).toContainText(/ 15 \w{3,4} \d{4}\s*· \d+ days from today/)
  await useDate.click()
  await expect(calendar).toBeHidden()

  // The field shows the day ("Fri 15 Jan 2027 · 59 days to go"); a line says what the daily time adds up to.
  const field = targetDate.getByRole('button', { name: / · \d+ days to go$/ })
  const match = / *(.+) · (\d+) days to go *$/.exec(await field.innerText())
  expect(match).not.toBeNull()
  const [, goalDate, days] = match!
  expect(goalDate).toMatch(/^\w{3} 15 \w{3,4} \d{4}$/)
  await time.getByRole('button', { name: '30–60 min' }).click()
  await expect(time.getByRole('button', { name: '30–60 min' })).toHaveAttribute('aria-pressed', 'true')
  await expect(time.getByRole('button', { name: '1–2 hours' })).toHaveAttribute('aria-pressed', 'false')
  const [least, most] = [Math.round(Number(days) / 2), Number(days)]
  await expect(page.getByText(`30–60 min a day adds up to ${least}–${most} hours of Chinese.`)).toBeVisible()
  await next.click()

  // 4 Her Mandarin, then the level check: she does not know the answers yet.
  await expect(page.getByRole('heading', { name: 'How much Mandarin do you know?', level: 2 })).toBeVisible()
  await expectSetupProgress(page, 80)
  const level = page.getByRole('group', { name: 'How much Mandarin do you know?' }).getByRole('button', {
    name: /^Pre-HSK New/,
  })
  await level.click()
  await expect(level).toHaveAttribute('aria-pressed', 'true')
  // S1.32: brand new starts from the first lesson; she takes the level check after all.
  await expect(page.getByRole('button', { name: 'Start from the first lesson' })).toBeVisible()
  await page.getByRole('button', { name: 'Not new after all? Take the level check instead' }).click()
  await expect(page.getByText(/^Question 1 of \d+ · /)).toBeVisible()
  // The bar goes on from the 80% of the steps before instead of starting again.
  await expectSetupProgress(page, /^(8[1-9]|9\d)$/)
  const dontKnow = page.getByRole('button', { name: 'I don’t know yet' })
  const placed = page.getByRole('heading', { name: 'Your starting level: Pre-HSK (New)', level: 2 })
  for (let asked = 0; ; asked += 1) {
    await expect(dontKnow.or(placed)).toBeVisible()
    if (await placed.isVisible()) break
    expect(asked, 'the level check stops after at most 8 questions').toBeLessThan(8)
    await dontKnow.click()
    // "I don't know yet" is fine: the right answer is shown, with no mark against her.
    await expect(page.getByRole('status').filter({ hasText: 'No problem!' })).toBeVisible()
    await next.click()
  }
  await expectSetupProgress(page, 100)
  await next.click()

  // 5 Focus
  await expect(page.getByRole('heading', { name: 'What do you want to get better at?', level: 2 })).toBeVisible()
  await page
    .getByRole('group', { name: 'What do you want to get better at?' })
    .getByRole('button', { name: /^Speaking/ })
    .click()
  await page.getByRole('button', { name: 'Build my plan' }).click()

  // The plan, built from her answers, for her to confirm: what it means is shown at once, nothing to unfold.
  await expect(page.getByRole('heading', { name: 'Your plan is ready!', level: 2 })).toBeVisible()
  const placement = page.getByRole('region', { name: 'Your Mandarin level' })
  await expect(placement).toContainText('Pre-HSK (New)')
  await expect(placement.getByRole('heading', { level: 4 })).toHaveText([
    'You’ll start with',
    'For your trip, you’ll learn to',
  ])
  await expect(placement.getByRole('button')).toHaveCount(0)
  const goal = page.getByRole('region', { name: 'Your goal' })
  await expect(goal).toContainText('Travel')
  await expect(goal).toContainText(`by ${goalDate}`)
  await expect(goal.getByRole('listitem')).toHaveText(['Order food', 'Ask for directions'])
  await expect(goal).toContainText('30–60 min a day')
  await page.getByRole('button', { name: 'Yes, show my path' }).click()

  await expect(page.getByRole('heading', { name: 'Your path · Travel', level: 2 })).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: 'built from your answers' })).toHaveText(
    'SparkBot isn’t available, so this plan is built from your answers.',
  )
  const steps = page.getByRole('list', { name: 'The steps of your plan' })
  await expect(steps).toContainText('Learn · Unit 1 · Pinyin and tones · 拼音')
  await expect(steps).toContainText('matches your goal: Order food.')
  await expect(steps).toContainText('matches your goal: Ask for directions.')
  await expect(steps).toContainText('Your goal · Travel')
  await startMyPath(page)

  // Home on her first day, with the plan.
  await expect(page.getByRole('heading', { name: 'Welcome to MandarinSpark, Nadia', level: 1 })).toBeAttached()
  const homeGoal = page.getByRole('region', { name: 'Your goal' })
  await expect(homeGoal).toContainText(`Travel · by ${goalDate.replace(/ \d{4}$/, '')}`)
  await expect(homeGoal.getByText(/^Week \d+ of \d+ · /)).toHaveText(/^Week 1 of \d+ · just started$/)
  await expect(homeGoal.getByRole('link', { name: 'View my plan' })).toBeVisible()
  const unit1 = page.getByRole('region', { name: 'Pinyin and tones · 拼音' })
  await expect(unit1).toContainText('First in your plan · Learn · Unit 1')
  await expect(unit1.getByRole('link', { name: 'Start Unit 1' })).toBeVisible()
  // A new learner's daily goal is 20 XP.
  const today = page.getByRole('region', { name: 'Today’s plan' })
  await expect(today).toContainText('0 / 20 XP')
  await expect(
    today.getByRole('listitem').filter({ hasText: 'Say hi to Xiaomei' }).getByRole('link', { name: 'Start talking' }),
  ).toBeVisible()
  await expect(page.getByRole('region', { name: 'Build your learning plan' })).toHaveCount(0)
  await expectConfirmEmailBanner(page, email)
})

test('a learner who picks No date gets a plan that follows her daily time', async ({ page }) => {
  await registerStudent(page, 'Siti Aminah', 'nodate')

  await expect(page).toHaveURL(/\/student\/setup$/)
  await page.getByRole('button', { name: 'Let’s start' }).click()
  const next = continueButton(page)
  await answerWhyAndGoal(page, /^Work and career/, ['Greet customers'])

  // No date instead of a day on the calendar; the line under the time says what that means.
  await expect(page.getByRole('heading', { name: 'When, and how much time?', level: 2 })).toBeVisible()
  await expect(next).toBeDisabled()
  const targetDate = page.getByRole('group', { name: 'Target date' })
  await targetDate.getByRole('button', { name: 'No date' }).click()
  await expect(targetDate.getByRole('button', { name: 'No date' })).toHaveAttribute('aria-pressed', 'true')
  await expect(targetDate.getByRole('button', { name: 'Pick a day on the calendar' })).toBeVisible()
  await expect(page.getByText('No date: your plan follows your pace. You can add a date any time.')).toBeVisible()
  await page
    .getByRole('group', { name: 'Time I can give each day' })
    .getByRole('button', { name: 'Under 30 min' })
    .click()
  await next.click()

  // Brand new: she skips the level check and starts from the first lesson.
  await expect(page.getByRole('heading', { name: 'How much Mandarin do you know?', level: 2 })).toBeVisible()
  await page.getByRole('button', { name: 'Brand new? Skip the check and start from the first lesson' }).click()
  await expect(page.getByRole('heading', { name: 'What do you want to get better at?', level: 2 })).toBeVisible()
  // No focus is a balanced plan.
  await page.getByRole('button', { name: 'Build my plan' }).click()

  await expect(page.getByRole('heading', { name: 'Your plan is ready!', level: 2 })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Your Mandarin level' })).toContainText('Pre-HSK (New)')
  const goal = page.getByRole('region', { name: 'Your goal' })
  await expect(goal).toContainText('Work and career')
  await expect(goal).toContainText('No date · at your own pace')
  await expect(goal).toContainText('Under 30 min a day')
  await page.getByRole('button', { name: 'Yes, show my path' }).click()

  await expect(page.getByRole('heading', { name: 'Your path · Work and career', level: 2 })).toBeVisible()
  await expect(page.getByRole('list', { name: 'The steps of your plan' })).toContainText(/No date · target: /)
  await startMyPath(page)

  await expect(page.getByRole('heading', { name: 'Welcome to MandarinSpark, Siti', level: 1 })).toBeAttached()
  const homeGoal = page.getByRole('region', { name: 'Your goal' })
  await expect(homeGoal).toContainText('Work and career · no date')
  // Without a date, the plan's weeks come from her daily time.
  await expect(homeGoal.getByText(/^Week \d+ of \d+ · /)).toHaveText(/^Week 1 of \d+ · just started$/)
})

test('a new learner can set the plan up later and gets Home without a plan', async ({ page }) => {
  const email = await registerStudent(page, 'Farid Ismail', 'later')

  await expect(page).toHaveURL(/\/student\/setup$/)
  await page.getByRole('button', { name: 'Set up later' }).click()
  let dialog = await expectLeaveStep(page, 'Set up your plan later?', 'Set up later')
  await expect(dialog).toContainText('You can build it any time from Home.')
  await dialog.getByRole('button', { name: 'Keep going' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('heading', { name: 'Let’s build your Mandarin plan', level: 2 })).toBeVisible()

  await page.getByRole('button', { name: 'Set up later' }).click()
  dialog = await expectLeaveStep(page, 'Set up your plan later?', 'Set up later')
  await dialog.getByRole('button', { name: 'Set up later' }).click()

  await expect(page).toHaveURL(/\/student\/home$/)
  await expect(page.getByRole('heading', { name: 'Welcome to MandarinSpark, Farid', level: 1 })).toBeAttached()
  await expect(
    page.getByRole('region', { name: 'Build your learning plan' }).getByRole('link', { name: 'Build my plan' }),
  ).toBeVisible()
  await expect(page.getByRole('region', { name: 'Your goal' })).toHaveCount(0)
  await expect(page.getByRole('region', { name: 'Pinyin and tones · 拼音' })).toContainText(
    'Start here · Learn · Unit 1',
  )
  const firstWeek = page.getByRole('region', { name: 'Your first week' })
  await expect(firstWeek.getByRole('group', { name: 'Daily goal' }).getByRole('button')).toHaveText([
    '10 XP',
    '20 XP',
    '30 XP',
  ])
  await expect(firstWeek.getByRole('button', { name: '20 XP' })).toHaveAttribute('aria-pressed', 'true')
  await expectConfirmEmailBanner(page, email)

  // Home no longer sends her to the setup.
  await page.reload()
  await expect(page).toHaveURL(/\/student\/home$/)
  await expect(page.getByRole('region', { name: 'Build your learning plan' })).toBeVisible()
})
