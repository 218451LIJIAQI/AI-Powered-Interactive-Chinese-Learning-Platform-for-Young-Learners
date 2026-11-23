import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test'

import { AMIRA, GOAL_BONUS_XP, numberIn, passRankUp, sidebarStatus, signInAmira, toast } from './studentSupport'
import { API_BASE_URL, apiAuthHeaders, expectFocusMode, expectLeaveStep, sidebar } from './support'

const NEXT_LESSON = 'Initials 1 · b p m f and more'
const LOCKED_LESSON = 'Initials 2 · zh ch sh and more'
const UNIT_1 = 'Pinyin and tones · 拼音'

/** Home's "Today’s plan", with today's XP against the daily goal ("20 / 30 XP"). */
function todaysPlan(page: Page): Locator {
  return page.getByRole('region', { name: 'Today’s plan' })
}

/** Id of a lesson of Amira's Learn path (GET /students/learn/path). */
async function learnLessonId(request: APIRequestContext, title: string): Promise<number> {
  const response = await request.get(`${API_BASE_URL}/students/learn/path`, {
    headers: await apiAuthHeaders(request, AMIRA),
  })
  expect(response.status()).toBe(200)
  type Path = { data: { levels: { units: { lessons: { id: number; title: string }[] }[] }[] } }
  const lessons = ((await response.json()) as Path).data.levels.flatMap((level) =>
    level.units.flatMap((unit) => unit.lessons),
  )
  const lesson = lessons.find((candidate) => candidate.title === title)
  expect(lesson, `"${title}" on the Learn path`).toBeDefined()
  return lesson!.id
}

/** Checks the chosen answer of the question on screen, expects it right and moves on. */
async function checkRight(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Check', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Nice!' })).toBeVisible()
  await page.getByRole('button', { name: 'Continue', exact: true }).click()
}

/** Answers a multiple-choice question of the lesson player (QuestionStage) with one of its options. */
async function chooseAnswer(page: Page, instruction: string, option: string): Promise<void> {
  const question = page.getByRole('region', { name: instruction })
  await question.getByRole('list', { name: 'Answers' }).getByRole('button', { name: option, exact: true }).click()
  await checkRight(page)
}

/** Answers a sentence-building question by clicking its word tiles in order. */
async function buildSentence(page: Page, instruction: string, tiles: string[]): Promise<void> {
  const question = page.getByRole('region', { name: instruction })
  for (const tile of tiles) {
    await question.getByRole('group', { name: 'Word tiles' }).getByRole('button', { name: tile, exact: true }).click()
  }
  await expect(question.getByRole('group', { name: 'Your sentence' }).getByRole('button')).toHaveText(tiles)
  await checkRight(page)
}

/** The last step of a lesson: "Listen, then say it", on a phrase from the lesson. */
function sayStep(page: Page): Locator {
  return page.getByRole('region', { name: 'Listen, then say it' })
}

/** A level of the Learn map: "Level 3, Initials 1 · b p m f and more, current". */
function mapLevel(scope: Page | Locator, number: number, title: string, state: string): Locator {
  return scope.getByRole('button', { name: `Level ${number}, ${title}, ${state}`, exact: true })
}

