import { expect, test, type Locator, type Page } from '@playwright/test'

import { GOAL_BONUS_XP, numberIn, passRankUp, sidebarStatus, signInAmira } from './studentSupport'
import { expectFocusMode, expectLeaveStep, sidebar } from './support'

test.describe('Practice', () => {
  test.describe.configure({ mode: 'serial' })

  /** Practice's tabs: the six activities and her mistakes. */
  function activities(page: Page): Locator {
    return page.getByRole('navigation', { name: 'Practice activities' })
  }

  /** Today's XP and goal from the strip above the stage ("20 / 30 XP"). */
  async function todayAndGoal(page: Page): Promise<{ today: number; goal: number }> {
    const strip = page.getByRole('main').getByText(/^\d+ \/ \d+ XP$/)
    const text = await strip.innerText()
    return { today: await numberIn(text, /^(\d+) \//), goal: await numberIn(text, /\/ (\d+) XP$/) }
  }

  /** Shows the meaning of the card on screen and rates it. */
  async function rateCard(page: Page, rating: 'Still learning' | 'Got it'): Promise<void> {
    await page.getByRole('button', { name: 'Show meaning' }).click()
    await page
      .getByRole('group', { name: 'How well do you know this card?' })
      .getByRole('button', { name: rating })
      .click()
  }

  test('a flashcard set is reviewed card by card, and a card still being learned waits in Mistakes', async ({
    page,
  }) => {
    await signInAmira(page)
    const xpBefore = await numberIn(sidebarStatus(page).innerText(), /(\d+) XP/)
    await sidebar(page)
      .getByRole('link', { name: /^Practice/ })
      .click()
    // Practice has no hub: its first tab opens, under the seven tabs (six activities and her mistakes). No Quizzes.
    await expect(page).toHaveURL(/\/student\/practice\/flashcards$/)
    await expect(page.getByRole('heading', { name: 'Practice', level: 1 })).toBeAttached()
    await expect(activities(page).getByRole('link')).toHaveText([
      'Flashcards',
      'Listening',
      'Tracing',
      'Drag & Drop',
      'Matching',
      'Typing',
      /^Mistakes\s*\d+$/,
    ])
    const mistakesBefore = await numberIn(
      activities(page)
        .getByRole('link', { name: /^Mistakes/ })
        .innerText(),
      /(\d+)/,
    )
    const { today, goal } = await todayAndGoal(page)

    // The sets are listed by level beside the stage; the HSK 2 group folds open.
    const sets = page.getByRole('navigation', { name: 'Practice sets' })
    await sets.getByRole('button', { name: /^HSK 2 \(Survival\)/ }).click()
    const travelWords = sets.getByRole('link', { name: /^Travel Words/ })
    await expect(travelWords).not.toContainText('In progress')
    // "6 cards · +15 XP": the set's reward.
    const reward = await numberIn(travelWords.innerText(), /\+(\d+) XP/)
    await travelWords.click()
    await expect(page).toHaveURL(/\/student\/practice\/flashcards\?set=\d+$/)
    await expect(
      page.getByRole('heading', { name: /^Travel Words · Flashcards · 6 cards · HSK 2/, level: 2 }),
    ).toBeAttached()
    await expect(sets.getByRole('link', { name: /^Travel Words/ })).toHaveAttribute('aria-current', 'true')

    // The front shows the word; Show meaning turns it to the meaning and asks how well she knows it.
    const counter = page.getByText(/^Card \d of 6$/)
    await expect(counter).toHaveText('Card 1 of 6')
    const previous = page.getByRole('button', { name: 'Previous', exact: true })
    await expect(previous).toBeDisabled()
    const rating = page.getByRole('group', { name: 'How well do you know this card?' })
    await expect(rating).toHaveCount(0)
    await expect(page.getByText('机场', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: 'Show meaning' }).click()
    await expect(page.getByText('airport', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Flip back' })).toBeVisible()
    await expect(rating.getByRole('button')).toHaveText(['Still learning', 'Got it'])
    await expect(rating.getByRole('button', { name: 'Still learning' })).toBeFocused()
    // Rating moves on to the next card; Previous goes back to look again, and a new rating replaces the old one.
    await rating.getByRole('button', { name: 'Got it' }).click()
    await expect(counter).toHaveText('Card 2 of 6')
    await expect(page.getByRole('button', { name: 'Show meaning' })).toBeFocused()
    await previous.click()
    await expect(counter).toHaveText('Card 1 of 6')
    await rateCard(page, 'Still learning')
    for (let card = 2; card <= 5; card += 1) {
      await expect(counter).toHaveText(`Card ${card} of 6`)
      await rateCard(page, 'Got it')
    }
    // Rating the last card finishes the set.
    await expect(counter).toHaveText('Card 6 of 6')
    await rateCard(page, 'Got it')

    // The first review pays the set's XP whatever the ratings, and 5 more when it reaches today's goal.
    const earned = reward + (today < goal && today + reward >= goal ? GOAL_BONUS_XP : 0)
    await passRankUp(page, xpBefore, earned)
    const result = page.getByRole('region', { name: 'Set reviewed' })
    await expect(result).toContainText('5 got it · 1 added to Mistakes')
    await expect(result.getByText(`+${earned} XP`, { exact: true })).toBeVisible()
    await expect(page.getByText('6 of 6 cards', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Practise again' })).toBeVisible()
    await expect(page.getByRole('link', { name: /^Next set: / })).toBeVisible()
    // A card still being learned keeps the set in progress; the result stays after the list reloads.
    await expect(sets.getByRole('link', { name: /^Travel Words.*In progress$/ })).toBeVisible()
    await expect(result).toBeVisible()
    await expect(sidebarStatus(page)).toContainText(`${xpBefore + earned} XP`)

    // 机场 waits in Mistakes, asked as what it means.
    const mistakesTab = activities(page).getByRole('link', { name: /^Mistakes/ })
    await expect(mistakesTab).toHaveText(new RegExp(`^Mistakes\\s*${mistakesBefore + 1}$`))
    await mistakesTab.click()
    const newest = page
      .getByRole('region', { name: `${mistakesBefore + 1} mistakes to fix` })
      .getByRole('listitem')
      .first()
    await expect(newest).toContainText('What does 机场 mean?')
    await expect(newest).toContainText('Flashcards · marked “Still learning” · 机场 = airport')
    await expect(newest).toContainText('Practice')
  })

  test('finishing the suggested flashcard set keeps its result on screen', async ({ page }) => {
    await signInAmira(page, '/student/practice/flashcards')
    // The first set she has not finished with every card known is opened for her.
    const sets = page.getByRole('navigation', { name: 'Practice sets' })
    await expect(sets.getByRole('link', { name: /^Family.*Completed$/ })).toBeVisible()
    await expect(sets.getByRole('link', { name: /^Food and Drink.*In progress$/ })).toHaveAttribute(
      'aria-current',
      'true',
    )
    for (let card = 1; card <= 6; card += 1) {
      await expect(page.getByText(`Card ${card} of 6`, { exact: true })).toBeVisible()
      await rateCard(page, 'Got it')
    }

    const result = page.getByRole('region', { name: 'Set reviewed' })
    await expect(result).toContainText('6 got it')
    // She was paid for this set on her first review.
    await expect(result).toContainText('You earned this set’s XP before.')
    await expect(page).toHaveURL(/\/student\/practice\/flashcards\?set=\d+$/)
    await expect(sets.getByRole('link', { name: /^Food and Drink.*Completed$/ })).toHaveAttribute(
      'aria-current',
      'true',
    )
    await expect(result).toBeVisible()
    await expect(page.getByRole('link', { name: 'Next set: Greetings' })).toBeVisible()
  })

  test('Matching pairs words with meanings, grades the board and shows the finished result', async ({ page }) => {
    await signInAmira(page, '/student/practice/matching')
    const sets = page.getByRole('navigation', { name: 'Practice sets' })
    await sets.getByRole('link', { name: /^Numbers Match/ }).click()
    await expect(page).toHaveURL(/\/student\/practice\/matching\?set=\d+$/)
    await expect(page.getByText('0 of 4 paired · click a word, then its meaning')).toBeVisible()

    const words = page.getByRole('list', { name: 'Words' })
    const meanings = page.getByRole('list', { name: 'Meanings' })
    const check = page.getByTestId('matching-check')
    await expect(check).toBeDisabled()
    await expect(meanings.getByRole('button', { name: 'three', exact: true })).toBeDisabled()

    for (const [hanzi, meaning] of [
      ['三', 'three'],
      ['五', 'five'],
      ['八', 'eight'],
      ['十', 'ten'],
    ] as const) {
      await words.getByRole('button', { name: new RegExp(hanzi) }).click()
      await meanings.getByRole('button', { name: meaning, exact: true }).click()
    }
    await expect(page.getByText('4 of 4 paired · click a word, then its meaning')).toBeVisible()
    await check.click()
    await expect(page.getByRole('status').filter({ hasText: '4 of 4 pairs right' })).toBeVisible()

    await page.getByTestId('continue').click()
    const result = page.getByRole('region', { name: 'All 4 pairs correct' })
    await expect(result).toContainText('三 three · 五 five · 八 eight · 十 ten')
    await expect(result).toContainText('You earned this set’s XP before.')
    await expect(page.getByRole('button', { name: 'Practise again' })).toBeVisible()
  })

  test('Mistakes lists her waiting mistakes, and the review shows the right answer', async ({ page }) => {
    await signInAmira(page, '/student/practice/flashcards')
    const tab = activities(page).getByRole('link', { name: /^Mistakes/ })
    // The tab counts her waiting mistakes once they are read.
    await expect(tab).toHaveText(/^Mistakes\s*\d+$/)
    const total = await numberIn(tab.innerText(), /(\d+)/)
    await tab.click()

    await expect(page).toHaveURL(/\/student\/practice\/mistakes$/)
    const mistakes = page.getByRole('region', { name: `${total} mistakes to fix` })
    const filters = mistakes.getByRole('group', { name: 'Show mistakes from' })
    await expect(filters.getByRole('button')).toHaveText([`All · ${total}`, 'Learn · 2', `Practice · ${total - 2}`])
    await expect(filters.getByRole('button', { name: `All · ${total}` })).toHaveAttribute('aria-pressed', 'true')
    // Six rows at most, the newest first; the review takes them all.
    await expect(mistakes.getByRole('listitem')).toHaveCount(Math.min(total, 6))
    if (total > 6) await expect(mistakes).toContainText(`+${total - 6} more`)
    // What the review takes: up to 20 mistakes, about half a minute each.
    await expect(mistakes).toContainText(`· about ${Math.ceil(Math.min(total, 20) / 2)} min · +10 XP`)

    await filters.getByRole('button', { name: 'Learn · 2' }).click()
    await expect(filters.getByRole('button', { name: 'Learn · 2' })).toHaveAttribute('aria-pressed', 'true')
    await expect(mistakes.getByRole('listitem')).toHaveCount(2)
    await expect(
      mistakes.getByRole('listitem').filter({ hasText: 'Listen and choose: which phrase did you hear?' }),
    ).toContainText('You answered bà · correct: pà')

    await mistakes.getByRole('link', { name: 'Start review' }).click()
    await expect(page).toHaveURL(/\/student\/practice\/review$/)
    await expectFocusMode(page)
    await expect(page.getByRole('heading', { name: 'Mistakes review', level: 1 })).toBeAttached()
    await expect(page.getByText(`Mistakes review · 1 of ${total}`)).toBeVisible()
    // The oldest first: 你好, marked "Still learning" in Flashcards, asked as what it means.
    const question = page.getByRole('region', { name: 'Select the correct meaning' })
    await expect(question).toContainText('你好')
    await expect(question).toContainText('nǐ hǎo · marked “Still learning” in Flashcards')
    // A skipped answer stays a mistake: the right one is marked in place (nothing is cleared until the review ends).
    await page.getByRole('button', { name: 'Skip' }).click()
    await expect(page.getByRole('status').filter({ hasText: 'Skipped' })).toContainText(
      'It stays on your Mistakes list.',
    )
    const correctAnswer = question.getByRole('button', { name: 'Correct answer: hello', exact: true })
    await expect(correctAnswer).toHaveClass(/\bis-correct\b/)
    await expect(correctAnswer).toBeDisabled()
    await expect(page.getByRole('button', { name: 'Continue', exact: true })).toBeFocused()
  })

  test('leaving the Mistakes review asks first, in words', async ({ page }) => {
    await signInAmira(page, '/student/practice/review')
    await page.getByRole('button', { name: 'Skip' }).click()
    await expect(page.getByRole('status').filter({ hasText: 'Skipped' })).toBeVisible()

    await page.getByRole('button', { name: 'Leave the review' }).click()
    const dialog = await expectLeaveStep(page, 'Leave the review?', 'Leave')
    await expect(dialog).toContainText('The mistakes stay on your list.')
    await expect(dialog).not.toContainText('learn.')
    await dialog.getByRole('button', { name: 'Leave', exact: true }).click()
    await expect(page).toHaveURL(/\/student\/practice\/mistakes$/)
    await expect(page.getByRole('region', { name: /^\d+ mistakes to fix$/ })).toBeVisible()
  })

  test('finishing the Mistakes review clears a fixed mistake and updates her count', async ({ page }) => {
    await signInAmira(page, '/student/practice/mistakes')
    const xpBefore = await numberIn(sidebarStatus(page).innerText(), /(\d+) XP/)
    const { today, goal } = await todayAndGoal(page)
    const tab = activities(page).getByRole('link', { name: /^Mistakes/ })
    await expect(tab).toHaveText(/^Mistakes\s*\d+$/)
    const total = await numberIn(tab.innerText(), /(\d+)/)

    await page.getByRole('link', { name: 'Start review' }).click()
    await expect(page).toHaveURL(/\/student\/practice\/review$/)
    await expect(page.getByText(`Mistakes review · 1 of ${total}`)).toBeVisible()

    // Fix the oldest mistake (你好 = hello) and skip the remaining ones so the review completes deterministically.
    const firstQuestion = page.getByRole('region', { name: 'Select the correct meaning' })
    await expect(firstQuestion).toContainText('你好')
    await firstQuestion
      .getByRole('list', { name: 'Answers' })
      .getByRole('button', { name: 'hello', exact: true })
      .click()
    await page.getByRole('button', { name: 'Check', exact: true }).click()
    await expect(page.getByRole('status').filter({ hasText: 'Correct!' })).toBeVisible()
    await page.getByRole('button', { name: total === 1 ? 'Finish the review' : 'Continue', exact: true }).click()

    for (let index = 2; index <= total; index += 1) {
      await expect(page.getByText(`Mistakes review · ${index} of ${total}`)).toBeVisible()
      await page.getByRole('button', { name: 'Skip' }).click()
      await expect(page.getByRole('status').filter({ hasText: 'Skipped' })).toBeVisible()
      await page.getByRole('button', { name: index === total ? 'Finish the review' : 'Continue', exact: true }).click()
    }

    const reviewXp = 10
    const earned = reviewXp + (today < goal && today + reviewXp >= goal ? GOAL_BONUS_XP : 0)
    await passRankUp(page, xpBefore, earned)
    const done = page.getByTestId('review-complete')
    await expect(done.getByRole('heading', { name: 'Review complete!', level: 2 })).toBeVisible()
    await expect(done.getByRole('term')).toHaveText(['XP earned', 'cleared', 'left for later'])
    await expect(done.getByRole('definition')).toHaveText([`+${earned}`, '1', String(total - 1)])

    await page.getByTestId('review-done').click()
    await expect(page).toHaveURL(/\/student\/practice\/mistakes$/)
    await expect(page.getByRole('region', { name: `${total - 1} mistakes to fix` })).toBeVisible()
    await expect(tab).toHaveText(new RegExp(`^Mistakes\\s*${total - 1}$`))
  })
})
