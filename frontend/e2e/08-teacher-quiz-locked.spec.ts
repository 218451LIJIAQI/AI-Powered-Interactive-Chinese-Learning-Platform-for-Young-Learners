import { expect, test } from '@playwright/test'

import { E2E_ACCOUNTS } from './accounts'
import { signIn } from './support'

const CLASS = 'Mandarin for Campus Life'
const LESSON = 'Room Numbers'

test('a quiz students have taken is locked, and the lesson editor says why', async ({ page }) => {
  await signIn(page, E2E_ACCOUNTS.teacher)
  await page.goto('/teacher/classes')
  await page.getByRole('link', { name: `Open ${CLASS}` }).click()
  await page.getByRole('link', { name: `Edit ${LESSON}` }).click()
  await page.getByRole('navigation', { name: 'Lesson sections' }).getByRole('link', { name: 'Quiz 5' }).click()
  await expect(page).toHaveURL(/\/teacher\/classes\/\d+\/lessons\/\d+\/quiz$/)

  const questions = page.getByRole('region', { name: 'Questions' })
  await expect(questions).toContainText('Students have taken this quiz, so its questions are locked')
  await expect(questions.getByRole('listitem')).toHaveCount(5)
  await expect(questions.getByRole('button', { name: 'Add question' })).toHaveCount(0)
  await expect(questions.getByRole('button', { name: 'Edit question 1' })).toHaveCount(0)
})