test.describe('Home and Learn', () => {
  test.describe.configure({ mode: 'serial' })

  test('Home greets Amira with her goal, her next lesson and today’s plan', async ({ page }) => {
    await signInAmira(page)
    await expect(page.getByRole('heading', { name: 'Welcome back, Amira', level: 1 })).toBeAttached()

    // The goal strip: each number once, the week in words and the plan's progress as a bar, no percent in text.
    const goal = page.getByRole('region', { name: 'Your goal' })
    await expect(goal).toContainText(/Travel to Shanghai · by \w{3} \d{1,2} \w{3,4}/)
    await expect(goal.getByRole('progressbar', { name: /^Plan progress: \d+%$/ })).toBeVisible()
    await expect(goal.getByText(/^Week \d+ of \d+ · /)).toHaveText('Week 2 of 10 · on track')
    await expect(goal).not.toContainText('%')
    await expect(goal.getByRole('link', { name: 'View my plan' })).toBeVisible()

    const next = page.getByRole('region', { name: NEXT_LESSON })
    await expect(next).toContainText('Next in your plan · Learn · Unit 1 · Pinyin and tones')
    await expect(next).toContainText('About 4 minutes · +10 XP')
    // Unit 1 has 7 lessons; its optional bonus chest is not one of them.
    const stops = next.getByRole('list', { name: 'Lessons of Unit 1' })
    await expect(stops.getByRole('listitem')).toHaveCount(7)
    await expect(stops.getByRole('listitem', { name: 'Lesson 2: done' })).toBeVisible()
    await expect(stops.getByRole('listitem', { name: 'Lesson 3: next' })).toBeVisible()
    await expect(stops.getByRole('listitem', { name: 'Lesson 4: not open yet' })).toBeVisible()
    await expect(next.getByRole('figure')).toContainText('You’ll learn: “dad”')
    await expect(next.getByRole('link', { name: 'Start lesson' })).toBeVisible()

    const today = todaysPlan(page)
    await expect(today).toContainText('20 / 30 XP')
    await expect(today.getByRole('progressbar', { name: 'Today’s XP: 67%' })).toBeVisible()
    const items = today.getByRole('list', { name: 'Today’s plan' }).getByRole('listitem')
    await expect(items.filter({ hasText: 'Fix 6 mistakes' }).getByRole('link', { name: 'Start review' })).toBeVisible()
    await expect(
      items.filter({ hasText: 'Talk with Xiaomei' }).getByRole('link', { name: 'Start talking' }),
    ).toBeVisible()
  })

  test('Start lesson opens the lesson in focus mode, and closing it asks before leaving', async ({ page }) => {
    await signInAmira(page)
    await page.getByRole('region', { name: NEXT_LESSON }).getByRole('link', { name: 'Start lesson' }).click()

    await expect(page).toHaveURL(/\/student\/learn\/lessons\/\d+$/)
    const lessonUrl = page.url()
    await expect(page.getByRole('heading', { name: NEXT_LESSON, level: 1 })).toBeVisible()
    await expectFocusMode(page)
    // Something answered, so leaving would lose it (a right answer leaves no mistake behind).
    await page.getByRole('list', { name: 'Answers' }).getByRole('button', { name: 'bà', exact: true }).click()
    await page.getByRole('button', { name: 'Check', exact: true }).click()
    await expect(page.getByRole('status').filter({ hasText: 'Nice!' })).toBeVisible()

    await page.getByRole('button', { name: 'Leave the lesson' }).click()
    let dialog = await expectLeaveStep(page, 'Leave this lesson?', 'Leave')
    await expect(dialog).toContainText('If you leave now, your answers in this lesson won’t be saved.')
    await dialog.getByRole('button', { name: 'Keep going' }).click()
    await expect(dialog).toBeHidden()
    await expect(page).toHaveURL(lessonUrl)
    await expect(page.getByRole('status').filter({ hasText: 'Nice!' })).toBeVisible()

    await page.getByRole('button', { name: 'Leave the lesson' }).click()
    dialog = await expectLeaveStep(page, 'Leave this lesson?', 'Leave')
    await dialog.getByRole('button', { name: 'Leave', exact: true }).click()
    await expect(page).toHaveURL(/\/student\/learn$/)
    await expect(sidebar(page)).toBeVisible()
  })

  test('Learn maps the units and the current level, and a locked lesson cannot be opened', async ({
    page,
    request,
  }) => {
    await signInAmira(page)
    await sidebar(page).getByRole('link', { name: 'Learn' }).click()

    await expect(page).toHaveURL(/\/student\/learn$/)
    await expect(page.getByRole('heading', { name: 'HSK 1 (Basic)', level: 2 })).toBeVisible()
    await expect(page.getByText('You are here · Unit 1')).toBeVisible()
    const unit1 = page.getByRole('region', { name: UNIT_1 })
    await expect(unit1).toContainText('2 of 7')
    const levels = unit1.getByRole('list', { name: 'Lessons of Unit 1' })
    await expect(mapLevel(levels, 1, 'The four tones · 声调', 'done')).toBeVisible()
    await expect(mapLevel(levels, 2, 'Simple finals · a o e i u ü', 'done')).toBeVisible()
    await expect(mapLevel(levels, 3, NEXT_LESSON, 'current')).toBeVisible()
    await expect(mapLevel(levels, 4, LOCKED_LESSON, 'locked')).toBeVisible()
    await expect(levels.getByRole('button', { name: 'Bonus chest (optional), locked' })).toBeVisible()
    await expect(levels.getByRole('button', { name: 'Unit 1 review, locked' })).toBeVisible()
    const unit2 = page.getByRole('region', { name: 'Characters and first words · 汉字' })
    await expect(unit2).toContainText('Unit 2')
    await expect(unit2).toContainText('Finish Unit 1 to unlock')
    await expect(page.getByRole('heading', { name: 'HSK 2 (Survival)', level: 2 })).toBeVisible()

    // The side column: the next goal, today's goal and the mistakes to practise.
    const rail = page.getByRole('complementary', { name: 'Your progress on the path' })
    await expect(rail.getByRole('heading', { level: 2 })).toHaveText(['Next goal', 'Practise what you learned'])
    await expect(rail).toContainText('Unlock Unit 2')
    await expect(rail).toContainText('5 lessons to go.')
    await expect(rail).toContainText('20 / 30 XP')
    await expect(rail.getByRole('progressbar', { name: 'Daily goal: 67%' })).toBeVisible()
    await expect(rail).toContainText('One more lesson reaches today’s goal.')
    await expect(rail).toContainText('6 mistakes waiting.')
    await expect(rail.getByRole('link', { name: 'See mistakes' })).toHaveAttribute('href', '/student/practice/mistakes')

    // A locked level says what to finish first and offers no Start link.
    const lockedLevel = mapLevel(levels, 4, LOCKED_LESSON, 'locked')
    await lockedLevel.focus()
    await page.keyboard.press('Enter')
    const card = unit1.getByRole('dialog', { name: LOCKED_LESSON })
    await expect(card).toBeFocused()
    await expect(card).toContainText(`Finish “${NEXT_LESSON}” to unlock this lesson.`)
    await expect(card.getByRole('link')).toHaveCount(0)
    await page.keyboard.press('Escape')
    await expect(lockedLevel).toBeFocused()
    await page.keyboard.press('Enter')
    await page.keyboard.press('Tab')
    await expect(card).toBeHidden()

    // Its address does not open it either: the server refuses it and the player says why.
    const lockedId = await learnLessonId(request, LOCKED_LESSON)
    const refused = page.waitForResponse((response) =>
      response.url().endsWith(`/api/v1/students/learn/lessons/${lockedId}`),
    )
    await page.goto(`/student/learn/lessons/${lockedId}`)
    expect((await refused).status()).toBe(409)
    await expect(page.getByRole('heading', { name: 'This lesson cannot be opened' })).toBeVisible()
    await expect(page.getByRole('alert')).toContainText(
      'This lesson is still locked. Finish the lessons before it first.',
    )
    await page.getByRole('link', { name: 'Go to Learn' }).click()
    await expect(page).toHaveURL(/\/student\/learn$/)
  })

  test('the current lesson starts from its level card on the map', async ({ page }) => {
    await signInAmira(page, '/student/learn')
    await mapLevel(page, 3, NEXT_LESSON, 'current').click()
    const card = page.getByRole('dialog', { name: NEXT_LESSON })
    await expect(card).toContainText('About 4 min')
    await card.getByRole('link', { name: 'Start · +10 XP' }).click()

    await expect(page).toHaveURL(/\/student\/learn\/lessons\/\d+$/)
    await expect(page.getByRole('heading', { name: NEXT_LESSON, level: 1 })).toBeVisible()
  })

  test('Amira finishes her next lesson, and her XP and today’s goal go up', async ({ page }) => {
    await signInAmira(page)
    const statusBadge = sidebarStatus(page)
    await expect(statusBadge).toContainText(/\d+-day streak/)
    const status = await statusBadge.innerText()
    const xpBefore = await numberIn(status, /(\d+) XP/)
    const streakBefore = await numberIn(status, /(\d+)-day streak/)
    const todayBefore = await numberIn(todaysPlan(page).innerText(), /(\d+) \/ 30 XP/)

    await page.getByRole('region', { name: NEXT_LESSON }).getByRole('link', { name: 'Start lesson' }).click()
    await expect(page.getByRole('heading', { name: NEXT_LESSON, level: 1 })).toBeVisible()
    // The seeded questions of the lesson, in order (config/seed/LearnPathContent).
    await chooseAnswer(page, 'Select the correct pinyin', 'bà')
    await buildSentence(page, 'Click what you hear', ['mā', 'pà', 'dú', 'hē'])
    await chooseAnswer(page, 'Click the word you hear', 'pà')
    await chooseAnswer(page, 'Select the correct pinyin', 'dà')
    await chooseAnswer(page, 'Click the word you hear', 'dú')
    await chooseAnswer(page, 'Select the correct pinyin', 'hē')

    // The last step, on the Chinese of the last question: not graded, and Continue moves on to the result.
    const say = sayStep(page)
    await expect(say).toContainText('喝')
    await expect(say).toContainText('hē · to drink')
    await expect(say).toContainText('Not graded.')
    await page.getByRole('button', { name: 'Continue', exact: true }).click()

    const lessonXp = 10
    const goalReached = todayBefore < 30 && todayBefore + lessonXp >= 30
    const earned = lessonXp + (goalReached ? GOAL_BONUS_XP : 0)
    const expectedStreak = goalReached ? streakBefore + 1 : streakBefore
    await expect(page.getByRole('heading', { name: 'Lesson complete!', level: 2 })).toBeVisible()
    await expect(page.getByRole('term')).toHaveText(['XP earned', 'accuracy'])
    // The lesson's 10 XP, plus GOAL_BONUS_XP when completing it reaches today's 30 XP goal.
    await expect(page.getByRole('definition')).toHaveText([`+${earned}`, '100%'])
    await expect(page.getByText(`${expectedStreak}-day streak · today’s goal reached`)).toBeVisible()
    await expect(page.getByText('Unlocked')).toBeVisible()
    await expect(page.getByText(`Next up: ${LOCKED_LESSON}`)).toBeVisible()

    await page.getByRole('button', { name: 'Continue', exact: true }).click()
    await passRankUp(page, xpBefore, earned)
    await expect(page).toHaveURL(/\/student\/learn$/)
    await expect(toast(page, 'Lesson complete')).toContainText(
      `Lesson complete: +${earned} XP. “${LOCKED_LESSON}” is unlocked.`,
    )
    const levels = page.getByRole('region', { name: UNIT_1 }).getByRole('list', { name: 'Lessons of Unit 1' })
    await expect(mapLevel(levels, 3, NEXT_LESSON, 'done')).toBeVisible()
    await expect(mapLevel(levels, 4, LOCKED_LESSON, 'current')).toBeVisible()
    const rail = page.getByRole('complementary', { name: 'Your progress on the path' })
    await expect(rail).toContainText(`${todayBefore + earned} / 30 XP`)
    await expect(rail).toContainText('Goal reached')
    await expect(sidebarStatus(page)).toContainText(`${xpBefore + earned} XP · ${expectedStreak}-day streak`)

    await sidebar(page).getByRole('link', { name: 'Home' }).click()
    await expect(todaysPlan(page)).toContainText(`${todayBefore + earned} / 30 XP`)
    await expect(todaysPlan(page).getByRole('progressbar', { name: 'Today’s XP: 100%' })).toBeVisible()
    await expect(
      page.getByRole('region', { name: LOCKED_LESSON }).getByRole('link', { name: 'Start lesson' }),
    ).toBeVisible()
  })

  test('the Say step is not graded, and a blocked microphone does not stop it', async ({ page }) => {
    await signInAmira(page, '/student/learn')
    const xpBefore = await numberIn(sidebarStatus(page).innerText(), /(\d+) XP/)
    await mapLevel(page, 1, 'The four tones · 声调', 'done').click()
    await page
      .getByRole('dialog', { name: 'The four tones · 声调' })
      .getByRole('link', { name: 'Practise again' })
      .click()

    await chooseAnswer(page, 'Select the correct pinyin', 'mā')
    await buildSentence(page, 'Click what you hear', ['mā', 'mǎ', 'ma'])
    await chooseAnswer(page, 'Click the word you hear', 'mǎ')
    await chooseAnswer(page, 'Select the correct pinyin', 'ma')
    await chooseAnswer(page, 'Click the word you hear', 'mā')
    await chooseAnswer(page, 'Select the correct pinyin', 'mǎ')

    const say = sayStep(page)
    await expect(say).toContainText('马')
    await expect(page.getByRole('button', { name: 'See my result' })).toHaveCount(0)
    // Refuse the capture request after the in-app prompt, regardless of the machine's audio devices.
    await say.getByRole('button', { name: 'Start recording' }).click()
    const ask = page.getByRole('dialog', { name: 'Use your microphone?' })
    await expect(ask.getByRole('button')).toContainText(['Not now', 'Allow microphone'])
    await page.evaluate(() => {
      if (!('mediaDevices' in navigator)) throw new Error('The browser has no media capture API')
      Object.defineProperty(navigator.mediaDevices, 'getUserMedia', {
        value: async () => {
          throw new DOMException('Microphone permission denied.', 'NotAllowedError')
        },
      })
    })
    await ask.getByRole('button', { name: 'Allow microphone' }).click()
    await expect(say.getByRole('alert')).toContainText('Microphone blocked.')
    await expect(say.getByRole('alert')).toContainText('say it out loud and continue')
    await page.getByRole('button', { name: 'Continue', exact: true }).click()

    // A lesson practised again is checked as usual but earns nothing the second time.
    await expect(page.getByRole('heading', { name: 'Lesson complete!', level: 2 })).toBeVisible()
    await expect(page.getByRole('definition').first()).toHaveText('+0')
    await expect(page.getByText('You earned this lesson’s XP the first time.')).toBeVisible()
    await page.getByRole('button', { name: 'Continue', exact: true }).click()
    await expect(page).toHaveURL(/\/student\/learn$/)
    await expect(toast(page, 'Lesson complete')).toContainText('Lesson complete.')
    await expect(sidebarStatus(page)).toContainText(`${xpBefore} XP`)
  })
})
