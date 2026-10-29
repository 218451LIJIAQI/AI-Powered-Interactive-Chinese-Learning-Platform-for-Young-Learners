import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import { API_BASE_URL, apiToken, bearer, expectFocusMode, expectLeaveStep, sidebar, signIn } from './support'

// Amira Hassan, the demo student (config/seed: DemoLearnSeeder, DemoPlanSeeder, DemoClassesSeeder): Unit 1 of the
// Learn path with "Hello and goodbye" and "Polite phrases" done, 20 of her 30 XP today, a 7-day streak, six mistakes
// waiting (four from Learn, two from Practice), the plan "Travel to Shanghai", and Li Wei's "Mandarin for Beginners"
// with a live session booked for later today. The tests run in file order on one backend: one finishes her next
// lesson, one reviews a flashcard set and one joins a class.
const AMIRA = E2E_ACCOUNTS.student
const NEXT_LESSON = 'Introduce yourself'
const LOCKED_LESSON = 'Numbers 1–10'
const UNIT_1 = 'Say hello · 你好'
const MY_CLASS = 'Mandarin for Beginners'
const CLASS_TO_JOIN = 'Chinese Characters 101'

test.beforeEach(async ({ page }) => {
  await signIn(page, AMIRA)
  await expect(page).toHaveURL(/\/student\/home$/)
})

/** The first number a pattern captures in a text on the page. */
async function numberIn(text: Promise<string>, pattern: RegExp): Promise<number> {
  const match = pattern.exec(await text)
  expect(match, `${pattern} in the page text`).not.toBeNull()
  return Number(match![1])
}

/** The sidebar's status card with the total XP ("Level 3 · Regular Learner 240 XP · 7-day streak"). */
function sidebarStatus(page: Page) {
  return page.getByRole('link', { name: /\d+ XP · / })
}

/** Home's "Today’s plan", with today's XP against the daily goal ("20 / 30 XP"). */
function todaysPlan(page: Page) {
  return page.getByRole('region', { name: 'Today’s plan' })
}

