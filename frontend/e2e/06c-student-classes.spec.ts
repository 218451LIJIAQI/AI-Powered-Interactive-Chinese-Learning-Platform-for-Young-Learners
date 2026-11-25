import type { Locator, Page } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import { AMIRA, MY_CLASS, signInAmira, toast } from './studentSupport'
import { API_BASE_URL, bestEffortApi, expect, expectFocusMode, expectLeaveStep, sidebar, test } from './support'

const CLASS_TO_JOIN = 'Serving Customers'

test.describe('Classes', () => {
  test.describe.configure({ mode: 'serial' })

  /** A class card of My classes. */
  function myClass(page: Page, title: string): Locator {
    return page.getByRole('listitem', { name: title, exact: true })
  }

  /** The My classes · Find classes tabs, each with its count. */
  function sections(page: Page): Locator {
    return page.getByRole('navigation', { name: 'Classes sections' })
  }

  /** Opens a class not joined yet from Find classes and returns its numeric id. */
  async function viewFromFind(page: Page, title: string): Promise<number> {
    await page.getByRole('link', { name: `View class ${title}` }).click()
    await expect(page).toHaveURL(/\/student\/classes\/\d+$/)
    await expect(page.getByRole('heading', { name: title, level: 1 })).toBeVisible()
    return Number(/\/student\/classes\/(\d+)$/.exec(page.url())![1])
  }

  test('My classes lists her classes with their initials, progress and next step', async ({ page }) => {
    await signInAmira(page)
    await sidebar(page).getByRole('link', { name: 'Classes' }).click()

    await expect(page).toHaveURL(/\/student\/classes$/)
    await expect(page.getByRole('heading', { name: 'My classes', level: 1 })).toBeAttached()
    await expect(sections(page).getByRole('link', { name: 'My classes 2' })).toHaveAttribute('aria-current', 'page')
    await expect(sections(page).getByRole('link', { name: 'Find classes 7' })).toBeVisible()

    // Classes are made by the MandarinSpark team: the card shows the class's initials, never a teacher.
    const card = myClass(page, MY_CLASS)
    await expect(card.getByText('MC', { exact: true })).toBeVisible()
    await expect(card).not.toContainText(/teacher/i)
    await expect(card).not.toContainText(E2E_ACCOUNTS.admin.name)
    await expect(card.getByRole('progressbar', { name: `Progress in ${MY_CLASS}` })).toHaveAttribute(
      'aria-valuenow',
      '40',
    )
    await expect(card).toContainText('2 of 5 lessons done')
    await expect(card.getByRole('link', { name: `Open class: ${MY_CLASS}` })).toBeVisible()
    await expect(card.getByRole('link', { name: 'Continue lesson: Meeting Classmates' })).toBeVisible()

    // An archived class keeps its results and opens, with nothing more to continue.
    const archived = myClass(page, 'Mandarin Foundations')
    await expect(archived).toContainText(/Archived \d{1,2} \w{3,4} · 5 of 5 lessons done/)
    await expect(archived.getByRole('link')).toHaveText(['Open class'])
  })

  test('Theme chips filter Find classes for a signed-in learner', async ({ page }) => {
    await signInAmira(page, '/student/classes/find')
    await expect(page.getByRole('heading', { name: 'Find classes', level: 1 })).toBeAttached()
    const themes = page.getByRole('group', { name: 'Filter classes by theme' })
    // The Find classes tab counts the classes; All has no count of its own (S10.04).
    await expect(themes.getByRole('button', { name: 'All', exact: true })).toHaveAttribute('aria-pressed', 'true')

    await themes.getByRole('button', { name: /^Work and career · / }).click()
    await expect(page.getByRole('heading', { name: '2 classes matched' })).toBeAttached()
    await expect(page.getByRole('link', { name: `View class ${CLASS_TO_JOIN}` })).toBeVisible()
    await expect(page.getByRole('link', { name: 'View class Basic Work Mandarin' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'View class Travel Mandarin' })).toHaveCount(0)

    // The classes of her plan say so.
    await themes.getByRole('button', { name: /^Travel · / }).click()
    for (const title of ['Travel Mandarin', 'Shopping and Prices']) {
      const card = page.getByRole('article').filter({ has: page.getByRole('link', { name: `View class ${title}` }) })
      await expect(card).toContainText('In your plan')
    }
  })

  test('Join class puts a free class in My classes at once, and Undo takes it out again', async ({ page, request }) => {
    await signInAmira(page, '/student/classes')
    const findTab = sections(page).getByRole('link', { name: /^Find classes/ })
    await findTab.focus()
    await page.keyboard.press('Enter')

    await expect(page).toHaveURL(/\/student\/classes\/find$/)
    await expect(page.getByRole('heading', { name: /^\d+ classes matched$/ })).toBeAttached()
    await expect(findTab).toBeFocused()
    // Her own class is not offered again.
    await expect(page.getByRole('link', { name: `View class ${MY_CLASS}` })).toHaveCount(0)
    const classId = await viewFromFind(page, CLASS_TO_JOIN)
    await expect(page.getByText('Free · best after Unit 4 of Learn · join to open the lessons')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Unit 1: At the Counter', level: 2 })).toBeVisible()
    // Lessons open after joining.
    await expect(page.getByRole('listitem').filter({ hasText: 'Greeting Customers' })).toBeVisible()
    await expect(page.getByRole('link', { name: /^Greeting Customers/ })).toHaveCount(0)

    await page.clock.install()
    let needsLeave = false
    try {
      await page.getByRole('button', { name: 'Join class' }).click()
      needsLeave = true

      // Joined at once; the toast offers Undo. Hold it open while the card assertions wait for the list to load.
      await expect(page).toHaveURL(/\/student\/classes$/)
      const joined = toast(page, `${CLASS_TO_JOIN} is in My classes.`)
      await expect(joined).toBeVisible()
      const undo = joined.getByRole('button', { name: 'Undo' })
      await undo.hover()
      await expect(sections(page).getByRole('link', { name: 'My classes 3' })).toBeVisible()
      const card = myClass(page, CLASS_TO_JOIN)
      await expect(card.getByText('SC', { exact: true })).toBeVisible()
      await expect(card).not.toContainText(/teacher/i)
      await expect(card).not.toContainText(E2E_ACCOUNTS.admin.name)
      await expect(card).toContainText('Not started')
      await expect(card.getByRole('link', { name: 'Start lesson 1: Greeting Customers' })).toBeVisible()
      await expect(myClass(page, MY_CLASS)).toBeVisible()

      await undo.click()
      await expect(page).toHaveURL(/\/student\/classes\/find$/)
      needsLeave = false
      await expect(page.getByRole('link', { name: `View class ${CLASS_TO_JOIN}` })).toBeVisible()
      await expect(sections(page).getByRole('link', { name: 'My classes 2' })).toBeVisible()
    } finally {
      if (needsLeave) {
        await bestEffortApi(request, AMIRA, (headers) =>
          request.post(`${API_BASE_URL}/students/classes/${classId}/leave`, { headers }),
        )
      }
    }
  })

  test('Leave class asks first, and the class leaves My classes', async ({ page, request }) => {
    await signInAmira(page, '/student/classes/find')
    const classId = await viewFromFind(page, CLASS_TO_JOIN)
    let needsLeave = false
    try {
      await page.getByRole('button', { name: 'Join class' }).click()
      needsLeave = true
      await expect(page).toHaveURL(/\/student\/classes$/)
      await myClass(page, CLASS_TO_JOIN)
        .getByRole('link', { name: `Open class: ${CLASS_TO_JOIN}` })
        .click()

      await expect(page).toHaveURL(/\/student\/classes\/\d+$/)
      const classUrl = page.url()
      await expect(page.getByText('0 of 4 lessons done')).toBeVisible()
      await expect(page.getByRole('region', { name: 'Your next step' })).toContainText(
        'Greeting Customers · not started yet',
      )
      await expect(page.getByRole('link', { name: 'Greeting Customers Not started' })).toBeVisible()

      // Leaving is hard to undo, so it asks; staying is the safe choice and has the focus.
      const leave = page.getByRole('button', { name: 'Leave class' })
      await leave.click()
      const dialog = await expectLeaveStep(page, `Leave ${CLASS_TO_JOIN}?`, 'Leave class', 'Stay in class')
      await expect(dialog).toContainText('You lose access to its lessons.')
      await dialog.getByRole('button', { name: 'Stay in class' }).click()
      await expect(dialog).toBeHidden()
      await expect(page).toHaveURL(classUrl)

      await leave.click()
      await dialog.getByRole('button', { name: 'Leave class' }).click()
      await expect(page).toHaveURL(/\/student\/classes$/)
      needsLeave = false
      await expect(toast(page, `You left ${CLASS_TO_JOIN}.`)).toBeVisible()
      await expect(sections(page).getByRole('link', { name: 'My classes 2' })).toBeVisible()
      await expect(myClass(page, MY_CLASS)).toBeVisible()
      await expect(myClass(page, CLASS_TO_JOIN)).toHaveCount(0)
    } finally {
      if (needsLeave) {
        await bestEffortApi(request, AMIRA, (headers) =>
          request.post(`${API_BASE_URL}/students/classes/${classId}/leave`, { headers }),
        )
      }
    }
  })

  test('a class opens its units, a lesson, a lesson activity and the lesson quiz', async ({ page }) => {
    await signInAmira(page, '/student/classes')
    await myClass(page, MY_CLASS)
      .getByRole('link', { name: `Open class: ${MY_CLASS}` })
      .click()

    await expect(page).toHaveURL(/\/student\/classes\/\d+$/)
    await expect(page.getByRole('heading', { name: MY_CLASS, level: 1 })).toBeVisible()
    await expect(page.getByText('2 of 5 lessons done')).toBeVisible()
    await expect(page.getByRole('main')).not.toContainText(/teacher/i)
    await expect(page.getByRole('main')).not.toContainText(E2E_ACCOUNTS.admin.name)
    await expect(page.getByRole('heading', { name: /^Unit \d+: / })).toHaveText([
      'Unit 1: First Conversations',
      'Unit 2: Campus Life',
    ])
    const nextStep = page.getByRole('region', { name: 'Your next step' })
    await expect(nextStep).toContainText('Meeting Classmates · 3 of 4 steps done')
    await expect(nextStep.getByRole('link', { name: 'Continue: Meeting Classmates' })).toBeVisible()
    for (const label of [
      'Meeting Classmates In progress',
      'Room Numbers Completed',
      'University Life Not started',
      'Ordering Food at the Canteen Completed',
      'Finding Places Not started',
    ]) {
      await expect(page.getByRole('link', { name: label, exact: true })).toBeVisible()
    }
    await page.getByRole('link', { name: 'Meeting Classmates In progress' }).click()

    await expect(page).toHaveURL(/\/student\/classes\/\d+\/lessons\/\d+$/)
    const lessonUrl = page.url()
    await expect(page.getByRole('heading', { name: 'Meeting Classmates', level: 1 })).toBeVisible()
    await expect(page.getByText('In progress · 3 of 4 steps')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Continue: Match the Answers' })).toBeVisible()
    const activities = page.getByRole('region', { name: 'Activities' })
    await expect(activities.getByRole('link', { name: '3 Match the Answers Matching Start' })).toBeVisible()
    await activities.getByRole('link', { name: 'Classmate Phrases Flashcards · Done' }).click()

    // The activity, in focus mode: every card seen; Next on the last card finishes the set.
    await expect(page).toHaveURL(/\/activities\/flashcards\/\d+$/)
    await expectFocusMode(page)
    await expect(page.getByRole('heading', { name: 'Classmate Phrases', level: 1 })).toBeVisible()
    await expect(page.getByText('Card 1 of 5')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Previous', exact: true })).toBeDisabled()
    await page.getByRole('button', { name: /Show meaning$/ }).click()
    await expect(page.getByRole('button', { name: /^What's your name\? 你叫什么名字？/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    for (let card = 2; card <= 5; card += 1) {
      await page.getByRole('button', { name: 'Next', exact: true }).click()
      await expect(page.getByText(`Card ${card} of 5`)).toBeVisible()
    }
    await page.getByRole('button', { name: 'Next', exact: true }).click()
    const done = page.getByRole('status').filter({ hasText: 'Completed' })
    await expect(done).toContainText('All 5 cards reviewed.')
    await expect(page.getByRole('button', { name: 'Practise again' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Next activity' })).toBeVisible()
    // Nothing is lost once it is done, so closing goes straight back to the lesson.
    await page.getByRole('button', { name: 'Leave the activity' }).click()
    await expect(page).toHaveURL(lessonUrl)

    // The lesson quiz shows her best score; a retake opens on its first question, named after the lesson.
    const quiz = page.getByRole('region', { name: 'Lesson quiz' })
    await expect(quiz).toContainText('5 questions')
    await expect(quiz).toContainText('Best score')
    await expect(quiz).toContainText('80%')
    await expect(quiz.getByRole('link', { name: 'See best result' })).toBeVisible()
    await quiz.getByRole('link', { name: 'Retake quiz' }).click()
    await expect(page).toHaveURL(/\/student\/quiz\/\d+$/)
    await expectFocusMode(page)
    await expect(page.getByRole('heading', { name: 'Lesson quiz: Meeting Classmates', level: 1 })).toBeAttached()
    await expect(page.getByText('Question 1 of 5', { exact: true })).toBeVisible()
    await expect(page.getByRole('progressbar', { name: 'Lesson quiz' })).toHaveAttribute('aria-valuenow', '0')
    // Each question is answered before the next one.
    await expect(page.getByRole('button', { name: 'Next', exact: true })).toBeDisabled()
    await page.getByRole('button', { name: 'Leave the quiz' }).click()
    const dialog = await expectLeaveStep(page, 'Leave the quiz?', 'Leave')
    await expect(dialog).toContainText('Your answers in this attempt won’t be saved. Your best score, 80%, stays.')
    await dialog.getByRole('button', { name: 'Leave', exact: true }).click()
    await expect(page).toHaveURL(lessonUrl)
    // The attempt she left is thrown away; her best score stays.
    await expect(quiz.getByRole('link', { name: 'Retake quiz' })).toBeVisible()
    await expect(quiz.getByRole('link', { name: 'Resume quiz' })).toHaveCount(0)
    await expect(quiz).toContainText('80%')
  })

  test('a lesson quiz is answered question by question and ends on the result', async ({ page }) => {
    await signInAmira(page, '/student/classes')
    await myClass(page, MY_CLASS)
      .getByRole('link', { name: `Open class: ${MY_CLASS}` })
      .click()
    await page.getByRole('link', { name: 'Meeting Classmates In progress' }).click()
    await expect(page).toHaveURL(/\/student\/classes\/\d+\/lessons\/\d+$/)
    const lessonUrl = page.url()
    const quiz = page.getByRole('region', { name: 'Lesson quiz' })
    await quiz.getByRole('link', { name: 'Retake quiz' }).click()
    await expect(page).toHaveURL(/\/student\/quiz\/\d+$/)

    // The seeded questions and right answers (config/seed/DemoClassContent).
    const answers = [/^我叫 Amira。/, /^Student$/, /^Nice to meet you\.$/, /^Name$/, /^你是学生吗？/]
    const nextQuestion = page.getByRole('button', { name: 'Next', exact: true })
    for (const [index, answer] of answers.entries()) {
      await expect(page.getByText(`Question ${index + 1} of 5`, { exact: true })).toBeVisible()
      // Submitting is only offered on the last question.
      await expect(page.getByTestId('quiz-submit')).toHaveCount(index === 4 ? 1 : 0)
      if (index < 4) await expect(nextQuestion).toBeDisabled()
      await page.getByRole('radiogroup').getByRole('radio', { name: answer }).click()
      await expect(page.getByRole('radio', { name: answer })).toBeChecked()
      await expect(page.getByRole('progressbar', { name: 'Lesson quiz' })).toHaveAttribute(
        'aria-valuenow',
        String((index + 1) * 20),
      )
      if (index < 4) await nextQuestion.click()
    }
    await expect(page.getByRole('status').filter({ hasText: 'All answered' })).toBeVisible()
    await page.getByRole('button', { name: 'Submit quiz' }).click()

    await expect(page.getByRole('heading', { name: 'You scored 5 out of 5 (100%)', level: 1 })).toBeVisible()
    await expect(page.getByText('Submitted just now · Your best is 5 out of 5 (100%)')).toBeVisible()
    // The lesson still has an activity to do: the result leads on to it.
    await expect(page.getByText('Finish 1 more activity to complete this lesson')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Continue: Match the Answers' })).toBeVisible()
    // The attempt is in, so closing goes straight to the lesson.
    await page.getByRole('button', { name: 'Leave the quiz' }).click()
    await expect(page).toHaveURL(lessonUrl)
    await expect(quiz).toContainText('100%')
    await expect(quiz.getByRole('link', { name: 'Retake quiz' })).toBeVisible()

    // "See best result" opens the attempt with the best score, inside the shell.
    await quiz.getByRole('link', { name: 'See best result' }).click()
    await expect(page).toHaveURL(/\/student\/quiz\/\d+\/attempts\/\d+$/)
    await expect(sidebar(page)).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Lesson quiz', level: 1 })).toBeAttached()
    await expect(page.getByRole('navigation', { name: 'Breadcrumb' })).toContainText('Meeting Classmates')
    // S11.16: the best attempt says so.
    await expect(page.getByRole('term')).toHaveText(['Best score'])
    await expect(page.getByRole('definition')).toHaveText(['5 out of 5 (100%)'])
    await expect(page.getByRole('heading', { name: /^Question review/, level: 2 })).toBeVisible()
  })
})