/** Id of a lesson of Amira's Learn path (GET /students/learn/path). */
async function learnLessonId(request: APIRequestContext, title: string): Promise<number> {
  const token = await apiToken(request, AMIRA)
  const response = await request.get(`${API_BASE_URL}/students/learn/path`, { headers: bearer(token) })
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
async function checkRight(page: Page, continueLabel = 'Continue'): Promise<void> {
  await page.getByRole('button', { name: 'Check', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Nice!' })).toBeVisible()
  await page.getByRole('button', { name: continueLabel, exact: true }).click()
}

/** Answers a multiple-choice question of the lesson player (QuestionStage) with one of its options. */
async function chooseAnswer(page: Page, instruction: string, option: string): Promise<void> {
  const question = page.getByRole('region', { name: instruction })
  await question.getByRole('list', { name: 'Answers' }).getByRole('button', { name: option, exact: true }).click()
  await checkRight(page)
}

/** Answers a sentence-building question by clicking its word tiles in order. */
async function buildSentence(page: Page, instruction: string, tiles: string[], continueLabel?: string): Promise<void> {
  const question = page.getByRole('region', { name: instruction })
  for (const tile of tiles) {
    await question.getByRole('group', { name: 'Word tiles' }).getByRole('button', { name: tile, exact: true }).click()
  }
  await expect(question.getByRole('group', { name: 'Your sentence' }).getByRole('button')).toHaveText(tiles)
  await checkRight(page, continueLabel)
}

test.describe('Home and Learn', () => {
  test('Home greets Amira with her goal, her next lesson and today’s plan', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Welcome back, Amira', level: 1 })).toBeVisible()
    await expect(page.getByRole('list', { name: 'The last seven days' }).getByRole('listitem')).toHaveCount(7)

    const goal = page.getByRole('region', { name: 'Your goal' })
    await expect(goal).toContainText('Travel to Shanghai')
    await expect(goal.getByRole('progressbar', { name: /^Plan progress: \d+%$/ })).toBeVisible()
    await expect(goal).toContainText(/\d+% of your plan · week \d+ of \d+/)
    await expect(goal.getByRole('link', { name: 'View my plan' })).toBeVisible()

    const next = page.getByRole('region', { name: NEXT_LESSON })
    await expect(next).toContainText('Next in your plan · Learn · Unit 1 · Say hello')
    await expect(next).toContainText('Lesson 3 of 7 · about 4 minutes · +10 XP')
    const stops = next.getByRole('list', { name: 'Lessons of Unit 1' })
    await expect(stops.getByRole('listitem', { name: 'Lesson 2: done' })).toBeVisible()
    await expect(stops.getByRole('listitem', { name: 'Lesson 3: next' })).toBeVisible()
    await expect(stops.getByRole('listitem', { name: 'Lesson 4: not open yet' })).toBeVisible()
    await expect(next.getByRole('link', { name: 'Continue lesson' })).toBeVisible()

    const today = todaysPlan(page)
    await expect(today).toContainText('20 / 30 XP')
    await expect(today).toContainText('10 more XP reaches today’s goal and keeps your 7-day streak.')
    const items = today.getByRole('list', { name: 'Today’s plan' }).getByRole('listitem')
    await expect(items.filter({ hasText: NEXT_LESSON })).toContainText('Learn · lesson 3 of 7')
    await expect(items.filter({ hasText: 'Fix 6 mistakes' }).getByRole('link', { name: 'Start review' })).toBeVisible()
    await expect(
      items.filter({ hasText: 'Talk with Xiaomei' }).getByRole('link', { name: 'Start talking' }),
    ).toBeVisible()
  })

  test('Continue lesson opens the lesson in focus mode, and closing it asks before leaving', async ({ page }) => {
    await page.getByRole('region', { name: NEXT_LESSON }).getByRole('link', { name: 'Continue lesson' }).click()

    await expect(page).toHaveURL(/\/student\/learn\/lessons\/\d+$/)
    const lessonUrl = page.url()
    await expect(page.getByRole('heading', { name: NEXT_LESSON, level: 1 })).toBeVisible()
    await expectFocusMode(page)
    // Something answered, so leaving would lose it (a right answer leaves no mistake behind).
    await page.getByRole('list', { name: 'Answers' }).getByRole('button', { name: 'I, me' }).click()
    await page.getByRole('button', { name: 'Check', exact: true }).click()
    await expect(page.getByRole('status').filter({ hasText: 'Nice!' })).toBeVisible()

    await page.getByRole('button', { name: 'Leave the lesson' }).click()
    let dialog = await expectLeaveStep(page, 'Leave this lesson?', 'Leave', 'Keep going')
    await expect(dialog).toContainText('If you leave now, your progress in this lesson won’t be saved.')
    await dialog.getByRole('button', { name: 'Keep going' }).click()
    await expect(dialog).toBeHidden()
    await expect(page).toHaveURL(lessonUrl)
    await expect(page.getByRole('status').filter({ hasText: 'Nice!' })).toBeVisible()

    await page.getByRole('button', { name: 'Leave the lesson' }).click()
    dialog = await expectLeaveStep(page, 'Leave this lesson?', 'Leave', 'Keep going')
    await dialog.getByRole('button', { name: 'Leave', exact: true }).click()
    await expect(page).toHaveURL(/\/student\/learn$/)
    await expect(sidebar(page)).toBeVisible()
  })

  test('Learn shows the units and the current lesson, and a locked lesson cannot be opened', async ({
    page,
    request,
  }) => {
    await sidebar(page).getByRole('link', { name: 'Learn' }).click()

    await expect(page).toHaveURL(/\/student\/learn$/)
    await expect(page.getByRole('heading', { name: 'Level 1 · Beginner 1 · HSK 1', level: 2 })).toBeVisible()
    const unit1 = page.getByRole('region', { name: UNIT_1 })
    await expect(unit1).toContainText('2 of 7')
    for (const stop of [
      'Hello and goodbye (done)',
      'Polite phrases (done)',
      `${NEXT_LESSON} (ready to start)`,
      `${LOCKED_LESSON} (locked)`,
      'Unit 1 review (locked)',
    ]) {
      await expect(unit1.getByRole('button', { name: stop })).toBeVisible()
    }
    const unit2 = page.getByRole('region', { name: 'Food and drink · 吃喝' })
    await expect(unit2).toContainText('Unit 2 · Locked')
    await expect(unit2).toContainText('Finish Unit 1 to unlock')
    const rail = page.getByRole('complementary', { name: 'Your progress on the path' })
    await expect(rail).toContainText('Unlock Unit 2')
    await expect(rail).toContainText(/2 of \d+ lessons/)
    await expect(rail.getByRole('progressbar', { name: /^Daily goal: \d+%$/ })).toBeVisible()

    // A locked stop says what to finish first and offers no Start link.
    await unit1.getByRole('button', { name: `${LOCKED_LESSON} (locked)` }).click()
    const card = unit1.getByRole('dialog', { name: LOCKED_LESSON })
    await expect(card).toContainText(`Finish “${NEXT_LESSON}” to unlock this lesson.`)
    await expect(card.getByRole('link')).toHaveCount(0)

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
    await page.getByRole('link', { name: 'Back to the path' }).click()
    await expect(page).toHaveURL(/\/student\/learn$/)
  })

  test('the current lesson starts from its stop card on the path', async ({ page }) => {
    await page.goto('/student/learn')
    await page.getByRole('button', { name: `${NEXT_LESSON} (ready to start)` }).click()
    const card = page.getByRole('dialog', { name: NEXT_LESSON })
    await expect(card).toContainText('Lesson 3 of 7 · Unit 1 · about 4 min')
    await card.getByRole('link', { name: 'Start · +10 XP' }).click()

    await expect(page).toHaveURL(/\/student\/learn\/lessons\/\d+$/)
    await expect(page.getByRole('heading', { name: NEXT_LESSON, level: 1 })).toBeVisible()
  })

  test('Amira finishes her next lesson, and her XP and today’s goal go up', async ({ page }) => {
    const xpBefore = await numberIn(sidebarStatus(page).innerText(), /(\d+) XP/)
    const todayBefore = await numberIn(todaysPlan(page).innerText(), /(\d+) \/ 30 XP/)

    await page.getByRole('link', { name: 'Continue lesson' }).click()
    await expect(page.getByRole('heading', { name: NEXT_LESSON, level: 1 })).toBeVisible()
    // The seeded questions of the lesson, in order (config/seed/LearnQuestionGenerator).
    await chooseAnswer(page, 'Select the correct meaning', 'I, me')
    await chooseAnswer(page, 'Click the word you hear', '叫 jiào')
    await chooseAnswer(page, 'Choose the pinyin', 'míngzi')
    await chooseAnswer(page, 'Which one means “what”?', '什么 shénme')
    await chooseAnswer(page, 'Select the correct meaning', 'My name is Xiaomei.')
    await buildSentence(page, 'Click what you hear', ['你', '叫', '什么', '名字'])
    await buildSentence(page, 'Say it in Chinese', ['我', '是', '学生'], 'See my result')

    await expect(page.getByRole('heading', { name: 'Lesson complete!', level: 2 })).toBeVisible()
    await expect(page.getByText(`${NEXT_LESSON} · Unit 1, lesson 3`)).toBeVisible()
    await expect(page.getByRole('term')).toHaveText(['XP earned', 'accuracy', 'time'])
    await expect(page.getByRole('definition').nth(1)).toHaveText('100%')
    // The lesson's 10 XP, and 5 more for reaching today's goal with it.
    const earned = await numberIn(page.getByRole('definition').first().innerText(), /^\+(\d+)$/)
    expect(earned).toBe(15)
    await expect(page.getByText(`today’s goal reached (${todayBefore + earned} / 30 XP)`)).toBeVisible()
    await expect(page.getByText(`Next up: ${LOCKED_LESSON}`)).toBeVisible()

    await page.getByRole('button', { name: 'Continue', exact: true }).click()
    await expect(page).toHaveURL(/\/student\/learn$/)
    await expect(page.getByRole('status').filter({ hasText: 'Lesson complete' })).toContainText(
      `“${NEXT_LESSON}” done: +10 XP. “${LOCKED_LESSON}” is unlocked.`,
    )
    const unit1 = page.getByRole('region', { name: UNIT_1 })
    await expect(unit1.getByRole('button', { name: `${NEXT_LESSON} (done)` })).toBeVisible()
    await expect(unit1.getByRole('button', { name: `${LOCKED_LESSON} (ready to start)` })).toBeVisible()
    await expect(sidebarStatus(page)).toContainText(`${xpBefore + earned} XP`)

    await sidebar(page).getByRole('link', { name: 'Home' }).click()
    await expect(todaysPlan(page)).toContainText(`${todayBefore + earned} / 30 XP`)
    await expect(todaysPlan(page)).toContainText('Today’s goal reached. Your streak is safe.')
    await expect(
      page.getByRole('region', { name: LOCKED_LESSON }).getByRole('link', { name: 'Continue lesson' }),
    ).toBeVisible()
  })
})

test.describe('Practice', () => {
  test('a flashcard set chosen in Practice is reviewed card by card and shows its result', async ({ page }) => {
    await sidebar(page)
      .getByRole('link', { name: /^Practice/ })
      .click()
    // The Practice room: the activities, each a link to its sets (her mistakes come first, see below).
    const activities = page.getByRole('navigation', { name: 'Practice activities' })
    await expect(activities.getByRole('link')).toHaveText([
      'Flashcards',
      'Quizzes',
      'Listening',
      'Tracing',
      'Drag & Drop',
      'Matching',
      'Story',
    ])
    await activities.getByRole('link', { name: 'Flashcards' }).click()
    await expect(page).toHaveURL(/\/student\/practice\/flashcards$/)

    const sets = page.getByRole('group', { name: '5 sets of this activity' })
    await sets.getByRole('link', { name: 'Travel Words' }).click()
    await expect(page).toHaveURL(/\/student\/practice\/flashcards\?set=\d+$/)
    await expect(page.getByRole('heading', { name: 'Travel Words', level: 2 })).toBeVisible()
    await expect(page.getByText('Flashcards · 6 cards · HSK 2 · +10 XP')).toBeVisible()
    await expect(page.getByText('Card 1 of 6')).toBeVisible()
    const markReviewed = page.getByRole('button', { name: 'Mark as reviewed' })
    await expect(markReviewed).toBeDisabled()
    await page.getByRole('button', { name: 'Click to flip' }).click()
    await expect(page.getByRole('button', { name: 'Flip back' })).toBeVisible()
    for (let card = 2; card <= 6; card += 1) {
      await page.getByRole('button', { name: 'Next', exact: true }).click()
      await expect(page.getByText(`Card ${card} of 6`)).toBeVisible()
    }
    await expect(page.getByRole('list', { name: '6 of 6 cards seen' })).toBeVisible()
    await markReviewed.click()

    await expect(page.getByRole('heading', { name: 'Set reviewed', level: 2 })).toBeVisible()
    await expect(page.getByText('All 6 cards seen.')).toBeVisible()
    await expect(page.getByText('+10 XP', { exact: true })).toBeVisible()
    // The set is ticked off, and the result stays after the list reloads.
    await expect(sets.getByRole('link', { name: 'Travel Words (Done)' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Set reviewed', level: 2 })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Practise again' })).toBeVisible()
  })

  test('finishing the suggested flashcard set keeps its result on screen', async ({ page }) => {
    await page.goto('/student/practice/flashcards')
    // The first set she has not done yet is opened for her.
    await expect(page.getByRole('heading', { name: 'Family', level: 2 })).toBeVisible()
    for (let card = 2; card <= 6; card += 1) {
      await page.getByRole('button', { name: 'Next', exact: true }).click()
    }
    await page.getByRole('button', { name: 'Mark as reviewed' }).click()

    await expect(page.getByRole('heading', { name: 'Set reviewed', level: 2 })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Family (Done)' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Set reviewed', level: 2 })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Family', level: 2 })).toBeVisible()
  })

  test('Mistakes lists her waiting mistakes, and the review shows the right answer', async ({ page }) => {
    await page.goto('/student/practice')
    // Waiting mistakes are the room's first call: one review fixes them, or the list shows them first.
    const call = page.getByRole('region', { name: '6 mistakes to fix' })
    await expect(call.getByRole('link', { name: 'Start review' })).toBeVisible()
    await call.getByRole('link', { name: 'See the list' }).click()

    await expect(page).toHaveURL(/\/student\/practice\/mistakes$/)
    const mistakes = page.getByRole('region', { name: '6 mistakes to fix' })
    await expect(mistakes.getByRole('listitem')).toHaveCount(6)
    const filters = mistakes.getByRole('group', { name: 'Show mistakes from' })
    await expect(filters.getByRole('button')).toHaveText(['All 6', 'Learn 4', 'Practice 2'])
    await filters.getByRole('button', { name: 'Practice 2' }).click()
    await expect(filters.getByRole('button', { name: 'Practice 2' })).toHaveAttribute('aria-pressed', 'true')
    await expect(mistakes.getByRole('listitem')).toHaveCount(2)
    await expect(mistakes.getByRole('listitem').filter({ hasText: 'What does 菜单 mean?' })).toContainText(
      'You answered Bill · correct: Menu',
    )

    await mistakes.getByRole('link', { name: 'Start review' }).click()
    await expect(page).toHaveURL(/\/student\/practice\/review$/)
    await expectFocusMode(page)
    await expect(page.getByRole('heading', { name: 'Mistakes review', level: 1 })).toBeVisible()
    await expect(page.getByText('Mistakes review · 1 of 6')).toBeVisible()
    // A skipped answer counts as wrong: the review shows the right one (nothing is cleared until the review ends).
    await page.getByRole('button', { name: 'Skip' }).click()
    await expect(page.getByRole('status').filter({ hasText: 'Correct answer:' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Continue', exact: true })).toBeFocused()
  })

  test('leaving the Mistakes review asks first, in words', async ({ page }) => {
    await page.goto('/student/practice/review')
    await page.getByRole('button', { name: 'Skip' }).click()
    await expect(page.getByRole('status').filter({ hasText: 'Correct answer:' })).toBeVisible()

    await page.getByRole('button', { name: 'Leave the review' }).click()
    const dialog = page.getByRole('alertdialog', { name: /^Leave (the|this) review\?$/ })
    await expect(dialog).toBeVisible()
    await expect(dialog).not.toContainText('learn.')
    await expect(dialog.getByRole('button')).toHaveText(['Leave', 'Keep going'])
    await dialog.getByRole('button', { name: 'Leave', exact: true }).click()
    await expect(page).toHaveURL(/\/student\/practice\/mistakes$/)
    await expect(page.getByRole('region', { name: '6 mistakes to fix' })).toBeVisible()
  })
})

test.describe('Classes', () => {
  /** A class card of My classes. */
  function myClass(page: Page, title: string) {
    return page.getByRole('article').filter({ has: page.getByRole('heading', { name: title, level: 2 }) })
  }

  test('My classes lists Mandarin for Beginners with her progress and next step', async ({ page }) => {
    await sidebar(page).getByRole('link', { name: 'Classes' }).click()

    await expect(page).toHaveURL(/\/student\/classes$/)
    await expect(page.getByRole('heading', { name: 'My classes', level: 1 })).toBeVisible()
    const sections = page.getByRole('navigation', { name: 'Classes sections' })
    await expect(sections.getByRole('link', { name: 'My classes 1' })).toHaveAttribute('aria-current', 'page')
    const card = myClass(page, MY_CLASS)
    await expect(card).toContainText('Li Wei')
    await expect(card).toContainText('2 units · 5 lessons')
    await expect(card.getByRole('progressbar', { name: 'Class progress: 40%' })).toBeVisible()
    await expect(card).toContainText('2 of 5 lessons · 40%')
    await expect(card.getByRole('link', { name: 'Continue: Greetings' })).toBeVisible()
    await expect(card.getByRole('link', { name: 'Open class' })).toBeVisible()
  })

  test('Find classes lets her join a free class, which then appears in My classes', async ({ page }) => {
    await page.goto('/student/classes')
    const sections = page.getByRole('navigation', { name: 'Classes sections' })
    await sections.getByRole('link', { name: /^Find classes/ }).click()

    await expect(page).toHaveURL(/\/student\/classes\/find$/)
    await expect(page.getByRole('heading', { name: /^\d+ classes matched$/ })).toBeVisible()
    // Her own class is not offered again.
    await expect(page.getByRole('link', { name: `View class ${MY_CLASS}` })).toHaveCount(0)
    await page.getByRole('link', { name: `View class ${CLASS_TO_JOIN}` }).click()

    await expect(page).toHaveURL(/\/student\/classes\/\d+$/)
    await expect(page.getByRole('heading', { name: CLASS_TO_JOIN, level: 1 })).toBeVisible()
    await expect(page.getByText('Free to join')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Unit 1: Strokes', level: 2 })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Open Basic Strokes' })).toHaveCount(0)
    await page.getByRole('button', { name: 'Join class' }).click()

    await expect(page.getByRole('status').filter({ hasText: `You joined ${CLASS_TO_JOIN}.` })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Open Basic Strokes' })).toBeVisible()
    await page.getByRole('navigation', { name: 'Breadcrumb' }).getByRole('link', { name: 'Classes' }).click()
    await expect(page).toHaveURL(/\/student\/classes$/)
    await expect(sections.getByRole('link', { name: 'My classes 2' })).toBeVisible()
    const card = myClass(page, CLASS_TO_JOIN)
    await expect(card).toContainText('John Lim')
    await expect(card).toContainText('0 of 4 lessons · 0%')
    await expect(card.getByRole('link', { name: 'Continue: Basic Strokes' })).toBeVisible()
    await expect(myClass(page, MY_CLASS)).toBeVisible()
  })

  test('a class opens its units, a lesson, a lesson activity and the lesson quiz', async ({ page }) => {
    await page.goto('/student/classes')
    await myClass(page, MY_CLASS).getByRole('link', { name: 'Open class' }).click()

    await expect(page).toHaveURL(/\/student\/classes\/\d+$/)
    await expect(page.getByRole('heading', { name: MY_CLASS, level: 1 })).toBeVisible()
    await expect(page.getByText('Teacher Li Wei · 2 units · 5 lessons')).toBeVisible()
    await expect(page.getByRole('heading', { name: /^Unit \d+: / })).toHaveText([
      'Unit 1: First Conversations',
      'Unit 2: Campus Life',
    ])
    for (const lesson of [
      'Greetings',
      'Numbers 1–10',
      'University Life',
      'Ordering Food at the Canteen',
      'Finding Places',
    ]) {
      await expect(page.getByRole('link', { name: `Open ${lesson}` })).toBeVisible()
    }
    await page.getByRole('link', { name: 'Open Greetings' }).click()

    await expect(page).toHaveURL(/\/student\/classes\/\d+\/lessons\/\d+$/)
    const lessonUrl = page.url()
    await expect(page.getByRole('heading', { name: 'Greetings', level: 1 })).toBeVisible()
    await expect(page.getByText('In progress · 3 of 4 steps')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Activities', level: 2 })).toBeVisible()
    await page.getByRole('link', { name: 'Start Greetings Flashcards' }).click()

    // The activity, in focus mode: every card seen, then marked as reviewed.
    await expect(page).toHaveURL(/\/activities\/flashcards\/\d+$/)
    await expectFocusMode(page)
    await expect(page.getByRole('heading', { name: 'Greetings Flashcards', level: 1 })).toBeVisible()
    await expect(page.getByText('Card 1 of 5')).toBeVisible()
    await page.getByRole('button', { name: /Show meaning$/ }).click()
    await expect(page.getByRole('button', { name: /^Hello 你好/ })).toHaveAttribute('aria-pressed', 'true')
    for (let card = 2; card <= 5; card += 1) {
      await page.getByRole('button', { name: 'Next card' }).click()
      await expect(page.getByText(`Card ${card} of 5`)).toBeVisible()
    }
    await page.getByRole('button', { name: 'Mark as reviewed' }).click()
    await expect(page.getByRole('status').filter({ hasText: 'Activity done.' })).toContainText(
      '3 of 4 lesson steps done.',
    )
    // Nothing is lost once it is done, so closing goes straight back to the lesson.
    await page.getByRole('button', { name: 'Leave the activity' }).click()
    await expect(page).toHaveURL(lessonUrl)

    // The lesson quiz opens on its start page, named after the lesson.
    await page.getByRole('link', { name: 'Retake quiz' }).click()
    await expect(page).toHaveURL(/\/student\/quiz\/\d+$/)
    await expectFocusMode(page)
    await expect(page.getByRole('heading', { name: 'Lesson quiz: Greetings', level: 1 })).toBeVisible()
    await expect(
      page.getByText('Guided · 5 questions · 10 marks · 10-minute time limit · hints available'),
    ).toBeVisible()
    await expect(page.getByText('Best score so far: 80%')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Start quiz' })).toBeVisible()
    await page.getByRole('link', { name: 'Back to lesson' }).click()
    await expect(page).toHaveURL(lessonUrl)
  })

  test('Live sessions lists her booked sessions, upcoming and past', async ({ page }) => {
    await page.goto('/student/classes')
    await page
      .getByRole('navigation', { name: 'Classes sections' })
      .getByRole('link', { name: /^Live sessions/ })
      .click()

    await expect(page).toHaveURL(/\/student\/classes\/live$/)
    await expect(page.getByRole('heading', { name: 'Upcoming', level: 2 })).toBeVisible()
    await expect(page.getByText(/^\d+ sessions · you booked 1$/)).toBeVisible()
    // Today's session, two hours from the start of the run: booked, and Join opens 15 minutes before it.
    const join = page.getByRole('button', { name: 'Join Greetings 问候' })
    const booked = page.getByRole('listitem').filter({ has: join })
    await expect(booked).toContainText('Teacher Li Wei · Mandarin for Beginners · Lesson: Greetings')
    await expect(booked).toContainText('Booked')
    await expect(booked).toContainText(/Join opens at .+, 15 minutes before the start\./)
    await expect(join).toBeDisabled()
    // Sessions she has not booked can be booked.
    await expect(page.getByRole('button', { name: 'Book University Life' })).toBeEnabled()

    await expect(page.getByRole('heading', { name: 'Past', level: 2 })).toBeVisible()
    await expect(page.getByText('3 recordings · sessions you booked')).toBeVisible()
    for (const session of ['Numbers 数字', 'Campus Words 校园', 'Greetings 问候']) {
      await expect(
        page.getByRole('link', { name: `Watch the recording of ${session} (opens in a new tab)` }),
      ).toBeVisible()
    }
  })
})

test.describe('AI Helper with the AI provider off', () => {
  const TABS = [
    { tab: 'Talk with Xiaomei', path: 'talk', section: 'Talk with Xiaomei', action: 'Speak your reply in Chinese' },
    { tab: 'Ask SparkBot', path: 'ask', section: 'Chat with SparkBot', action: 'Send' },
    { tab: 'Quick quiz', path: 'quiz', section: 'Quick quiz', action: 'Make quiz' },
    { tab: 'Story', path: 'story', section: 'Story', action: 'Make story' },
    {
      tab: 'Pronunciation & handwriting',
      path: 'voice',
      section: 'Check your speaking or handwriting',
      action: 'Check with SparkBot',
    },
  ] as const

  test('every tab says that SparkBot is unavailable and offers nothing to send', async ({ page }) => {
    await sidebar(page).getByRole('link', { name: 'AI Helper' }).click()
    await expect(page.getByRole('heading', { name: 'AI Helper', level: 1 })).toBeVisible()
    const tabs = page.getByRole('navigation', { name: 'AI Helper sections' })
    await expect(tabs.getByRole('link')).toHaveText(TABS.map(({ tab }) => tab))

    for (const { tab, path, section, action } of TABS) {
      await tabs.getByRole('link', { name: tab }).click()
      await expect(page).toHaveURL(new RegExp(`/student/ai/${path}$`))
      await expect(page.getByRole('status').filter({ hasText: 'SparkBot isn’t available right now.' })).toContainText(
        'Lessons, practice and quizzes still work. Please try again later.',
      )
      const panel = page.getByRole('region', { name: section })
      await expect(panel).toBeVisible()
      await expect(panel.getByRole('button', { name: action, exact: true })).toBeDisabled()
    }
    await tabs.getByRole('link', { name: 'Ask SparkBot' }).click()
    await expect(page.getByRole('textbox', { name: 'Your question' })).toBeDisabled()
    await expect(page.getByRole('textbox', { name: 'Your question' })).toHaveAttribute(
      'placeholder',
      'SparkBot is unavailable right now',
    )
  })

  test('the AI endpoints say the AI is unavailable instead of making up an answer', async ({ request }) => {
    const headers = bearer(await apiToken(request, AMIRA))
    const call = async (path: string, data: object) => {
      const response = await request.post(`${API_BASE_URL}${path}`, { headers, data })
      expect(response.status(), path).toBe(200)
      return ((await response.json()) as { data: Record<string, unknown> }).data
    }
    const unavailable = 'AI tutor is currently unavailable. Please try again later.'

    const status = await request.get(`${API_BASE_URL}/ai/status`, { headers })
    expect(((await status.json()) as { data: { available: boolean } }).data.available).toBe(false)
    // The chat answers as a server-sent event stream; its final `complete` event carries the outcome.
    const stream = await request.post(`${API_BASE_URL}/ai/chat/stream`, {
      headers,
      data: { message: 'How do you say hello in Mandarin?' },
    })
    expect(stream.status()).toBe(200)
    const events = await stream.text()
    expect(events).toContain('event:complete')
    expect(events).toContain('"status":"UNAVAILABLE"')
    expect(events).toContain(unavailable)
    expect(
      await call('/ai/conversation/reply', {
        scenario: 'MEET_XIAOMEI',
        turns: [{ speaker: 'XIAOMEI', text: '你好！我是小美。' }],
        languagePreference: 'en',
        message: '你好，我叫阿米拉。',
      }),
    ).toMatchObject({ status: 'UNAVAILABLE', message: unavailable, reply: null })
    expect(await call('/ai/quiz-generator', { topic: 'Numbers', numberOfQuestions: 3 })).toMatchObject({
      status: 'UNAVAILABLE',
      questions: [],
    })
    expect(await call('/ai/story-generator', { topic: 'My family' })).toMatchObject({
      status: 'UNAVAILABLE',
      lines: [],
      comprehensionQuestions: [],
    })
  })
})

test.describe('Progress', () => {
  test('Progress shows her XP level, streak and badges', async ({ page }) => {
    await sidebar(page).getByRole('link', { name: 'Progress' }).click()

    await expect(page).toHaveURL(/\/student\/progress$/)
    await expect(page.getByRole('heading', { name: 'Progress', level: 1 })).toBeVisible()
    const level = page.getByRole('region', { name: 'Your XP level' })
    await expect(level).toContainText('Regular Learner')
    await expect(level).toContainText(/\d+ of 300 XP/)
    await expect(level.getByRole('progressbar', { name: /^Progress to the next level: \d+%$/ })).toBeVisible()
    await expect(level.getByRole('list', { name: 'XP levels' }).getByRole('listitem')).toHaveCount(5)

    const streak = page.getByRole('region', { name: 'day streak' })
    await expect(streak).toContainText(/Best: \d+ days/)
    await expect(streak.getByRole('table')).toBeVisible()
    await expect(page.getByRole('region', { name: 'Where your XP comes from' })).toContainText('Recent XP')

    const badges = page.getByRole('region', { name: 'Badges' })
    await expect(badges).toContainText(/\d of 8 earned/)
    await expect(badges.getByRole('button', { name: /^First Steps .*Earned$/ })).toBeVisible()
    await expect(badges.getByRole('button', { name: /^300 XP .*Not earned yet$/ })).toBeVisible()
  })

  test('My learning plan shows her goal, this week and the path to it', async ({ page }) => {
    await page.goto('/student/progress')
    const plan = page.getByRole('region', { name: 'My learning plan' })
    await expect(plan).toContainText('Travel to Shanghai')
    await expect(plan.getByRole('progressbar', { name: /^Plan progress: \d+%$/ })).toBeVisible()
    await plan.getByRole('link', { name: 'View my plan' }).click()

    await expect(page).toHaveURL(/\/student\/progress\/plan$/)
    await expect(page.getByRole('heading', { name: 'My learning plan', level: 1 })).toBeVisible()
    await expect(page.getByText(/^Travel to Shanghai · by .+ · week \d+ of \d+$/)).toBeVisible()
    const glance = page.getByRole('region', { name: 'Your plan at a glance' })
    await expect(glance.getByRole('heading')).toHaveText(['Mandarin level', 'Plan progress', 'This week', 'Pace'])
    await expect(glance).toContainText('15 min · 5 days')
    const steps = page.getByRole('region', { name: 'Your path' }).getByRole('list', { name: 'The steps of your plan' })
    await expect(steps).toContainText('Learn · Unit 1 · Say hello · 你好')
    await expect(steps).toContainText(`Class · ${MY_CLASS}`)
    await expect(steps).toContainText('Learn · Unit 2 · Food and drink · 吃喝')
    await expect(steps).toContainText('Your goal · Travel to Shanghai')
    await expect(page.getByRole('region', { name: /^This week · week \d+$/ })).toBeVisible()
  })
})
